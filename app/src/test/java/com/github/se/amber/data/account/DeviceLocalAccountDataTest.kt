// Written by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.account

import coil3.ImageLoader
import coil3.memory.MemoryCache
import com.github.se.amber.data.media.image.MediaImageMemory
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.media.MediaRepository
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeviceLocalAccountDataTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private val mediaRepository: MediaRepository = mockk(relaxUnitFun = true)
  private val authRepository: AuthRepository = mockk()
  private val memoryCache: MemoryCache = mockk(relaxUnitFun = true)
  private lateinit var cacheDir: File
  private lateinit var picked: File
  private lateinit var captured: File
  private lateinit var otherFile: File
  private lateinit var recorded: File
  private lateinit var pickedMemo: File
  private lateinit var localData: DeviceLocalAccountData
  private val firestore: FirebaseFirestore = mockk()
  private val calls = mutableListOf<String>()

  @Before
  fun setUp() {
    coEvery { authRepository.signOut() } returns Result.success(Unit)
    val imageLoader =
        mockk<ImageLoader> {
          every { memoryCache } returns this@DeviceLocalAccountDataTest.memoryCache
        }

    every { firestore.terminate() } answers
        {
          calls += "terminate"
          Tasks.forResult(null)
        }
    every { firestore.clearPersistence() } answers
        {
          calls += "clearPersistence"
          Tasks.forResult(null)
        }

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

    localData =
        DeviceLocalAccountData(
            mediaRepository,
            authRepository,
            MediaImageMemory(imageLoader),
            cacheDir,
            firestore,
            { calls += "restart" },
            Dispatchers.Unconfined,
        )
  }

  @Test
  fun clearRemovesEverythingOfTheUserAndSignsOut() = runBlocking {
    localData.clear()

    coVerify(exactly = 1) { mediaRepository.clearLocalMedia() }
    verify(exactly = 1) { memoryCache.clear() }
    assertFalse(picked.exists())
    assertFalse(captured.parentFile!!.exists())
    assertFalse(recorded.exists())
    assertFalse(pickedMemo.exists())
    assertTrue(otherFile.exists())
    coVerify(exactly = 1) { authRepository.signOut() }
    assertEquals(listOf("terminate", "clearPersistence", "restart"), calls)
  }

  @Test
  fun clearWorksWhenNoPictureWasEverTaken() = runBlocking {
    captured.parentFile!!.deleteRecursively()
    picked.delete()

    localData.clear()

    coVerify(exactly = 1) { authRepository.signOut() }
  }

  @Test
  fun aFailedStepStillRunsTheOthersAndEveryFailureIsReported() {
    val mediaFailure = IOException("Media left")
    val signOutFailure = IllegalStateException("Still signed in")
    coEvery { mediaRepository.clearLocalMedia() } throws mediaFailure
    coEvery { authRepository.signOut() } returns Result.failure(signOutFailure)

    val error = assertThrows(IOException::class.java) { runBlocking { localData.clear() } }

    assertEquals(mediaFailure.message, error.message)
    // coroutines may wrap what they rethrow in a copy: the original is then its cause
    val suppressed =
        generateSequence<Throwable>(error) { it.cause }.flatMap { it.suppressed.asList() }
    assertEquals(listOf(signOutFailure.message), suppressed.map { it.message }.toList())
    verify(exactly = 1) { memoryCache.clear() }
    assertFalse(picked.exists())
    // Firestore stays usable, and the app open, so that clearing can run again
    assertEquals(emptyList<String>(), calls)
  }

  @Test
  fun aFileThatCannotBeDeletedDoesNotKeepTheOthers() {
    // a folder with a file in it can't be deleted: sorted first, like any name in listFiles may be
    val stuck = File(cacheDir, "picked_0.jpg").apply { mkdir() }
    File(stuck, "inside").writeText("photo")
    val other = File(cacheDir, "picked_9.jpg").apply { writeText("photo") }

    assertThrows(IOException::class.java) { runBlocking { localData.clear() } }

    assertFalse(picked.exists())
    assertFalse(other.exists())
    assertFalse(captured.exists())
  }

  @Test
  fun aFirestoreThatCannotBeClearedStillRestartsTheApp() {
    every { firestore.clearPersistence() } returns Tasks.forException(IOException("Cache left"))

    assertThrows(IOException::class.java) { runBlocking { localData.clear() } }

    assertEquals(listOf("terminate", "restart"), calls)
  }

  @Test
  fun leavingTheScreenDoesNotInterruptClearing() = runBlocking {
    val clearing = CompletableDeferred<Unit>()
    val mayFinish = CompletableDeferred<Unit>()
    coEvery { mediaRepository.clearLocalMedia() } coAnswers
        {
          clearing.complete(Unit)
          mayFinish.await()
        }
    val job = launch(Dispatchers.Default) { localData.clear() }

    clearing.await()
    job.cancel()
    mayFinish.complete(Unit)
    job.join()

    assertEquals(listOf("terminate", "clearPersistence", "restart"), calls)
  }
}
