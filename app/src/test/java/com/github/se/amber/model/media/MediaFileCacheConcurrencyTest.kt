// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Runs [MediaFileCache] on real threads, so cache hits really happen while a trim runs, which the
 * single-threaded tests in [MediaFileCacheTest] can't do. Only asserts what must hold whatever the
 * timing; a pass makes a race unlikely, it doesn't prove there is none.
 */
class MediaFileCacheConcurrencyTest {

  @get:Rule val tmp = TemporaryFolder()

  private val cacheDir by lazy { File(tmp.root, "cache") }

  /** All files have the same size, so at most [MAX_FILES] of them fit in maxBytes. */
  private fun contentOf(storagePath: String) = storagePath.padEnd(FILE_SIZE, '.')

  private fun newCache() =
      MediaFileCache(
          downloader = { path, destination -> destination.writeText(contentOf(path)) },
          dir = cacheDir,
          maxBytes = MAX_FILES * FILE_SIZE.toLong(),
      ) // default ioDispatcher: Dispatchers.IO, real threads

  private fun cachedFiles() = cacheDir.listFiles().orEmpty().toList()

  @Test
  fun hitsDuringTrimsNeverFailOrServeWrongContent() = runBlocking {
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
                val file =
                    if (random.nextBoolean()) cache.getFile(path) else cache.getCachedFile(path)
                // null: not cached, or evicted before it could be read; both allowed
                val text = file?.let { runCatching { it.readText() }.getOrNull() }
                if (text != null && text != contentOf(path)) wrong += "$path -> $text"
              }
            }
          }
          .joinAll() // an exception in any worker (e.g. from the sort) fails the test
    }

    assertTrue("wrong content served: $wrong", wrong.isEmpty())
    assertTrue(cachedFiles().none { it.name.endsWith(".part") })
    cache.getFile("a/last.jpg") // a trim with nothing running at the same time
    assertTrue(cachedFiles().size <= MAX_FILES)
  }

  private companion object {
    const val FILE_SIZE = 100 // bytes
    const val MAX_FILES = 50
    val TIMEOUT = 60.seconds
  }
}
