// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * What [MediaFileCache] needs from remote storage: downloading one file. Declared here so the cache
 * doesn't depend on the storage repository; wire it with a method reference, e.g.
 * `MediaFileCache(storageRepository::downloadToFile, dir)`, or a lambda in tests.
 *
 * TODO: temporary, until the media storage repository is implemented by another team member. Once
 *   that mockable repository exists, the cache may depend on it directly (tests would then mock it)
 *   and this interface would be removed. The repository's method name and signature aren't fixed
 *   yet, so the call in [MediaFileCache.getFile] may change with it.
 */
fun interface MediaDownloader {
  /**
   * Downloads the remote file at [storagePath] into [destination], overwriting its content. Throws
   * if the download fails (e.g. offline); [destination] may then hold a partial file.
   */
  suspend fun download(storagePath: String, destination: File)
}

/**
 * Local on-disk cache of media files (the bytes behind [MediaItem.storagePath]), so media that was
 * already loaded can still be browsed offline. Used by the media repository only, never by
 * ViewModels.
 *
 * Files are written to a temporary `.part` file and renamed once complete, so a cached file is
 * always whole. Past [maxBytes], the least recently used files are deleted.
 *
 * Only one instance may use a given [dir]: the locks don't coordinate across instances.
 *
 * @param downloader fetches a file on a cache miss; temporary, see [MediaDownloader].
 * @param dir cache folder, e.g. `File(context.cacheDir, "media")`; created if missing.
 * @param maxBytes size the cache is trimmed down to after each new file.
 * @param ioDispatcher where the disk and network work runs; tests pass a test dispatcher.
 */
class MediaFileCache(
    private val downloader: MediaDownloader,
    private val dir: File,
    private val maxBytes: Long = 500L * 1024 * 1024, // 500 MB
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  /**
   * The lock of one storagePath, see [withPathLock]. [users] counts the callers holding or waiting
   * for [mutex], so the entry can be dropped from [pathLocks] as soon as nobody needs it.
   */
  private class PathLock {
    val mutex = Mutex()
    var users = 0 // guarded by synchronized(pathLocks)
  }

  /** Locks of the storagePaths currently in use; empty when the cache is idle. */
  private val pathLocks = HashMap<String, PathLock>()

  /**
   * Serializes changes to the set of cached files: [commit], [trimToSize] and [clear]. Two trims at
   * once would both delete files for the same excess, and a commit racing a clear could leave a
   * file behind after it.
   */
  private val dirLock = Mutex()

  /**
   * Incremented by each [clear]. A new file records it before its download or copy starts, and
   * [commit] refuses the file if the value changed in the meantime, as the file then belongs to the
   * session that was just cleared.
   */
  @Volatile private var generation = 0 // written under dirLock

  init {
    dir.mkdirs()
    // leftovers of downloads interrupted by the app being killed; trimToSize never counts them, so
    // without this they would stay forever. Safe because no download of this instance has started
    // yet
    dir.listFiles { f -> f.name.endsWith(PART_SUFFIX) }?.forEach { it.delete() }
  }

  /**
   * Returns the local copy of the file at [storagePath], downloading it only if it isn't cached.
   *
   * - Cache hit: the file is marked as recently used and returned at once, which also works
   *   offline.
   * - Cache miss: the file is downloaded through [downloader] into a temporary `.part` file, which
   *   is renamed to its cache name once complete; the cache is then trimmed to [maxBytes].
   *
   * Concurrent calls for the same path share one download: the others wait for it, then find the
   * file cached.
   *
   * @throws Exception whatever [MediaDownloader.download] throws (e.g. when offline and the file
   *   isn't cached); nothing is left in the cache in that case.
   * @throws IOException if the downloaded file can't be moved to its cache name, or if [clear] ran
   *   during the download.
   */
  suspend fun getFile(storagePath: String): File =
      withContext(ioDispatcher) {
        val file = fileFor(storagePath)
        withPathLock(storagePath) {
          // checked inside the lock: a caller that waited for another one's download finds the
          // file here instead of downloading it a second time
          if (file.exists()) {
            file.setLastModified(System.currentTimeMillis()) // mark as recently used
            return@withPathLock
          }
          val startGeneration = generation
          val temp = newTempFile()
          try {
            downloader.download(storagePath, temp)
            commit(temp, file, startGeneration)
          } finally {
            temp.delete() // no-op if the commit succeeded; removes half-downloads otherwise
          }
        }
        // outside the path lock so a long trim doesn't make other callers of this path wait
        trimToSize(keep = file)
        file
      }

  /**
   * Returns the cached file of [storagePath] and marks it as recently used, or null if it isn't
   * cached. Never downloads and never waits for a download in progress, so it answers at once, even
   * offline.
   */
  suspend fun getCachedFile(storagePath: String): File? =
      withContext(ioDispatcher) {
        fileFor(storagePath)
            .takeIf { it.exists() }
            ?.also { it.setLastModified(System.currentTimeMillis()) }
      }

  /**
   * Adds a file we already have locally (e.g. one just uploaded) to the cache under [storagePath],
   * so it never needs to be downloaded. Replaces any file already cached under that path.
   *
   * [source] is moved, not copied: it is gone from its old location afterwards, even if the call
   * fails.
   *
   * @throws IOException if [source] can't be moved into the cache, or if [clear] ran during the
   *   call.
   */
  suspend fun put(storagePath: String, source: File): Unit =
      withContext(ioDispatcher) {
        val file = fileFor(storagePath)
        withPathLock(storagePath) {
          val startGeneration = generation
          val temp = newTempFile()
          try {
            // moved via a temp file, like a download: from another storage volume, the move is a
            // copy, which must not be visible under the cache name until it is complete
            Files.move(source.toPath(), temp.toPath(), REPLACE_EXISTING)
            commit(temp, file, startGeneration)
          } finally {
            temp.delete()
          }
        }
        trimToSize(keep = file)
      }

  /**
   * Removes the cached file of [storagePath], if any. Call it when the media is deleted. Waits for
   * any download of that path in progress, so the file can't reappear right after.
   */
  suspend fun evict(storagePath: String): Unit =
      withContext(ioDispatcher) { withPathLock(storagePath) { fileFor(storagePath).delete() } }

  /**
   * Deletes every cached file. Call it on sign-out or when leaving a care circle, so no private
   * media stays on the device.
   *
   * Downloads and calls to [put] in progress are not interrupted, but their files are refused when
   * they finish (they throw [IOException]), so nothing started before the clear ends up in the
   * cache. Their `.part` files are left alone: each one is deleted by the call that created it.
   */
  suspend fun clear(): Unit =
      withContext(ioDispatcher) {
        dirLock.withLock {
          generation++
          dir.listFiles { f -> !f.name.endsWith(PART_SUFFIX) }?.forEach { it.delete() }
        }
      }

  /**
   * Runs [block] while holding the lock of [storagePath], so calls on the same path never overlap
   * (e.g. two downloads of the same file, or an evict during a download). Calls on different paths
   * use different locks and still run in parallel.
   *
   * A lock is created on first use and removed once no caller holds or waits for it, so [pathLocks]
   * only grows with the paths in use at the same time, not with every path ever used.
   */
  private suspend fun <T> withPathLock(storagePath: String, block: suspend () -> T): T {
    // register as a user before waiting, so the lock can't be removed while we wait for it.
    // synchronized (not a Mutex) because HashMap isn't thread-safe and nothing here suspends
    val lock =
        synchronized(pathLocks) {
          pathLocks.getOrPut(storagePath) { PathLock() }.also { it.users++ }
        }
    try {
      return lock.mutex.withLock { block() }
    } finally {
      // also runs on failure or cancellation; the last user out removes the lock
      synchronized(pathLocks) { if (--lock.users == 0) pathLocks.remove(storagePath) }
    }
  }

  /**
   * The cache file of [storagePath]. The path's slashes become underscores so every file sits
   * directly in [dir], e.g. `careCircles/c1/media/m1.jpg` -> `careCircles_c1_media_m1.jpg`.
   */
  private fun fileFor(storagePath: String) = File(dir, storagePath.replace('/', '_'))

  /**
   * A new empty `.part` file in [dir], with a unique name so concurrent calls never share one.
   * Being in [dir] guarantees it can later be renamed to its cache name.
   */
  private fun newTempFile() = File.createTempFile("download", PART_SUFFIX, dir)

  /**
   * Gives the complete file [temp] its cache name [target], replacing any previous file there.
   *
   * The rename is atomic (both files are in [dir]), on Android as well as on the JVM that runs the
   * tests. Runs under [dirLock] so it can't interleave with [clear].
   *
   * @param startGeneration [generation] when the file's download or copy started.
   * @throws IOException if [clear] ran since [startGeneration], or if the rename fails.
   */
  private suspend fun commit(temp: File, target: File, startGeneration: Int) = dirLock.withLock {
    if (generation != startGeneration) {
      throw IOException("Cache cleared while $target was being added")
    }
    Files.move(temp.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    // a move keeps the old last-modified time; reset it so the new file isn't evicted first
    target.setLastModified(System.currentTimeMillis())
  }

  /**
   * Deletes the least recently used files until the cache fits in [maxBytes]. Called after each
   * file is added.
   *
   * Never deletes [keep], the file just added and about to be returned, even when it alone is
   * bigger than [maxBytes]: the cache then stays over the limit until the next file is added.
   * `.part` files are skipped, since they are downloads in progress.
   */
  private suspend fun trimToSize(keep: File) = dirLock.withLock {
    val files = dir.listFiles { f -> !f.name.endsWith(PART_SUFFIX) }.orEmpty()
    var total = files.sumOf { it.length() }
    for (f in files.sortedBy { it.lastModified() }) { // oldest first
      if (total <= maxBytes) break
      if (f == keep) continue
      total -= f.length()
      f.delete()
    }
  }

  private companion object {
    /** Suffix of temporary files holding a download or copy in progress. */
    const val PART_SUFFIX = ".part"
  }
}
