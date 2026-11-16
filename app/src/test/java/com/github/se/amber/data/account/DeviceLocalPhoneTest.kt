// Written by Alaabrine, with assistance from Cursor.

package com.github.se.amber.data.account

import coil3.ImageLoader
import coil3.memory.MemoryCache
import com.github.se.amber.data.media.image.MediaImageMemory
import com.github.se.amber.model.media.MediaRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeviceLocalPhoneTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private val mediaRepository: MediaRepository = mockk(relaxUnitFun = true)
  private val memoryCache: MemoryCache = mockk(relaxUnitFun = true)
  private lateinit var cacheDir: File
  private lateinit var picked: File
  private lateinit var captured: File
  private lateinit var otherFile: File
  private lateinit var recorded: File
  private lateinit var pickedMemo: File
  private lateinit var phone: DeviceLocalPhone
  private val calls = mutableListOf<String>()

  @Before
  fun setUp() {
    coEvery { mediaRepository.clearLocalMedia() } answers { calls += "clearMedia" }
    every { memoryCache.clear() } answers { calls += "clearMemory" }
    val imageLoader =
        mockk<ImageLoader> { every { memoryCache } returns this@DeviceLocalPhoneTest.memoryCache }
    cacheDir = tempFolder.newFolder("cache")
    picked = File(cacheDir, "picked_123.jpg").apply { writeText("photo") }
    captured =
        File(cacheDir, "captured_pictures/captured_123.jpg").apply {
          parentFile!!.mkdirs()
          writeText("photo")
        }
    otherFile = File(cacheDir, "other.txt").apply { writeText("kept") }
    recorded = File(cacheDir, "recording_123.m4a").apply { writeText("memo") }
    pickedMemo =
        File(cacheDir, "audio/picked_123.m4a").apply {
          parentFile!!.mkdirs()
          writeText("memo")
        }
    phone =
        DeviceLocalPhone(
            mediaRepository,
            MediaImageMemory(imageLoader),
            cacheDir,
            Dispatchers.Unconfined,
        )
  }

  @Test
  fun clearRunsMediaThenMemoryThenTemporaryFiles() = runBlocking {
    var filesStillThere = false
    every { memoryCache.clear() } answers
        {
          filesStillThere = listOf(picked, captured, recorded, pickedMemo).all { it.exists() }
          calls += "clearMemory"
        }

    phone.clear()

    assertTrue(filesStillThere)
    assertFalse(picked.exists())
    assertFalse(captured.parentFile!!.exists())
    assertFalse(recorded.exists())
    assertFalse(pickedMemo.exists())
    assertTrue(otherFile.exists())
    assertEquals(listOf("clearMedia", "clearMemory"), calls)
  }

  @Test
  fun clearRunsEveryStepAndNamesAFileThatStays() {
    val mediaFailure = IOException("Media left")
    val memoryFailure = IllegalStateException("Memory left")
    coEvery { mediaRepository.clearLocalMedia() } throws mediaFailure
    every { memoryCache.clear() } throws memoryFailure
    // a folder with a file in it can't be deleted, so the temporary-file step fails too
    val stuck = File(cacheDir, "picked_0.jpg").apply { mkdir() }
    File(stuck, "inside").writeText("photo")

    val error = assertThrows(IOException::class.java) { runBlocking { phone.clear() } }

    assertEquals(mediaFailure.message, error.message)
    val suppressed =
        generateSequence<Throwable>(error) { it.cause }.flatMap { it.suppressed.asList() }
    assertEquals(memoryFailure.message, suppressed.first().message)
    val fileFailure = suppressed.last().message.orEmpty()
    assertTrue(fileFailure, fileFailure.startsWith("Could not delete 1 temporary file(s), e.g. "))
    assertTrue(fileFailure, fileFailure.contains("picked_0.jpg"))
    assertFalse(picked.exists())
    assertFalse(captured.exists())
    assertFalse(recorded.exists())
    assertFalse(pickedMemo.exists())
    assertTrue(stuck.exists())
    assertTrue(otherFile.exists())
    assertEquals(emptyList<String>(), calls)
  }

  @Test
  fun aFileThatCannotBeDeletedNamesThatFile() {
    val stuck = File(cacheDir, "picked_0.jpg").apply { mkdir() }
    File(stuck, "inside").writeText("photo")
    val other = File(cacheDir, "picked_9.jpg").apply { writeText("photo") }

    val error = assertThrows(IOException::class.java) { runBlocking { phone.clear() } }

    assertEquals("Could not delete 1 temporary file(s), e.g. $stuck", error.message)
    assertFalse(picked.exists())
    assertFalse(other.exists())
    assertTrue(stuck.exists())
  }

  @Test
  fun aCancelledStepStopsAndIsRethrown() {
    coEvery { mediaRepository.clearLocalMedia() } throws CancellationException("stopped")

    val error = runCatching { runBlocking { phone.clear() } }.exceptionOrNull()

    assertTrue(error is CancellationException)
    assertEquals("stopped", error?.message)
    assertEquals(emptyList<String>(), calls)
    assertTrue(picked.exists())
  }

  @Test
  fun aCancellationAfterAnEarlierFailureIsRethrown() {
    coEvery { mediaRepository.clearLocalMedia() } throws IOException("Media left")
    every { memoryCache.clear() } throws CancellationException("stopped")

    val error = runCatching { runBlocking { phone.clear() } }.exceptionOrNull()

    assertTrue(error is CancellationException)
    assertEquals("stopped", error?.message)
    assertTrue(error?.suppressed?.isEmpty() == true)
    assertTrue(picked.exists())
  }
}
