// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import java.io.File
import java.io.IOException
import java.nio.file.Path
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent
class MediaFileCacheTest {

  @get:Rule val tmp = TemporaryFolder()

  /** Fake remote storage: records each download, and can be told to fail or to pause. */
  private class FakeStorage {
    val calls = mutableListOf<String>()
    var content: (String) -> String = { "content of $it" }
    var failure: Exception? = null
    var gate: CompletableDeferred<Unit>? = null // if set, downloads wait for it to complete
    val gates = mutableMapOf<String, CompletableDeferred<Unit>>() // same, for one path only

    suspend fun download(storagePath: String, destination: File) {
      calls += storagePath
      destination.writeText("partial") // like a real download, bytes arrive before it ends
      (gates[storagePath] ?: gate)?.await()
      failure?.let { throw it }
      destination.writeText(content(storagePath))
    }
  }

  private val storage = FakeStorage()
  private lateinit var cacheDir: File

  @Before
  fun setUp() {
    cacheDir = File(tmp.root, "cache") // created by the cache itself
  }

  private fun TestScope.newCache(maxBytes: Long = 1024 * 1024) =
      MediaFileCache(cacheDir, maxBytes, StandardTestDispatcher(testScheduler))

  /** [MediaFileCache.getFile], downloading from [storage] on a miss like the app's repository. */
  private suspend fun MediaFileCache.fetch(storagePath: String) =
      getFile(storagePath) { storage.download(storagePath, it) }

  private fun cachedFileNames() = cacheDir.listFiles().orEmpty().map { it.name }.sorted()

  /** Whether the cache folder holds the file of [storagePath] (named as the cache names it). */
  private fun isCached(storagePath: String) = File(cacheDir, storagePath.replace('/', '_')).exists()

  private fun sourceFile(text: String) = tmp.newFile().apply { writeText(text) }

  @Test
  fun getFileDownloadsOnCacheMiss() = runTest {
    val cache = newCache()

    val file = cache.fetch("careCircles/c1/media/m1.jpg")

    assertEquals("content of careCircles/c1/media/m1.jpg", file.readText())
    assertEquals(cacheDir, file.parentFile)
    assertEquals(listOf("careCircles/c1/media/m1.jpg"), storage.calls)
  }

  @Test
  fun getFileReturnsCachedFileWithoutDownloadingAgain() = runTest {
    val cache = newCache()
    val first = cache.fetch("a/m1.jpg")
    storage.content = { "changed remotely" }

    val second = cache.fetch("a/m1.jpg")

    assertEquals(first, second)
    assertEquals("content of a/m1.jpg", second.readText())
    assertEquals(listOf("a/m1.jpg"), storage.calls)
  }

  @Test
  fun failedDownloadThrowsLeavesNothingAndIsRetried() = runTest {
    val cache = newCache()
    storage.failure = IOException("offline")

    val error = runCatching { cache.fetch("a/m1.jpg") }.exceptionOrNull()

    assertEquals("offline", error?.message)
    assertEquals(emptyList<String>(), cachedFileNames()) // no cached file, no .part left
    storage.failure = null
    assertEquals("content of a/m1.jpg", cache.fetch("a/m1.jpg").readText())
    assertEquals(listOf("a/m1.jpg", "a/m1.jpg"), storage.calls)
  }

  @Test
  fun concurrentCallsForSamePathShareOneDownload() = runTest {
    val cache = newCache()
    val gate = CompletableDeferred<Unit>()
    storage.gate = gate

    val first = async { cache.fetch("a/m1.jpg") }
    val second = async { cache.fetch("a/m1.jpg") }
    runCurrent() // first is downloading, second waits for it
    gate.complete(Unit)

    assertEquals(first.await(), second.await())
    assertEquals("content of a/m1.jpg", second.await().readText())
    assertEquals(listOf("a/m1.jpg"), storage.calls)
  }

  @Test
  fun putMovesSourceIntoCacheWithoutDownload() = runTest {
    val cache = newCache()
    val source = sourceFile("uploaded bytes")

    cache.put("a/m1.jpg", source)

    assertFalse(source.exists())
    assertEquals("uploaded bytes", cache.fetch("a/m1.jpg").readText())
    assertEquals(emptyList<String>(), storage.calls)
  }

  @Test
  fun putReplacesAlreadyCachedFile() = runTest {
    val cache = newCache()
    cache.fetch("a/m1.jpg")

    cache.put("a/m1.jpg", sourceFile("new bytes"))

    assertEquals("new bytes", cache.fetch("a/m1.jpg").readText())
    assertEquals(listOf("a/m1.jpg"), storage.calls)
  }

  @Test
  fun evictedFileIsDownloadedAgain() = runTest {
    val cache = newCache()
    cache.fetch("a/m1.jpg")

    cache.evict("a/m1.jpg")

    assertFalse(isCached("a/m1.jpg"))
    cache.fetch("a/m1.jpg")
    assertEquals(listOf("a/m1.jpg", "a/m1.jpg"), storage.calls)
  }

  @Test
  fun clearDeletesEveryCachedFile() = runTest {
    val cache = newCache()
    cache.fetch("a/m1.jpg")
    cache.put("a/m2.m4a", sourceFile("voice memo"))

    cache.clear()

    assertFalse(isCached("a/m1.jpg"))
    assertFalse(isCached("a/m2.m4a"))
    assertEquals(emptyList<String>(), cachedFileNames())
  }

  @Test
  fun clearCircleDeletesOnlyThatCirclesFiles() = runTest {
    val cache = newCache()
    cache.fetch("careCircles/c1/media/m1.jpg")
    cache.put("careCircles/c1/media/m2.m4a", sourceFile("voice memo"))
    cache.fetch("careCircles/c2/media/m1.jpg")
    cache.fetch("careCircles/c10/media/m1.jpg") // its id starts like c1's

    cache.clearCircle("c1")

    assertEquals(
        listOf("careCircles_c10_media_m1.jpg", "careCircles_c2_media_m1.jpg"),
        cachedFileNames(),
    )
  }

  @Test
  fun downloadOfClearedCircleStartedBeforeIsNotCachedButOtherCirclesAre() = runTest {
    val cache = newCache()
    val gate = CompletableDeferred<Unit>()
    storage.gate = gate

    val cleared = async { runCatching { cache.fetch("careCircles/c1/media/m1.jpg") } }
    val other = async { runCatching { cache.fetch("careCircles/c2/media/m1.jpg") } }
    runCurrent() // both downloads are in progress
    cache.clearCircle("c1") // e.g. the user leaves c1 while a c2 photo loads too
    gate.complete(Unit)

    val error = cleared.await().exceptionOrNull()
    assertTrue(error?.message.orEmpty().startsWith("Cache cleared"))
    assertTrue(other.await().isSuccess)
    assertEquals(listOf("careCircles_c2_media_m1.jpg"), cachedFileNames())
  }

  @Test
  fun callMadeAfterClearCircleIsNotRefusedByLaterClearOfAnotherCircle() = runTest {
    val cache = newCache()
    cache.clearCircle("c1")
    val gate = CompletableDeferred<Unit>()
    storage.gate = gate

    val download = async { cache.fetch("careCircles/c1/media/m1.jpg") }
    runCurrent() // the download is in progress
    cache.clearCircle("c2")
    gate.complete(Unit)

    assertEquals("content of careCircles/c1/media/m1.jpg", download.await().readText())
  }

  @Test
  fun invalidCircleIdsAreRejected() = runTest {
    val cache = newCache()
    cache.fetch("careCircles/c1/media/m1.jpg")

    for (circleId in listOf("", "  ", "c1/media", "c_1")) {
      val error = runCatching { cache.clearCircle(circleId) }.exceptionOrNull()
      assertTrue("\"$circleId\" was accepted", error is IllegalArgumentException)
    }
    assertEquals(listOf("careCircles_c1_media_m1.jpg"), cachedFileNames())
  }

  @Test
  fun getFileWaitingBeforeClearDoesNotDownloadAfterIt() = runTest {
    val cache = newCache()
    val gate = CompletableDeferred<Unit>()
    storage.gate = gate
    val first = async { runCatching { cache.fetch("a/m1.jpg") } }
    val waiting = async { runCatching { cache.fetch("a/m1.jpg") } }
    runCurrent() // first is downloading, waiting waits for its path lock

    cache.clear() // e.g. sign-out while a list and a detail screen load the same photo
    gate.complete(Unit)

    val error = waiting.await().exceptionOrNull()
    assertTrue(error?.message.orEmpty().startsWith("Cache cleared"))
    // the download in progress was refused at commit, not by a failed move
    assertTrue(first.await().exceptionOrNull()?.message.orEmpty().startsWith("Cache cleared"))
    assertEquals(listOf("a/m1.jpg"), storage.calls) // the waiting call never downloaded
    assertEquals(emptyList<String>(), cachedFileNames())
  }

  @Test
  fun putWaitingBeforeClearIsRefusedAndKeepsItsSource() = runTest {
    val cache = newCache()
    val gate = CompletableDeferred<Unit>()
    storage.gate = gate
    val source = sourceFile("uploaded bytes")
    val download = async { runCatching { cache.fetch("a/m1.jpg") } }
    val put = async { runCatching { cache.put("a/m1.jpg", source) } }
    runCurrent() // the put waits for the download's path lock

    cache.clear()
    gate.complete(Unit)
    download.await()

    val error = put.await().exceptionOrNull()
    assertTrue(error?.message.orEmpty().startsWith("Cache cleared"))
    assertTrue(source.exists()) // refused before it was moved
    assertEquals(emptyList<String>(), cachedFileNames())
  }

  @Test
  fun putRefusedAfterItsSourceWasMovedLosesTheSourceAndCachesNothing() = runTest {
    // Unconfined: clear() can then run to completion inside the put, see below
    val cache = MediaFileCache(cacheDir, ioDispatcher = Dispatchers.Unconfined)
    val bytes = sourceFile("uploaded bytes")
    // a source whose path, read by put right before it moves the file, triggers a clear: the
    // clear then lands after put's first check but before its commit
    val source =
        object : File(bytes.path) {
          override fun toPath(): Path {
            runBlocking { cache.clear() }
            return super.toPath()
          }
        }

    val error = runCatching { cache.put("a/m1.jpg", source) }.exceptionOrNull()

    assertTrue(error?.message.orEmpty().startsWith("Cache cleared"))
    assertFalse(bytes.exists()) // already moved when the commit refused it, as documented
    assertEquals(emptyList<String>(), cachedFileNames()) // no cached file, no .part left
  }

  @Test
  fun evictWaitsForDownloadInProgressSoFileDoesNotReappear() = runTest {
    val cache = newCache()
    val gate = CompletableDeferred<Unit>()
    storage.gate = gate

    val download = async { cache.fetch("a/m1.jpg") }
    runCurrent() // the download is in progress
    val evict = async { cache.evict("a/m1.jpg") }
    runCurrent()
    assertFalse(evict.isCompleted) // waits for the download instead of deleting nothing
    gate.complete(Unit)
    download.await()
    evict.await()

    assertFalse(isCached("a/m1.jpg"))
    assertEquals(emptyList<String>(), cachedFileNames())
  }

  @Test
  fun callsForDifferentPathsDoNotWaitForEachOther() = runTest {
    val cache = newCache()
    val slowGate = CompletableDeferred<Unit>()
    storage.gates["a/slow.jpg"] = slowGate

    val slow = async { cache.fetch("a/slow.jpg") }
    runCurrent() // the slow download is in progress

    assertEquals("content of a/fast.jpg", cache.fetch("a/fast.jpg").readText())
    assertTrue(slow.isActive)
    slowGate.complete(Unit)
    assertEquals("content of a/slow.jpg", slow.await().readText())
  }

  @Test
  fun trimNeverDeletesDownloadsInProgress() = runTest {
    val cache = newCache(maxBytes = 2) // anything added forces a trim
    val slowGate = CompletableDeferred<Unit>()
    storage.gates["a/slow.jpg"] = slowGate
    val slow = async { cache.fetch("a/slow.jpg") }
    runCurrent() // its .part file holds the bytes received so far

    cache.fetch("a/fast.jpg") // trims the cache

    assertTrue(cachedFileNames().any { it.endsWith(".part") })
    slowGate.complete(Unit)
    assertEquals("content of a/slow.jpg", slow.await().readText())
  }

  @Test
  fun cancelledDownloadLeavesNothingBehind() = runTest {
    val cache = newCache()
    val gate = CompletableDeferred<Unit>()
    storage.gate = gate

    val download = launch { cache.fetch("a/m1.jpg") }
    runCurrent() // the download is in progress, with a .part file
    download.cancel()
    download.join()

    assertEquals(emptyList<String>(), cachedFileNames())
    storage.gate = null
    assertEquals("content of a/m1.jpg", cache.fetch("a/m1.jpg").readText()) // lock was released
  }

  @Test
  fun putWithMissingSourceThrowsAndCachesNothing() = runTest {
    val cache = newCache()

    val error = runCatching { cache.put("a/m1.jpg", File(tmp.root, "missing.jpg")) }
    val thrown = error.exceptionOrNull()

    assertTrue(thrown is IOException)
    assertFalse(isCached("a/m1.jpg"))
    assertEquals(emptyList<String>(), cachedFileNames()) // no .part left either
  }

  @Test
  fun leastRecentlyUsedFileIsEvictedPastMaxBytes() = runTest {
    val cache = newCache(maxBytes = 10)
    storage.content = { "4 B." } // 4 bytes each: two fit, three don't
    cache.fetch("a/old.jpg").setLastModified(1_000)
    cache.fetch("a/older.jpg").setLastModified(500)
    cache.fetch("a/older.jpg") // reading it again makes it the most recently used

    cache.fetch("a/new.jpg")

    assertEquals(listOf("a_new.jpg", "a_older.jpg"), cachedFileNames())
  }

  @Test
  fun fileBiggerThanMaxBytesIsStillReturnedAndKept() = runTest {
    val cache = newCache(maxBytes = 2)
    cache.fetch("a/m1.jpg")

    val file = cache.fetch("a/m2.jpg")

    assertEquals("content of a/m2.jpg", file.readText())
    assertEquals(listOf("a_m2.jpg"), cachedFileNames())
  }

  @Test
  fun leftoverPartFilesAreDeletedOnFirstUseButCachedFilesKept() = runTest {
    cacheDir.mkdirs()
    File(cacheDir, "download123.part").writeText("interrupted")
    File(cacheDir, "a_m1.jpg").writeText("cached before restart")

    val cache = newCache()
    // the constructor doesn't touch the disk
    assertEquals(listOf("a_m1.jpg", "download123.part"), cachedFileNames())

    val file = cache.fetch("a/m1.jpg") // first use: a cache hit, no download

    assertEquals(listOf("a_m1.jpg"), cachedFileNames())
    assertEquals("cached before restart", file.readText())
    assertEquals(emptyList<String>(), storage.calls)
  }

  @Test
  fun invalidStoragePathsAreRejected() = runTest {
    val cache = newCache()

    for (path in listOf("", "  ", ".", "..", "a/m1.part")) {
      val error = runCatching { cache.fetch(path) }.exceptionOrNull()
      assertTrue("\"$path\" was accepted", error is IllegalArgumentException)
    }
    assertEquals(emptyList<String>(), storage.calls)
  }

  @Test
  fun cacheKeepsWorkingAfterItsFolderIsDeleted() = runTest {
    val cache = newCache()
    cache.fetch("a/m1.jpg")

    cacheDir.deleteRecursively() // e.g. the user cleared the app's cache while it runs

    assertFalse(isCached("a/m1.jpg"))
    assertEquals("content of a/m1.jpg", cache.fetch("a/m1.jpg").readText())
    assertEquals(listOf("a/m1.jpg", "a/m1.jpg"), storage.calls)
  }

  @Test
  fun putFileWithOldTimestampIsNotEvictedFirst() = runTest {
    val cache = newCache(maxBytes = 10)
    storage.content = { "4 B." } // 4 bytes each: two fit, three don't
    cache.fetch("a/downloaded.jpg").setLastModified(2_000)
    val oldPhoto = sourceFile("4 B.").apply { setLastModified(1_000) } // e.g. taken years ago

    cache.put("a/uploaded.jpg", oldPhoto)
    cache.fetch("a/new.jpg")

    // put counts as a use: the downloaded file is now the least recently used one
    assertEquals(listOf("a_new.jpg", "a_uploaded.jpg"), cachedFileNames())
  }

  @Test
  fun hitsDoNotTrimTheCache() = runTest {
    cacheDir.mkdirs() // two files left over a 5-byte limit, e.g. from a run with a higher limit
    File(cacheDir, "a_m1.jpg").writeText("4 B.")
    File(cacheDir, "a_m2.jpg").writeText("4 B.")
    val cache = newCache(maxBytes = 5)

    cache.fetch("a/m1.jpg")
    cache.fetch("a/m2.jpg")

    assertEquals(listOf("a_m1.jpg", "a_m2.jpg"), cachedFileNames()) // still over the limit
    assertEquals(emptyList<String>(), storage.calls)
  }

  @Test
  fun nonPositiveMaxBytesIsRejected() = runTest {
    for (maxBytes in listOf(0L, -1L)) {
      val error = runCatching { newCache(maxBytes) }.exceptionOrNull()
      assertTrue("maxBytes $maxBytes was accepted", error is IllegalArgumentException)
    }
  }
}
