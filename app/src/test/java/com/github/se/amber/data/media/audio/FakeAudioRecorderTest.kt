// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Checks that [FakeAudioRecorder] follows the [AudioRecorder] contract, which the ViewModel tests
 * using it rely on.
 */
class FakeAudioRecorderTest {

  @get:Rule val folder = TemporaryFolder()

  private val recorder by lazy { FakeAudioRecorder(folder.root, durationMs = 4_000L) }

  @Test
  fun stopReturnsAnM4aFileInTheDirectoryWithTheDuration() = runTest {
    recorder.start()
    assertTrue(recorder.isRecording.value)

    val recorded = recorder.stop()

    assertFalse(recorder.isRecording.value)
    assertTrue(recorded.file.exists())
    assertTrue(recorded.file.name.endsWith(".m4a"))
    assertEquals(folder.root, recorded.file.parentFile)
    assertEquals(4_000L, recorded.durationMs)
  }

  @Test
  fun startWhileRecordingAndStopWithoutRecordingThrow() = runTest {
    assertTrue(runCatching { recorder.stop() }.exceptionOrNull() is IllegalStateException)

    recorder.start()

    assertTrue(runCatching { recorder.start() }.exceptionOrNull() is IllegalStateException)
    assertTrue(recorder.isRecording.value)
  }

  @Test
  fun cancelDeletesTheFileAndDoesNothingWhenIdle() = runTest {
    recorder.cancel()
    recorder.start()
    val file = recorder.file!!

    recorder.cancel()

    assertFalse(file.exists())
    assertNull(recorder.file)
    assertFalse(recorder.isRecording.value)
    assertEquals(2, recorder.cancelCalls)
  }

  @Test
  fun reachLimitEndsTheRecordingAndStopStillReturnsIt() = runTest {
    recorder.start()
    val file = recorder.file

    recorder.reachLimit()

    assertFalse(recorder.isRecording.value)
    assertTrue(runCatching { recorder.start() }.exceptionOrNull() is IllegalStateException)
    assertEquals(file, recorder.stop().file)
  }

  @Test
  fun throwsTheErrorsItIsGivenAndDeletesTheFileOnStop() = runTest {
    val denied = SecurityException("no permission")
    recorder.startError = denied
    assertSame(denied, runCatching { recorder.start() }.exceptionOrNull())
    assertFalse(recorder.isRecording.value)

    recorder.startError = null
    val tooShort = IllegalStateException("too short")
    recorder.stopError = tooShort
    recorder.start()
    val file = recorder.file!!

    assertSame(tooShort, runCatching { recorder.stop() }.exceptionOrNull())
    assertFalse(file.exists())
    assertFalse(recorder.isRecording.value)
    assertEquals(2, recorder.startCalls)
    assertEquals(1, recorder.stopCalls)
  }
}
