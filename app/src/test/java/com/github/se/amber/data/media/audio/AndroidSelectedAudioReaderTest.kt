// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.media.audio

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import com.github.se.amber.data.media.MAX_FILE_BYTES
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.runs
import io.mockk.unmockkConstructor
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

/**
 * Checks [AndroidSelectedAudioReader] with a mocked content resolver, as for the picture reader,
 * and a mocked [MediaMetadataRetriever], since the copied file's name isn't known in advance.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidSelectedAudioReaderTest {

  @get:Rule val tempFolder = TemporaryFolder()

  private val uri = "content://com.android.providers.downloads/document/7"
  private val audioBytes = ByteArray(1_000) { it.toByte() }

  private lateinit var cacheDir: File
  private lateinit var pickedDir: File
  private lateinit var resolver: ContentResolver
  private lateinit var context: Context

  /** What the mocked retriever reads as the duration. */
  private var duration: String? = "3000"

  /** What the mocked retriever reads as the type, from the file's content. */
  private var contentType: String? = "audio/mp4"

  private val testScope = TestScope()

  @Before
  fun setUp() {
    cacheDir = tempFolder.newFolder("cache")
    pickedDir = File(cacheDir, "audio/picked")

    resolver = mockk()
    every { resolver.getType(any()) } returns "audio/mp4"
    every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(audioBytes) }

    val appContext = mockk<Context>()
    every { appContext.cacheDir } returns cacheDir
    every { appContext.contentResolver } returns resolver
    context = mockk()
    every { context.applicationContext } returns appContext

    mockkConstructor(MediaMetadataRetriever::class)
    every { anyConstructed<MediaMetadataRetriever>().setDataSource(any<String>()) } just runs
    every {
      anyConstructed<MediaMetadataRetriever>()
          .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
    } answers { duration }
    every {
      anyConstructed<MediaMetadataRetriever>()
          .extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
    } answers { contentType }
    every { anyConstructed<MediaMetadataRetriever>().release() } just runs
  }

  @After
  fun tearDown() {
    unmockkConstructor(MediaMetadataRetriever::class)
  }

  private fun reader(maxBytes: Long = 10_000) =
      AndroidSelectedAudioReader(context, StandardTestDispatcher(testScope.testScheduler), maxBytes)

  /** The reader the app uses, with the real limit. */
  private fun defaultReader() =
      AndroidSelectedAudioReader(context, StandardTestDispatcher(testScope.testScheduler))

  /** A stream of exactly [byteCount] zeros, served without holding them in memory. */
  private fun zeros(byteCount: Long): InputStream =
      object : InputStream() {
        private var left = byteCount

        override fun read(): Int = if (left-- > 0) 0 else -1

        override fun read(b: ByteArray, off: Int, len: Int): Int {
          if (left == 0L) return -1
          val served = minOf(len.toLong(), left).toInt()
          b.fill(0, off, off + served)
          left -= served
          return served
        }
      }

  private fun assertRejected(reader: AndroidSelectedAudioReader = reader()) = testScope.runTest {
    try {
      reader.read(uri)
      fail("Expected an IllegalArgumentException")
    } catch (_: IllegalArgumentException) {}
    assertTrue("no file is left behind", pickedDir.listFiles().isNullOrEmpty())
  }

  @Test
  fun copiesAValidAudioWithItsDuration() = testScope.runTest {
    val audio = reader().read(uri)

    assertEquals(pickedDir, audio.file.parentFile)
    assertTrue(audio.file.name.endsWith(".m4a"))
    assertArrayEquals(audioBytes, audio.file.readBytes())
    assertEquals(3_000L, audio.durationMs)
    assertTrue(audio.waveform.isEmpty())
    verify { anyConstructed<MediaMetadataRetriever>().release() }
  }

  @Test
  fun aLeftoverThatCantBeDeletedIsLoggedAndDoesntStopTheRead() = testScope.runTest {
    // a folder that isn't empty can't be deleted like a file
    File(pickedDir, "stuck/inner.m4a").apply {
      parentFile!!.mkdirs()
      writeText("old")
    }

    val audio = reader().read(uri)

    assertTrue(audio.file.exists())
    val warning = ShadowLog.getLogsForTag(AndroidSelectedAudioReader.TAG).single()
    assertEquals(Log.WARN, warning.type)
    assertTrue(warning.msg.contains("stuck"))
  }

  @Test
  fun acceptsEachM4aType() = testScope.runTest {
    for (type in listOf("audio/mp4", "audio/x-m4a", "audio/m4a")) {
      every { resolver.getType(any()) } returns type

      assertEquals("for $type", 3_000L, reader().read(uri).durationMs)
    }
  }

  @Test
  fun acceptsAnAudioWithoutATypeWhenItsContentIsM4a() = testScope.runTest {
    for (type in listOf(null, "application/octet-stream")) {
      every { resolver.getType(any()) } returns type

      assertEquals("for $type", 3_000L, reader().read(uri).durationMs)
    }
  }

  @Test
  fun rejectsAnotherTypeBeforeCopyingIt() {
    every { resolver.getType(any()) } returns "audio/mpeg"

    assertRejected()
    verify(exactly = 0) { resolver.openInputStream(any()) }
  }

  @Test
  fun rejectsAnAudioWithoutATypeWhenItsContentIsntM4a() {
    every { resolver.getType(any()) } returns null
    // e.g. an MP3 renamed song.m4a
    contentType = "audio/mpeg"

    assertRejected()
  }

  @Test
  fun rejectsAnAudioWhoseTypeSaysM4aButWhoseContentIsnt() {
    contentType = "audio/mpeg"

    assertRejected()
  }

  @Test
  fun rejectsAnAudioWhoseContentHasNoType() {
    contentType = null

    assertRejected()
  }

  @Test
  fun rejectsAnAudioThatCantBeOpened() {
    every { resolver.openInputStream(any()) } returns null

    assertRejected()
  }

  @Test
  fun rejectsAnAudioThatFailsWhileCopying() {
    every { resolver.openInputStream(any()) } returns
        object : InputStream() {
          override fun read(): Int = throw IOException("provider gone")
        }

    assertRejected()
  }

  @Test
  fun rejectsAnAudioWithoutDuration() {
    duration = null

    assertRejected()
  }

  @Test
  fun rejectsAnAudioWithAZeroDuration() {
    duration = "0"

    assertRejected()
  }

  @Test
  fun rejectsAFileTheRetrieverCantRead() {
    every { anyConstructed<MediaMetadataRetriever>().setDataSource(any<String>()) } throws
        RuntimeException("not audio")

    assertRejected()
  }

  @Test
  fun rejectsAnAudioOverTheLimitWithoutCopyingItAll() {
    var served = 0L
    every { resolver.openInputStream(any()) } returns
        object : InputStream() {
          override fun read(): Int = 0

          override fun read(b: ByteArray, off: Int, len: Int): Int {
            served += len
            return len
          }
        }

    assertRejected(reader(maxBytes = 100_000))
    assertTrue("it stops soon after the limit", served < 1_000_000)
  }

  @Test
  fun acceptsAnAudioOfExactlyTheUploadLimit() = testScope.runTest {
    every { resolver.openInputStream(any()) } answers { zeros(MAX_FILE_BYTES) }

    assertEquals(MAX_FILE_BYTES, defaultReader().read(uri).file.length())
  }

  @Test
  fun rejectsAnAudioOneByteOverTheUploadLimit() {
    every { resolver.openInputStream(any()) } answers { zeros(MAX_FILE_BYTES + 1) }

    assertRejected(defaultReader())
  }

  @Test
  fun leftoversAreDeletedOnFirstUseOnly() = testScope.runTest {
    pickedDir.mkdirs()
    val leftover = File(pickedDir, "picked_old.m4a").apply { writeText("old") }
    val reader = reader()

    val first = reader.read(uri)
    reader.read(uri)

    assertTrue(!leftover.exists())
    assertTrue(first.file.exists())
  }

  @Test
  fun aResolverErrorBeforeTheCopyIsRejectedToo() {
    // e.g. the app may no longer read the picked file
    every { resolver.getType(any()) } throws SecurityException("permission revoked")

    assertRejected()
  }

  @Test
  fun cancellingTheCallerStopsTheCopyAndDeletesTheFile() = testScope.runTest {
    lateinit var reading: Job
    var served = 0
    every { resolver.openInputStream(any()) } returns
        object : InputStream() {
          override fun read(): Int = 0

          override fun read(b: ByteArray, off: Int, len: Int): Int {
            served++
            // the user leaves the screen during the copy
            reading.cancel()
            return len
          }
        }

    reading = launch { reader().read(uri) }
    advanceUntilIdle()

    assertTrue(reading.isCancelled)
    assertEquals("the copy stops at once", 1, served)
    assertTrue(pickedDir.listFiles().isNullOrEmpty())
  }

  @Test
  fun aCallerCancelledOnceTheCopyIsDoneDoesntLeaveTheFile() = testScope.runTest {
    val io = QueuedDispatcher()
    val reader = AndroidSelectedAudioReader(context, io)

    val reading = launch { reader.read(uri) }
    runCurrent()
    // the audio is copied, but the caller leaves before taking it
    io.runAll()
    assertEquals(1, pickedDir.listFiles()!!.size)
    reading.cancel()
    runCurrent()

    assertTrue(pickedDir.listFiles().isNullOrEmpty())
  }
}
