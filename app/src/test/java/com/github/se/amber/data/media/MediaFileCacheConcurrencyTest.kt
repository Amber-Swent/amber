// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Runs [MediaFileCache] on real threads, so hits, downloads and trims of many paths really run at
 * the same time, which the single-threaded tests in [MediaFileCacheTest] can't do. Only asserts
 * what must hold whatever the timing; a pass makes a race unlikely, it doesn't prove there is none.
 */
class MediaFileCacheConcurrencyTest {

  @get:Rule val tmp = TemporaryFolder()

  private val cacheDir by lazy { File(tmp.root, "cache") }

  /** All files have the same size, so at most [MAX_FILES] of them fit in maxBytes. */
  private fun contentOf(storagePath: String) = storagePath.padEnd(FILE_SIZE, '.')

  private fun newCache() =
      MediaFileCache(
          dir = cacheDir,
          maxBytes = MAX_FILES * FILE_SIZE.toLong(),
      ) // default ioDispatcher: Dispatchers.IO, real threads

  private fun cachedFiles() = cacheDir.listFiles().orEmpty().toList()

  @Test
  fun concurrentHitsAndTrimsNeverFailOrServeWrongContent() = runBlocking {
    val cache = newCache()
    val wrong = ConcurrentLinkedQueue<String>()

    withTimeout(TIMEOUT) {
      (0 until 8)
          .map { worker ->
            launch(Dispatchers.IO) {
              val random = Random(worker)
              repeat(300) {
                // 150 paths but room for only 50 files: frequent misses (trims) and hits at once
                val path = "a/m${random.nextInt(150)}.jpg"
                val file = cache.getFile(path) { it.writeText(contentOf(path)) }
                // null: evicted by another worker's trim after it was returned, which the cache
                // allows
                val text = runCatching { file.readText() }.getOrNull()
                if (text != null && text != contentOf(path)) wrong += "$path -> $text"
              }
            }
          }
          .joinAll() // an exception in any worker fails the test
    }

    assertTrue("wrong content served: $wrong", wrong.isEmpty())
    assertTrue(cachedFiles().none { it.name.endsWith(".part") })
    cache.getFile("a/last.jpg") {
      it.writeText(contentOf("a/last.jpg"))
    } // a trim with nothing running at the same time
    assertTrue(cachedFiles().size <= MAX_FILES)
  }

  @Test
  fun clearCircleRacingWithHitsDownloadsAndTrimsKeepsOtherCirclesWorking() = runBlocking {
    val cache = newCache()
    val wrong = ConcurrentLinkedQueue<String>()

    withTimeout(TIMEOUT) {
      val readers =
          (0 until 8).map { worker ->
            launch(Dispatchers.IO) {
              val random = Random(worker)
              repeat(300) {
                val circle = if (random.nextBoolean()) "c1" else "c2"
                val path = "careCircles/$circle/media/m${random.nextInt(75)}.jpg"
                val file =
                    try {
                      cache.getFile(path) { it.writeText(contentOf(path)) }
                    } catch (e: IOException) {
                      // a c1 file whose call was made before a clearCircle("c1") is refused
                      if (circle == "c1") return@repeat else throw e
                    }
                val text = runCatching { file.readText() }.getOrNull()
                if (text != null && text != contentOf(path)) wrong += "$path -> $text"
              }
            }
          }
      launch(Dispatchers.IO) { repeat(50) { cache.clearCircle("c1") } }
      readers.joinAll() // an exception in any worker fails the test
    }

    assertTrue("wrong content served: $wrong", wrong.isEmpty())
    assertTrue(cachedFiles().none { it.name.endsWith(".part") })
    cache.clearCircle("c1") // with nothing running at the same time
    assertEquals(emptyList<String>(), cachedFiles().map { it.name }.filter { "_c1_" in it })
    val c2Path = "careCircles/c2/media/last.jpg"
    assertEquals(
        contentOf(c2Path),
        cache.getFile(c2Path) { it.writeText(contentOf(c2Path)) }.readText(),
    )
    assertTrue(cachedFiles().size <= MAX_FILES)
  }

  private companion object {
    const val FILE_SIZE = 100 // bytes
    const val MAX_FILES = 50
    val TIMEOUT = 60.seconds
  }
}
