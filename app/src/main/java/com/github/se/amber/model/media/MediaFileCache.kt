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
 * Local on-disk cache of media files (the bytes behind [MediaItem.storagePath]), so media that was
 * already loaded can still be browsed offline. Used by the media repository only, never by
 * ViewModels.
 *
 * Files are written to a temporary `.part` file and renamed once complete, so a cached file is
 * always whole. Past [maxBytes], the least recently used files are deleted.
 *
 * Only one instance may use a given [dir]: the locks don't coordinate across instances, so get the
 * app's instance from [MediaStorageRepositoryProvider.mediaFileCache]. The constructor doesn't
 * touch the disk; the folder is set up on first use, on [ioDispatcher].
 *
 * @param storage remote storage a file is downloaded from on a cache miss.
 * @param dir cache folder, e.g. `File(context.cacheDir, "media")`; created if missing.
 * @param maxBytes size the cache is trimmed down to after each new file; must be positive.
 * @param ioDispatcher where the disk and network work runs; tests pass a test dispatcher.
 */
class MediaFileCache(
    private val storage: MediaStorageRepository,
    private val dir: File,
    private val maxBytes: Long = 500L * 1024 * 1024, // 500 MiB
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  init {
    require(maxBytes > 0) { "maxBytes must be positive, was $maxBytes" }
  }

  /**
   * The lock of one storagePath, see [withPathLock]. [users] counts the callers holding or waiting
   * for [mutex], so the entry can be dropped from [pathLocks] as soon as nobody needs it.
   */
  private class PathLock {
    val mutex = Mutex()
    var users = 0 // guarded by synchronized(pathLocks)
  }

  /** A cached file with its last-used time and size, as read once by [trimToSize]. */
  private class Entry(val file: File, val lastUsed: Long, val size: Long)

  /** Locks of the storagePaths currently in use; empty when the cache is idle. */
  private val pathLocks = HashMap<String, PathLock>()

  /**
   * Makes [commit] atomic with respect to [clear] and [trimToSize]: a commit racing a clear can't
   * leave a file behind after it, and a trim never sees a just-added file before its timestamp is
   * reset (it would look old and be evicted at once).
   */
  private val dirLock = Mutex()

  /**
   * Incremented by each [clear]. A new file records it before its download or copy starts, and
   * [commit] refuses the file if the value changed in the meantime, as the file then belongs to the
   * session that was just cleared.
   */
  @Volatile private var generation = 0 // written under dirLock

  /**
   * Creates [dir] and deletes the leftovers of downloads interrupted by the app being killed
   * ([trimToSize] never counts them, so they would otherwise stay forever). Runs once, on the first
   * call, before that call's own download; [lazy] makes concurrent first calls wait for it.
   */
  private val setUp = lazy {
    dir.mkdirs()
    dir.listFiles { f -> f.name.endsWith(PART_SUFFIX) }?.forEach { it.delete() }
  }

  /**
   * Returns the local copy of the file at [storagePath], downloading it only if it isn't cached.
   *
   * - Cache hit: the file is marked as recently used and returned at once, which also works
   *   offline.
   * - Cache miss: the file is downloaded through [storage] into a temporary `.part` file, which is
   *   renamed to its cache name once complete; the cache is then trimmed to [maxBytes]. Offline,
   *   this fails once [storage] stops retrying (after a few seconds with the app's repository, see
   *   [MediaStorageRepositoryProvider]).
   *
   * Concurrent calls for the same path share one download: the others wait for it, then find the
   * file cached. The returned file may be evicted later to make room, so open it right away rather
   * than keeping the [File] around.
   *
   * @throws IllegalArgumentException if [storagePath] is blank, `.` or `..`, or ends with `.part`.
   * @throws Exception whatever [MediaStorageRepository.downloadToFile] throws (e.g. when offline
   *   and the file isn't cached); nothing is left in the cache in that case.
   * @throws IOException if the downloaded file can't be moved to its cache name, or if [clear] ran
   *   during the download.
   */
  suspend fun getFile(storagePath: String): File =
      withContext(ioDispatcher) {
        setUp.value
        val file = fileFor(storagePath)
        val added =
            withPathLock(storagePath) {
              // checked inside the lock: a caller that waited for another one's download finds the
              // file here instead of downloading it a second time
              if (file.exists()) {
                markUsed(file)
                return@withPathLock false
              }
              val startGeneration = generation
              val temp = newTempFile()
              try {
                storage.downloadToFile(storagePath, temp)
                commit(temp, file, startGeneration)
              } finally {
                temp.delete() // no-op if the commit succeeded; removes half-downloads otherwise
              }
              true
            }
        // only when the cache grew; outside the path lock so other callers of this path don't wait
        if (added) trimToSize(keep = file)
        file
      }

  /**
   * Adds a file we already have locally (e.g. one just uploaded) to the cache under [storagePath],
   * so it never needs to be downloaded. Replaces any file already cached under that path.
   *
   * [source] is moved, not copied: once it has been moved into the cache folder, it is gone from
   * its old location, even if the call then fails. If the call fails before that (e.g. [source]
   * doesn't exist), [source] is left untouched.
   *
   * @throws IllegalArgumentException if [storagePath] is blank, `.` or `..`, or ends with `.part`.
   * @throws IOException if [source] can't be moved into the cache, or if [clear] ran during the
   *   call.
   */
  suspend fun put(storagePath: String, source: File): Unit =
      withContext(ioDispatcher) {
        setUp.value
        val file = fileFor(storagePath)
        withPathLock(storagePath) {
          val startGeneration = generation
          val temp = newTempFile()
          try {
            // move into a temp file first, like a download: across storage volumes a move is really
            // a copy, and a half-copied file must never appear under the cache name
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
   * any download or [put] of that path in progress, so the file can't reappear right after.
   *
   * @throws IllegalArgumentException if [storagePath] is blank, `.` or `..`, or ends with `.part`.
   */
  suspend fun evict(storagePath: String): Unit =
      withContext(ioDispatcher) {
        setUp.value
        withPathLock(storagePath) { fileFor(storagePath).delete() }
      }

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
        setUp.value
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
   *
   * Different paths can map to the same name (e.g. `a/b_c.jpg` and `a_b/c.jpg`). Real storage paths
   * are made of Firestore IDs, which contain no `_`, so they don't collide.
   *
   * @throws IllegalArgumentException if [storagePath] is blank, would name [dir] itself or its
   *   parent (`.` or `..`), or ends with `.part`, which is reserved for temporary files.
   */
  private fun fileFor(storagePath: String): File {
    val name = storagePath.replace('/', '_')
    require(
        storagePath.isNotBlank() && name != "." && name != ".." && !name.endsWith(PART_SUFFIX)
    ) {
      "Invalid storagePath: \"$storagePath\""
    }
    return File(dir, name)
  }

  /**
   * Marks [file] as recently used for [trimToSize]. If the filesystem refuses (returns false), the
   * file only keeps its older time and may be evicted a bit early, so the result is ignored.
   */
  private fun markUsed(file: File) {
    file.setLastModified(System.currentTimeMillis())
  }

  /**
   * A new empty `.part` file in [dir], with a unique name so concurrent calls never share one.
   * Being in [dir] keeps the final rename to its cache name on one filesystem, so it is atomic.
   * Recreates [dir] first, in case it was deleted while the app runs (e.g. the user cleared the
   * app's cache); a no-op otherwise.
   */
  private fun newTempFile(): File {
    dir.mkdirs()
    return File.createTempFile("download", PART_SUFFIX, dir)
  }

  /**
   * Gives the complete file [temp] its cache name [target], replacing any previous file there.
   *
   * The rename is atomic (both files are in [dir]), on Android as well as on the JVM that runs the
   * tests. Runs under [dirLock] so it can't interleave with [clear] or [trimToSize].
   *
   * @param startGeneration [generation] when the file's download or copy started.
   * @throws IOException if [clear] ran since [startGeneration], or if the rename fails.
   */
  private suspend fun commit(temp: File, target: File, startGeneration: Int) = dirLock.withLock {
    if (generation != startGeneration) {
      throw IOException("Cache cleared while $target was being added")
    }
    // with ATOMIC_MOVE, replacing an existing target is left to the platform: rename(2) on Android
    // and MoveFileEx on Windows both replace it; REPLACE_EXISTING only states the intent
    Files.move(temp.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    // a move keeps the old last-modified time; reset it so the new file isn't evicted first
    markUsed(target)
  }

  /**
   * Deletes the least recently used files until the cache fits in [maxBytes]. Called after each
   * file is added.
   *
   * Never deletes [keep], the file just added and about to be returned, even when it alone is
   * bigger than [maxBytes]: the cache then stays over the limit until the next file is added. Also
   * skips files a cache hit used after the listing, which makes evicting a file that another call
   * is returning unlikely (not impossible), and `.part` files, which are downloads in progress.
   */
  private suspend fun trimToSize(keep: File) = dirLock.withLock {
    // each file's time and size are read once: cache hits change times concurrently, and a sort
    // whose keys change while it runs can throw IllegalArgumentException. Don't sort on
    // file.lastModified() directly; no test reliably catches that race
    val entries =
        dir.listFiles { f -> !f.name.endsWith(PART_SUFFIX) }
            .orEmpty()
            .map { Entry(it, it.lastModified(), it.length()) }
    var total = entries.sumOf { it.size }
    for (entry in entries.sortedBy { it.lastUsed }) { // oldest first
      if (total <= maxBytes) break
      if (entry.file == keep || entry.file.lastModified() > entry.lastUsed) continue
      entry.file.delete()
      if (!entry.file.exists()) total -= entry.size // also true if evict() or the system deleted it
    }
  }

  private companion object {
    /** Suffix of temporary files holding a download or copy in progress. */
    const val PART_SUFFIX = ".part"
  }
}
