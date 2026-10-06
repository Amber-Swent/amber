// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent
class MediaFileCacheTest {

  @get:Rule val tmp = TemporaryFolder()

  /** Fake remote storage: records each download, and can be told to fail or to pause. */
  private class FakeDownloader : MediaDownloader {
    val calls = mutableListOf<String>()
    var content: (String) -> String = { "content of $it" }
    var failure: Exception? = null
    var gate: CompletableDeferred<Unit>? = null // if set, downloads wait for it to complete
    val gates = mutableMapOf<String, CompletableDeferred<Unit>>() // same, for one path only

    override suspend fun download(storagePath: String, destination: File) {
      calls += storagePath
      destination.writeText("partial") // like a real download, bytes arrive before it ends
      (gates[storagePath] ?: gate)?.await()
      failure?.let { throw it }
      destination.writeText(content(storagePath))
    }
  }

  private val downloader = FakeDownloader()
  private lateinit var cacheDir: File

  @Before
  fun setUp() {
    cacheDir = File(tmp.root, "cache") // created by the cache itself
  }

  private fun TestScope.newCache(maxBytes: Long = 1024 * 1024) =
      MediaFileCache(downloader, cacheDir, maxBytes, StandardTestDispatcher(testScheduler))

  private fun cachedFileNames() = cacheDir.listFiles().orEmpty().map { it.name }.sorted()

  private fun sourceFile(text: String) = tmp.newFile().apply { writeText(text) }

  @Test
  fun getFileDownloadsOnCacheMiss() = runTest {
    val cache = newCache()

    val file = cache.getFile("careCircles/c1/media/m1.jpg")

    assertEquals("content of careCircles/c1/media/m1.jpg", file.readText())
    assertEquals(cacheDir, file.parentFile)
    assertEquals(listOf("careCircles/c1/media/m1.jpg"), downloader.calls)
  }

  @Test
  fun getFileReturnsCachedFileWithoutDownloadingAgain() = runTest {
    val cache = newCache()
    val first = cache.getFile("a/m1.jpg")
    downloader.content = { "changed remotely" }

    val second = cache.getFile("a/m1.jpg")

    assertEquals(first, second)
    assertEquals("content of a/m1.jpg", second.readText())
    assertEquals(1, downloader.calls.size)
  }

  @Test
  fun failedDownloadThrowsLeavesNothingAndIsRetried() = runTest {
    val cache = newCache()
    downloader.failure = IOException("offline")

    val error = runCatching { cache.getFile("a/m1.jpg") }.exceptionOrNull()

    assertEquals("offline", error?.message)
    assertEquals(emptyList<String>(), cachedFileNames()) // no cached file, no .part left
    downloader.failure = null
    assertEquals("content of a/m1.jpg", cache.getFile("a/m1.jpg").readText())
    assertEquals(2, downloader.calls.size)
  }

  @Test
  fun concurrentCallsForSamePathShareOneDownload() = runTest {
    val cache = newCache()
    val gate = CompletableDeferred<Unit>()
    downloader.gate = gate

    val first = async { cache.getFile("a/m1.jpg") }
    val second = async { cache.getFile("a/m1.jpg") }
    runCurrent() // first is downloading, second waits for it
    gate.complete(Unit)

    assertEquals(first.await(), second.await())
    assertEquals("content of a/m1.jpg", second.await().readText())
    assertEquals(1, downloader.calls.size)
  }

  @Test
  fun getCachedFileNeverDownloadsAndFindsCachedFiles() = runTest {
    val cache = newCache()

    assertNull(cache.getCachedFile("a/m1.jpg"))
    assertEquals(emptyList<String>(), downloader.calls)

    cache.getFile("a/m1.jpg")
    assertEquals("content of a/m1.jpg", cache.getCachedFile("a/m1.jpg")?.readText())
  }

  @Test
  fun putMovesSourceIntoCacheWithoutDownload() = runTest {
    val cache = newCache()
    val source = sourceFile("uploaded bytes")

    cache.put("a/m1.jpg", source)

    assertFalse(source.exists())
    assertEquals("uploaded bytes", cache.getFile("a/m1.jpg").readText())
    assertEquals(emptyList<String>(), downloader.calls)
  }

  @Test
  fun putReplacesAlreadyCachedFile() = runTest {
    val cache = newCache()
    cache.getFile("a/m1.jpg")

    cache.put("a/m1.jpg", sourceFile("new bytes"))

    assertEquals("new bytes", cache.getFile("a/m1.jpg").readText())
    assertEquals(1, downloader.calls.size)
  }

  @Test
  fun evictedFileIsDownloadedAgain() = runTest {
    val cache = newCache()
    cache.getFile("a/m1.jpg")

    cache.evict("a/m1.jpg")

    assertNull(cache.getCachedFile("a/m1.jpg"))
    cache.getFile("a/m1.jpg")
    assertEquals(2, downloader.calls.size)
  }

  @Test
  fun clearDeletesEveryCachedFile() = runTest {
    val cache = newCache()
    cache.getFile("a/m1.jpg")
    cache.put("a/m2.m4a", sourceFile("voice memo"))

    cache.clear()

    assertNull(cache.getCachedFile("a/m1.jpg"))
    assertNull(cache.getCachedFile("a/m2.m4a"))
    assertEquals(emptyList<String>(), cachedFileNames())
  }

  @Test
  fun downloadStartedBeforeClearIsNotCached() = runTest {
    val cache = newCache()
    val gate = CompletableDeferred<Unit>()
    downloader.gate = gate

    val pending = async { runCatching { cache.getFile("a/m1.jpg") } }
    runCurrent() // the download is in progress
    cache.clear()
    gate.complete(Unit)

    val error = pending.await().exceptionOrNull()
    assertTrue(error is IOException)
    assertTrue(error?.message.orEmpty().startsWith("Cache cleared")) // refused, not a failed move
    assertNull(cache.getCachedFile("a/m1.jpg"))
    assertEquals(emptyList<String>(), cachedFileNames())
  }

  @Test
  fun evictWaitsForDownloadInProgressSoFileDoesNotReappear() = runTest {
    val cache = newCache()
    val gate = CompletableDeferred<Unit>()
    downloader.gate = gate

    val download = async { cache.getFile("a/m1.jpg") }
    runCurrent() // the download is in progress
    val evict = async { cache.evict("a/m1.jpg") }
    runCurrent()
    assertFalse(evict.isCompleted) // waits for the download instead of deleting nothing
    gate.complete(Unit)
    download.await()
    evict.await()

    assertNull(cache.getCachedFile("a/m1.jpg"))
    assertEquals(emptyList<String>(), cachedFileNames())
  }

  @Test
  fun callsForDifferentPathsDoNotWaitForEachOther() = runTest {
    val cache = newCache()
    val slowGate = CompletableDeferred<Unit>()
    downloader.gates["a/slow.jpg"] = slowGate

    val slow = async { cache.getFile("a/slow.jpg") }
    runCurrent() // the slow download is in progress

    assertEquals("content of a/fast.jpg", cache.getFile("a/fast.jpg").readText())
    assertTrue(slow.isActive)
    slowGate.complete(Unit)
    assertEquals("content of a/slow.jpg", slow.await().readText())
  }

  @Test
  fun getCachedFileCountsAsUseForEviction() = runTest {
    val cache = newCache(maxBytes = 10)
    downloader.content = { "4 B." } // 4 bytes each: two fit, three don't
    cache.getFile("a/old.jpg").setLastModified(1_000)
    cache.getFile("a/older.jpg").setLastModified(500)
    cache.getCachedFile("a/older.jpg") // makes it the most recently used

    cache.getFile("a/new.jpg")

    assertEquals(listOf("a_new.jpg", "a_older.jpg"), cachedFileNames())
  }

  @Test
  fun trimNeverDeletesDownloadsInProgress() = runTest {
    val cache = newCache(maxBytes = 2) // anything added forces a trim
    val slowGate = CompletableDeferred<Unit>()
    downloader.gates["a/slow.jpg"] = slowGate
    val slow = async { cache.getFile("a/slow.jpg") }
    runCurrent() // its .part file holds the bytes received so far

    cache.getFile("a/fast.jpg") // trims the cache

    assertTrue(cachedFileNames().any { it.endsWith(".part") })
    slowGate.complete(Unit)
    assertEquals("content of a/slow.jpg", slow.await().readText())
  }

  @Test
  fun cancelledDownloadLeavesNothingBehind() = runTest {
    val cache = newCache()
    val gate = CompletableDeferred<Unit>()
    downloader.gate = gate

    val download = launch { cache.getFile("a/m1.jpg") }
    runCurrent() // the download is in progress, with a .part file
    download.cancel()
    download.join()

    assertEquals(emptyList<String>(), cachedFileNames())
    downloader.gate = null
    assertEquals("content of a/m1.jpg", cache.getFile("a/m1.jpg").readText()) // lock was released
  }

  @Test
  fun putWithMissingSourceThrowsAndCachesNothing() = runTest {
    val cache = newCache()

    val error = runCatching { cache.put("a/m1.jpg", File(tmp.root, "missing.jpg")) }
    val thrown = error.exceptionOrNull()

    assertTrue(thrown is IOException)
    assertNull(cache.getCachedFile("a/m1.jpg"))
    assertEquals(emptyList<String>(), cachedFileNames()) // no .part left either
  }

  @Test
  fun leastRecentlyUsedFileIsEvictedPastMaxBytes() = runTest {
    val cache = newCache(maxBytes = 10)
    downloader.content = { "4 B." } // 4 bytes each: two fit, three don't
    cache.getFile("a/old.jpg").setLastModified(1_000)
    cache.getFile("a/older.jpg").setLastModified(500)
    cache.getFile("a/older.jpg") // reading it again makes it the most recently used

    cache.getFile("a/new.jpg")

    assertEquals(listOf("a_new.jpg", "a_older.jpg"), cachedFileNames())
  }

  @Test
  fun fileBiggerThanMaxBytesIsStillReturnedAndKept() = runTest {
    val cache = newCache(maxBytes = 2)
    cache.getFile("a/m1.jpg")

    val file = cache.getFile("a/m2.jpg")

    assertEquals("content of a/m2.jpg", file.readText())
    assertEquals(listOf("a_m2.jpg"), cachedFileNames())
  }

  @Test
  fun leftoverPartFilesAreDeletedOnFirstUseButCachedFilesKept() = runTest {
    cacheDir.mkdirs()
    File(cacheDir, "download123.part").writeText("interrupted")
    File(cacheDir, "a_m1.jpg").writeText("cached before restart")

    val cache = newCache()
    assertEquals(2, cachedFileNames().size) // the constructor doesn't touch the disk

    assertNotNull(cache.getCachedFile("a/m1.jpg"))
    assertEquals(listOf("a_m1.jpg"), cachedFileNames())
    assertEquals("cached before restart", cache.getFile("a/m1.jpg").readText())
    assertEquals(emptyList<String>(), downloader.calls)
  }

  @Test
  fun invalidStoragePathsAreRejected() = runTest {
    val cache = newCache()

    for (path in listOf("", "  ", ".", "..", "a/m1.part")) {
      val error = runCatching { cache.getFile(path) }.exceptionOrNull()
      assertTrue("\"$path\" was accepted", error is IllegalArgumentException)
    }
    assertEquals(emptyList<String>(), downloader.calls)
  }

  @Test
  fun cacheKeepsWorkingAfterItsFolderIsDeleted() = runTest {
    val cache = newCache()
    cache.getFile("a/m1.jpg")

    cacheDir.deleteRecursively() // e.g. the user cleared the app's cache while it runs

    assertNull(cache.getCachedFile("a/m1.jpg"))
    assertEquals("content of a/m1.jpg", cache.getFile("a/m1.jpg").readText())
    assertEquals(2, downloader.calls.size)
  }
}
