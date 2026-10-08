// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks [MediaFileCache.put] on a real Android filesystem, which the JVM unit tests can't do: a
 * move from another storage volume (copy, then delete) and replacing an existing entry on internal
 * storage.
 */
@RunWith(AndroidJUnit4::class)
class MediaFileCacheInstrumentedTest {

  private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
  private val cacheDir = File(context.cacheDir, "media-cache-test")
  private val sources = mutableListOf<File>()

  private val cache = MediaFileCache(dir = cacheDir)

  /** [MediaFileCache.getFile] of a file [MediaFileCache.put] added: it must never download. */
  private suspend fun cached(storagePath: String) =
      cache.getFile(storagePath) { throw AssertionError("put must not download $storagePath") }

  @Before
  fun setUp() {
    cacheDir.deleteRecursively()
  }

  @After
  fun tearDown() {
    cacheDir.deleteRecursively()
    sources.forEach { it.delete() }
  }

  /**
   * Whether a plain rename from [from] to [to] fails, i.e. whether a move between them must copy.
   * Tried for real rather than guessed from mount points, which can hide a separate filesystem.
   */
  private fun renameFailsBetween(from: File, to: File): Boolean {
    val probe = File(from, "volume-probe.tmp").apply { writeText("probe") }
    val moved = File(to, "volume-probe.tmp")
    try {
      return !probe.renameTo(moved)
    } finally {
      probe.delete()
      moved.delete()
    }
  }

  private fun sourceFile(dir: File, name: String, bytes: ByteArray) =
      File(dir, name).apply { writeBytes(bytes) }.also { sources += it }

  @Test
  fun putFromAnotherVolumeCopiesAllBytesAndRemovesSource() = runBlocking {
    val externalDir = context.getExternalFilesDir(null)
    assumeNotNull(externalDir) // skipped if no shared storage is mounted
    // only meaningful if the move really has to copy; skipped rather than passing falsely
    assumeTrue(renameFailsBetween(externalDir!!, context.cacheDir))
    val bytes = Random(42).nextBytes(4 * 1024 * 1024) // big enough that a partial copy would show
    val source = sourceFile(externalDir, "upload.jpg", bytes)
    source.setLastModified(OLD_TIME) // e.g. a photo taken years ago
    val before = System.currentTimeMillis()

    cache.put("careCircles/c1/media/m1.jpg", source)

    assertFalse(source.exists()) // moved, not copied
    assertEquals(listOf("careCircles_c1_media_m1.jpg"), cacheDir.list()!!.toList()) // no .part
    // read before any getFile, which would itself mark the file as used. The margin allows for
    // filesystems that store times in whole seconds
    val cached = File(cacheDir, "careCircles_c1_media_m1.jpg")
    assertTrue(cached.lastModified() >= before - 2_000) // reset, so it isn't evicted first
    assertArrayEquals(bytes, cached.readBytes())
    assertEquals(cached, cached("careCircles/c1/media/m1.jpg")) // no download needed
  }

  @Test
  fun putOnSameVolumeReplacesExistingEntry() = runBlocking {
    assumeTrue(!renameFailsBetween(context.filesDir, context.cacheDir))
    val first = Random(1).nextBytes(1024 * 1024)
    val second = Random(2).nextBytes(1024 * 1024)

    cache.put("a/m1.jpg", sourceFile(context.filesDir, "first.jpg", first))
    cache.put("a/m1.jpg", sourceFile(context.filesDir, "second.jpg", second))

    assertArrayEquals(second, cached("a/m1.jpg").readBytes())
    assertTrue(sources.none { it.exists() }) // both sources were moved
    assertEquals(listOf("a_m1.jpg"), cacheDir.list()!!.toList()) // one entry, no .part
  }

  private companion object {
    const val OLD_TIME = 1_577_836_800_000L // 2020-01-01
  }
}
