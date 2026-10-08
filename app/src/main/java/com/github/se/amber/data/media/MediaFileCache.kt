// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import com.github.se.amber.model.media.MediaItem
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Local on-disk cache of media files (the bytes behind [MediaItem.storagePath]), so media that was
 * already loaded can still be browsed offline. One cache holds the media of every care circle:
 * storage paths contain the circle id, so they never collide. Used by [MediaRepositoryFirebase]
 * only, never by ViewModels.
 *
 * Files are written to a temporary `.part` file and renamed once complete, so a cached file is
 * always whole. Past [maxBytes], the least recently used files are deleted.
 *
 * The cache doesn't know where files come from: each [getFile] says how to download its file (in
 * the app, through [MediaFileStorage] from the circle's bucket).
 *
 * Only one instance may use a given [dir]: the locks don't coordinate across instances, so the app
 * builds a single one, inside its repository (see `MediaRepositoryProvider`). The constructor
 * doesn't touch the disk; the folder is set up on first use, on [ioDispatcher].
 *
 * @param dir cache folder, e.g. `File(context.cacheDir, "media")`; created if missing.
 * @param maxBytes size the cache is trimmed down to after each new file; must be positive.
 * @param ioDispatcher where the disk and network work runs; tests pass a test dispatcher.
 */
class MediaFileCache(
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
   * Makes [commit] and cache hits atomic with respect to [clear], [clearCircle] and [trimToSize]: a
   * commit racing a clear can't leave a file behind after it, a trim never sees a just-added file
   * before its timestamp is reset (it would look old and be evicted at once), and a trim can't
   * delete a file between a hit finding it and returning it.
   *
   * Taken either alone or while holding a path lock, never the other way round, so the two locks
   * can't deadlock.
   */
  private val dirLock = Mutex()

  /**
   * Incremented by each [clear] and [clearCircle]. Each [getFile] and [put] records it on entry,
   * before waiting for anything; if a clear covering its file runs before the call commits, the
   * file is refused with [IOException].
   */
  @Volatile private var generation = 0 // written under dirLock

  /** [generation] set by the latest [clear]; 0 if none. */
  @Volatile private var clearedAt = 0 // written under dirLock

  /** circleId -> [generation] set by the latest [clearCircle] of that circle. */
  private val circleClearedAt = ConcurrentHashMap<String, Int>() // written under dirLock

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
   * Returns the local copy of the file at [storagePath], downloading it with [download] only if it
   * isn't cached.
   *
   * - Cache hit: the file is marked as recently used and returned at once, which also works
   *   offline.
   * - Cache miss: [download] writes the file into a temporary `.part` file, which is renamed to its
   *   cache name once complete; the cache is then trimmed to [maxBytes]. Offline, this fails once
   *   [download] gives up.
   *
   * Concurrent calls for the same path share one download: the others wait for it, then find the
   * file cached. The returned file may be evicted at any time after it is returned, to make room,
   * so open it right away rather than keeping the [File] around.
   *
   * @param download writes the remote file of [storagePath] into the given file, overwriting it;
   *   only called on a cache miss, and while it runs no other call adds or evicts [storagePath].
   *   E.g. `{ fileStorage.downloadToFile(circle, storagePath, it) }`.
   * @throws IllegalArgumentException if [storagePath] is blank, `.` or `..`, or ends with `.part`.
   * @throws Exception whatever [download] throws (e.g. when offline and the file isn't cached);
   *   nothing is left in the cache in that case.
   * @throws IOException if the downloaded file can't be moved to its cache name, or if [clear] or a
   *   [clearCircle] of its circle ran since the call (the file is then not downloaded, or not
   *   kept).
   */
  suspend fun getFile(storagePath: String, download: suspend (destination: File) -> Unit): File {
    val callGeneration = generation // before waiting for anything, see [generation]
    return withContext(ioDispatcher) {
      setUp.value
      val file = fileFor(storagePath)
      val added =
          withPathLock(storagePath) {
            // checked inside the path lock: a caller that waited for another one's download finds
            // the file here instead of downloading it a second time. Under dirLock: a trim can't
            // delete the file between this check and its return
            val hit = dirLock.withLock { file.exists().also { if (it) markUsed(file) } }
            if (hit) return@withPathLock false
            ensureNotCleared(callGeneration, file) // no download for a cleared session
            val temp = newTempFile()
            try {
              download(temp)
              commit(temp, file, callGeneration)
            } finally {
              temp.delete() // no-op if the commit succeeded; removes half-downloads otherwise
            }
            true
          }
      // only when the cache grew; outside the path lock so other callers of this path don't wait
      if (added) trimToSize(keep = file)
      file
    }
  }

  /**
   * Adds a file we already have locally (e.g. one just uploaded) to the cache under [storagePath],
   * so it never needs to be downloaded. Replaces any file already cached under that path.
   *
   * [source] is moved, not copied: once it has been moved into the cache folder, it is gone from
   * its old location, even if the call then fails. If the call fails before that (e.g. [source]
   * doesn't exist, or the cache was cleared while the call waited), [source] is left untouched.
   *
   * @throws IllegalArgumentException if [storagePath] is blank, `.` or `..`, or ends with `.part`.
   * @throws IOException if [source] can't be moved into the cache, or if [clear] or a [clearCircle]
   *   of its circle ran since the call.
   */
  suspend fun put(storagePath: String, source: File) {
    val callGeneration = generation // before waiting for anything, see [generation]
    withContext(ioDispatcher) {
      setUp.value
      val file = fileFor(storagePath)
      withPathLock(storagePath) {
        ensureNotCleared(callGeneration, file) // leaves source untouched
        val temp = newTempFile()
        try {
          // move into a temp file first, like a download: across storage volumes a move is really
          // a copy, and a half-copied file must never appear under the cache name
          Files.move(source.toPath(), temp.toPath(), REPLACE_EXISTING)
          commit(temp, file, callGeneration)
        } finally {
          temp.delete()
        }
      }
      trimToSize(keep = file)
    }
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
   * Deletes every cached file, of every circle. Call it on sign-out, so no private media stays on
   * the device. To forget a single circle, use [clearCircle].
   *
   * Calls to [getFile] and [put] made before it, whether already running or still waiting, are not
   * interrupted, but their files are refused (they throw [IOException]), so nothing requested
   * before the clear ends up in the cache. Their `.part` files are left alone: each one is deleted
   * by the call that created it.
   */
  suspend fun clear(): Unit =
      withContext(ioDispatcher) {
        setUp.value
        dirLock.withLock {
          clearedAt = ++generation
          dir.listFiles { f -> !f.name.endsWith(PART_SUFFIX) }?.forEach { it.delete() }
        }
      }

  /**
   * Deletes the cached files of care circle [circleId], i.e. those whose storagePath starts with
   * `careCircles/{circleId}/` (see [MediaItem.storagePath]); other circles' files are kept. Call it
   * when the user leaves that circle, so its private media doesn't stay on the device.
   *
   * Like [clear], calls to [getFile] and [put] of that circle made before it are not interrupted,
   * but their files are refused (they throw [IOException]). Calls of other circles are unaffected.
   *
   * @throws IllegalArgumentException if [circleId] is blank, or contains `/` or `_`: Firestore
   *   auto-generated IDs never do, and the cache relies on `_` to find where the id ends in a file
   *   name.
   */
  suspend fun clearCircle(circleId: String) {
    require(circleId.isNotBlank() && '/' !in circleId && '_' !in circleId) {
      "Invalid circleId: \"$circleId\""
    }
    withContext(ioDispatcher) {
      setUp.value
      dirLock.withLock {
        circleClearedAt[circleId] = ++generation
        // .part files are named download*.part, so they never match: each is deleted by its call
        dir.listFiles { f -> circleIdOf(f.name) == circleId }?.forEach { it.delete() }
      }
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
   * are made of Firestore auto-generated IDs, which contain no `_`, so they don't collide; this
   * assumes circle and media IDs stay auto-generated.
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
   * The circle id in the cache file name [name], e.g. `c1` for `careCircles_c1_media_m1.jpg`, or
   * null if [name] isn't the file of a circle's media.
   */
  private fun circleIdOf(name: String): String? {
    if (!name.startsWith(CIRCLE_PREFIX)) return null
    return name.removePrefix(CIRCLE_PREFIX).substringBefore('_', "").ifEmpty { null }
  }

  /**
   * Refuses a call made before the latest [clear], or before the latest [clearCircle] of [target]'s
   * circle: its file belongs to what was cleared.
   *
   * @throws IOException if such a clear ran since [callGeneration].
   */
  private fun ensureNotCleared(callGeneration: Int, target: File) {
    val circleClear = circleIdOf(target.name)?.let { circleClearedAt[it] } ?: 0
    if (maxOf(clearedAt, circleClear) > callGeneration) {
      throw IOException("Cache cleared while $target was being added")
    }
  }

  /**
   * Gives the complete file [temp] its cache name [target], replacing any previous file there.
   *
   * The rename is atomic (both files are in [dir]), on Android as well as on the JVM that runs the
   * tests. Runs under [dirLock] so it can't interleave with [clear], [clearCircle] or [trimToSize].
   *
   * @param callGeneration [generation] when the [getFile] or [put] call was made.
   * @throws IOException if [clear] or a [clearCircle] of its circle ran since [callGeneration], or
   *   if the rename fails.
   */
  private suspend fun commit(temp: File, target: File, callGeneration: Int) = dirLock.withLock {
    // checked again here, under dirLock: a clear may have run during the download or copy
    ensureNotCleared(callGeneration, target)
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
   * skips `.part` files, which are downloads in progress. Runs under [dirLock], so no cache hit can
   * use a file while it runs.
   */
  private suspend fun trimToSize(keep: File) = dirLock.withLock {
    // each file's time and size are read once, so the sort keys can't change while it runs (e.g.
    // the system touching a file), which can make the sort throw IllegalArgumentException
    val entries =
        dir.listFiles { f -> !f.name.endsWith(PART_SUFFIX) }
            .orEmpty()
            .map { Entry(it, it.lastModified(), it.length()) }
    var total = entries.sumOf { it.size }
    for (entry in entries.sortedBy { it.lastUsed }) { // oldest first
      if (total <= maxBytes) break
      if (entry.file == keep) continue
      entry.file.delete()
      if (!entry.file.exists()) total -= entry.size // also true if evict() or the system deleted it
    }
  }

  private companion object {
    /** Suffix of temporary files holding a download or copy in progress. */
    const val PART_SUFFIX = ".part"

    /** Start of the cache file name of every circle's media: `careCircles/{circleId}/...`. */
    const val CIRCLE_PREFIX = "careCircles_"
  }
}
