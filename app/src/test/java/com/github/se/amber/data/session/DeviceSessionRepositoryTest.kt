// Written by Alaabrine, with assistance from Cursor.

package com.github.se.amber.data.session

import coil3.ImageLoader
import coil3.memory.MemoryCache
import com.github.se.amber.data.account.DeviceLocalPhone
import com.github.se.amber.data.media.image.MediaImageMemory
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.media.MediaRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeviceSessionRepositoryTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private val mediaRepository: MediaRepository = mockk(relaxUnitFun = true)
  private val authRepository: AuthRepository = mockk()
  private val memoryCache: MemoryCache = mockk(relaxUnitFun = true)
  private val calls = mutableListOf<String>()
  private lateinit var picked: File
  private lateinit var otherFile: File
  private lateinit var session: DeviceSessionRepository

  @Before
  fun setUp() {
    coEvery { mediaRepository.clearLocalMedia() } answers { calls += "clearMedia" }
    every { memoryCache.clear() } answers { calls += "clearMemory" }
    coEvery { authRepository.signOut() } answers
        {
          calls += if (picked.exists()) "signOut while picked remains" else "signOut"
          Result.success(Unit)
        }
    val cacheDir = tempFolder.newFolder("cache")
    picked = File(cacheDir, "picked_1.jpg").apply { writeText("photo") }
    otherFile = File(cacheDir, "other.txt").apply { writeText("kept") }
    val imageLoader =
        mockk<ImageLoader> {
          every { memoryCache } returns this@DeviceSessionRepositoryTest.memoryCache
        }
    session =
        DeviceSessionRepository(
            DeviceLocalPhone(
                mediaRepository,
                MediaImageMemory(imageLoader),
                cacheDir,
                Dispatchers.Unconfined,
            ),
            authRepository,
        )
  }

  @Test
  fun signOutClearsThePhoneThenEndsTheSession() = runBlocking {
    val result = session.signOut()

    assertTrue(result.isSuccess)
    assertFalse(picked.exists())
    assertTrue(otherFile.exists())
    assertEquals(listOf("clearMedia", "clearMemory", "signOut"), calls)
  }

  @Test
  fun aFailedClearReturnsThatFailureAndDoesNotSignOut() = runBlocking {
    val mediaFailure = IOException("Media left")
    coEvery { mediaRepository.clearLocalMedia() } throws mediaFailure

    val result = session.signOut()

    assertSame(mediaFailure, result.exceptionOrNull())
    // later clearing steps still ran; the session stays open so they can be tried again
    assertFalse(picked.exists())
    assertTrue(otherFile.exists())
    assertEquals(listOf("clearMemory"), calls)
    coVerify(exactly = 0) { authRepository.signOut() }
  }

  @Test
  fun aFailedAuthSignOutIsReturnedAfterThePhoneWasCleared() = runBlocking {
    val failure = IllegalStateException("Still signed in")
    coEvery { authRepository.signOut() } answers
        {
          calls += if (picked.exists()) "signOut while picked remains" else "signOut"
          Result.failure(failure)
        }

    val result = session.signOut()

    assertSame(failure, result.exceptionOrNull())
    assertFalse(picked.exists())
    assertEquals(listOf("clearMedia", "clearMemory", "signOut"), calls)
  }
}
