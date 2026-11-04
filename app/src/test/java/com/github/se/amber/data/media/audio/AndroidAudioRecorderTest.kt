// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import android.Manifest
import android.app.Application
import android.media.MediaRecorder
import android.util.Log
import com.github.se.amber.data.media.MAX_FILE_BYTES
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog

/**
 * Checks [AndroidAudioRecorder] with a mocked [MediaRecorder], since Robolectric's shadow writes no
 * file, has no amplitude and never reaches a limit. The mock writes the output file when it starts,
 * like the real one, and the clock is set by the tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidAudioRecorderTest {

  private val context: Application = RuntimeEnvironment.getApplication()
  private val recordedDir = File(context.cacheDir, "audio/recorded")

  private val testScope = TestScope()
  private var now = 10.seconds

  /** The level every mocked recorder reports, out of 32767. */
  private var level = 0

  /** How many bytes the mocked recorders write when they start. */
  private var bytesRecorded = 100

  /** Whether the mocked recorders' stop() fails, as when nothing was recorded. */
  private var stopFails = false

  private val mediaRecorders = mutableListOf<MediaRecorder>()
  private var infoListener: MediaRecorder.OnInfoListener? = null

  private val recorder =
      AndroidAudioRecorder(
          context,
          StandardTestDispatcher(testScope.testScheduler),
          ::newMockRecorder,
      ) {
        now.inWholeMilliseconds
      }

  private fun newMockRecorder(): MediaRecorder {
    val mock = mockk<MediaRecorder>(relaxed = true)
    var path: String? = null
    every { mock.setOutputFile(any<String>()) } answers { path = firstArg() }
    every { mock.setOnInfoListener(any()) } answers { infoListener = firstArg() }
    every { mock.start() } answers { File(path!!).writeBytes(ByteArray(bytesRecorded)) }
    every { mock.stop() } answers { if (stopFails) throw RuntimeException("stop failed") }
    every { mock.maxAmplitude } answers { level }
    mediaRecorders += mock
    return mock
  }

  /**
   * Runs a test, then cancels any recording left, before `runTest` moves the clock on: an amplitude
   * job left running would never let it finish.
   */
  private fun test(body: suspend TestScope.() -> Unit) = testScope.runTest {
    try {
      body()
    } finally {
      recorder.cancel()
    }
  }

  @Before
  fun grantMicrophone() {
    shadowOf(context).grantPermissions(Manifest.permission.RECORD_AUDIO)
  }

  @Test
  fun startThenStopReturnsTheRecordingWithItsDuration() = test {
    recorder.start()
    assertTrue(recorder.isRecording.value)
    now += 3.seconds

    val audio = recorder.stop()

    assertEquals(recordedDir, audio.file.parentFile)
    assertTrue(audio.file.name.endsWith(".m4a"))
    assertEquals(100L, audio.file.length())
    assertEquals(3_000L, audio.durationMs)
    assertFalse(recorder.isRecording.value)
    verify { mediaRecorders.single().release() }
  }

  @Test
  fun recordsWithTheFormatAndLimitsOfTheApp() = test {
    recorder.start()

    val mock = mediaRecorders.single()
    verify {
      mock.setAudioSource(MediaRecorder.AudioSource.MIC)
      mock.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
      mock.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
      mock.setAudioChannels(1)
      mock.setAudioSamplingRate(44_100)
      mock.setAudioEncodingBitRate(64_000)
      mock.setMaxDuration(MAX_RECORDING_MS.toInt())
      mock.setMaxFileSize(match { it in 1 until MAX_FILE_BYTES })
    }
  }

  @Test
  fun startingTwiceFails() = test {
    recorder.start()

    assertFails<IllegalStateException> { recorder.start() }
    assertEquals(1, mediaRecorders.size)
  }

  @Test
  fun stoppingWithoutARecordingFails() = test {
    assertFails<IllegalStateException> { recorder.stop() }
  }

  @Test
  fun startingWithoutThePermissionFails() = test {
    shadowOf(context).denyPermissions(Manifest.permission.RECORD_AUDIO)

    assertFails<SecurityException> { recorder.start() }
    assertFalse(recorder.isRecording.value)
  }

  @Test
  fun aMicrophoneThatCantBeOpenedFailsWithNoFileLeft() = test {
    val busy = mockk<MediaRecorder>(relaxed = true)
    every { busy.start() } throws IllegalStateException("microphone in use")
    val failing =
        AndroidAudioRecorder(context, StandardTestDispatcher(testScheduler), { busy }) {
          now.inWholeMilliseconds
        }

    assertFails<IOException> { failing.start() }
    assertFalse(failing.isRecording.value)
    assertTrue(recordedDir.listFiles().isNullOrEmpty())
    verify { busy.release() }
  }

  @Test
  fun cancelDeletesTheRecording() = test {
    recorder.start()
    val file = recordedDir.listFiles()!!.single()

    recorder.cancel()

    assertFalse(file.exists())
    assertFalse(recorder.isRecording.value)
    assertFails<IllegalStateException> { recorder.stop() }
  }

  @Test
  fun cancelWithoutARecordingDoesNothing() = test {
    recorder.cancel()

    assertFalse(recorder.isRecording.value)
    assertTrue(mediaRecorders.isEmpty())
  }

  @Test
  fun aLimitEndsTheRecordingAndStopStillReturnsIt() = test {
    recorder.start()
    now += 1.seconds
    stopFails = true // the recorder stopped by itself

    infoListener!!.onInfo(
        mediaRecorders.single(),
        MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED,
        0,
    )
    now += 5.seconds

    assertFalse(recorder.isRecording.value)
    assertFails<IllegalStateException> { recorder.start() }
    val audio = recorder.stop()
    assertTrue(audio.file.exists())
    // the time spent before the screen took it isn't part of the recording
    assertEquals(1_000L, audio.durationMs)
  }

  @Test
  fun theFileSizeLimitAlsoEndsTheRecording() = test {
    recorder.start()

    infoListener!!.onInfo(
        mediaRecorders.single(),
        MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED,
        0,
    )

    assertFalse(recorder.isRecording.value)
    assertTrue(recorder.stop().file.exists())
  }

  @Test
  fun anInfoThatIsntALimitDoesntEndTheRecording() = test {
    recorder.start()

    // sent by Android before the size limit is reached
    infoListener!!.onInfo(
        mediaRecorders.single(),
        MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_APPROACHING,
        0,
    )

    assertTrue(recorder.isRecording.value)
  }

  @Test
  fun aLimitSetsTheLevelBackToZero() = test {
    recorder.start()
    level = 16_384
    advanceTimeBy(150.milliseconds)
    runCurrent()

    infoListener!!.onInfo(
        mediaRecorders.single(),
        MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED,
        0,
    )

    assertEquals(0f, recorder.amplitude.value)
  }

  @Test
  fun aSecondLimitKeepsTheTimeOfTheFirst() = test {
    recorder.start()
    now += 1.seconds
    stopFails = true
    infoListener!!.onInfo(
        mediaRecorders.single(),
        MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED,
        0,
    )
    now += 2.seconds

    infoListener!!.onInfo(
        mediaRecorders.single(),
        MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED,
        0,
    )

    assertEquals(1_000L, recorder.stop().durationMs)
  }

  @Test
  fun aFailingStopDeletesTheFile() = test {
    recorder.start()
    val file = recordedDir.listFiles()!!.single()
    stopFails = true

    assertFails<IllegalStateException> { recorder.stop() }
    assertFalse(file.exists())
    // another recording can start
    stopFails = false
    recorder.start()
    assertTrue(recorder.isRecording.value)
  }

  @Test
  fun amplitudeFollowsTheMicrophoneAndGoesBackToZero() = test {
    recorder.start()
    level = 32_767
    advanceTimeBy(150.milliseconds)
    runCurrent()
    assertEquals(1f, recorder.amplitude.value, 0.001f)

    level = 16_384
    advanceTimeBy(100.milliseconds)
    runCurrent()
    assertEquals(0.5f, recorder.amplitude.value, 0.001f)

    recorder.stop()
    assertEquals(0f, recorder.amplitude.value)
  }

  @Test
  fun theLoudestNegativeSampleGivesALevelOf1() = test {
    recorder.start()
    level = 32_768
    advanceTimeBy(150.milliseconds)
    runCurrent()

    assertEquals(1f, recorder.amplitude.value)
  }

  @Test
  fun theWaveformComesFromTheLevelsRead() = test {
    recorder.start()
    level = 32_767
    advanceTimeBy(1_050.milliseconds)
    runCurrent()

    val waveform = recorder.stop().waveform

    assertEquals(WAVEFORM_BARS, waveform.size)
    assertTrue(waveform.all { it == 1f })
  }

  @Test
  fun leftoversAreDeletedOnFirstUseButPickedFilesAreKept() = test {
    recordedDir.mkdirs()
    val leftover = File(recordedDir, "recorded_old.m4a").apply { writeText("old") }
    val picked =
        File(context.cacheDir, "audio/picked/picked_1.m4a").apply {
          parentFile!!.mkdirs()
          writeText("picked")
        }

    recorder.start()
    val kept = recorder.stop()
    recorder.start()

    assertFalse(leftover.exists())
    assertTrue(picked.exists())
    // only the first start cleans the folder, so a returned recording isn't deleted
    assertTrue(kept.file.exists())
  }

  @Test
  fun aLeftoverThatCantBeDeletedIsLoggedAndDoesntStopTheRecording() = test {
    // a folder that isn't empty can't be deleted like a file
    File(recordedDir, "stuck/inner.m4a").apply {
      parentFile!!.mkdirs()
      writeText("old")
    }

    recorder.start()

    assertTrue(recorder.isRecording.value)
    val warning = ShadowLog.getLogsForTag(AndroidAudioRecorder.TAG).single()
    assertEquals(Log.WARN, warning.type)
    assertTrue(warning.msg.contains("stuck"))
  }

  @Test
  fun waveformAlwaysHas64ValuesBetween0And1() {
    for (count in listOf(0, 1, 10, 63, 64, 65, 3_000)) {
      val samples = List(count) { (it % 7) / 6f }

      val waveform = buildWaveform(samples)

      assertEquals("for $count samples", WAVEFORM_BARS, waveform.size)
      assertTrue("for $count samples", waveform.all { it in 0f..1f })
    }
  }

  @Test
  fun waveformKeepsTheLoudestSampleOfEachPartInOrder() {
    // 128 samples: each bar covers two, and only the second half is loud
    val samples = List(128) { if (it < 64) 0.1f else if (it % 2 == 0) 0.2f else 0.9f }

    val waveform = buildWaveform(samples)

    assertEquals(List(32) { 0.1f } + List(32) { 0.9f }, waveform)
  }

  @Test
  fun waveformRepeatsTheSamplesOfAShortRecording() {
    val waveform = buildWaveform(listOf(0.25f, 0.75f))

    assertEquals(List(32) { 0.25f } + List(32) { 0.75f }, waveform)
  }

  @Test
  fun aRecorderThatCantBeBuiltFailsWithNoFileLeft() = test {
    val failing =
        AndroidAudioRecorder(
            context,
            StandardTestDispatcher(testScheduler),
            { throw IllegalStateException("no recorder") },
        ) {
          now.inWholeMilliseconds
        }

    assertFails<IOException> { failing.start() }
    assertTrue(recordedDir.listFiles().isNullOrEmpty())
  }

  @Test
  fun aLateLimitFromThePreviousRecordingIsIgnored() = test {
    recorder.start()
    val oldListener = infoListener!!
    val oldRecorder = mediaRecorders.single()
    recorder.stop()
    recorder.start()

    oldListener.onInfo(oldRecorder, MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED, 0)

    assertTrue(recorder.isRecording.value)
  }

  @Test
  fun anEmptyRecordingIsDeleted() = test {
    bytesRecorded = 0
    recorder.start()
    val file = recordedDir.listFiles()!!.single()

    assertFails<IllegalStateException> { recorder.stop() }
    assertFalse(file.exists())
  }

  @Test
  fun aStopWhoseCallerIsCancelledDeletesTheRecording() = test {
    val io = QueuedDispatcher()
    val queued = AndroidAudioRecorder(context, io, ::newMockRecorder) { now.inWholeMilliseconds }
    launch { queued.start() }
    runCurrent()
    io.runAll()
    runCurrent()
    val file = recordedDir.listFiles()!!.single()

    val stopping = launch { queued.stop() }
    runCurrent()
    // the recording is stopped and returned, but the caller leaves before taking it
    io.runAll()
    stopping.cancel()
    runCurrent()

    assertFalse(file.exists())
    assertFalse(queued.isRecording.value)
  }

  @Test
  fun aStartWhoseCallerIsCancelledLeavesNoRecording() = test {
    val io = QueuedDispatcher()
    val queued = AndroidAudioRecorder(context, io, ::newMockRecorder) { now.inWholeMilliseconds }
    val starting = launch { queued.start() }
    runCurrent()
    // the recording starts, but the caller leaves before start() returns
    io.runAll()
    starting.cancel()
    runCurrent()

    assertFalse(queued.isRecording.value)
    assertTrue(recordedDir.listFiles().isNullOrEmpty())
  }

  @Test
  fun aStartWhoseCallerIsCancelledLeavesANewerRecordingAlone() = test {
    val io = QueuedDispatcher()
    val queued = AndroidAudioRecorder(context, io, ::newMockRecorder) { now.inWholeMilliseconds }
    val starting = launch { queued.start() }
    runCurrent()
    io.runAll()
    starting.cancel()
    // before that caller resumes, its recording is cancelled and another one starts
    queued.cancel()
    CoroutineScope(io).launch { queued.start() }
    io.runAll()
    runCurrent()

    assertTrue(queued.isRecording.value)
    queued.cancel()
  }

  @Test
  fun cancelDeletesTheFileEvenIfTheRecorderFailsToStop() = test {
    recorder.start()
    val file = recordedDir.listFiles()!!.single()
    stopFails = true

    recorder.cancel()

    assertFalse(file.exists())
    verify { mediaRecorders.single().release() }
  }

  @Test
  fun anIOExceptionWhileOpeningTheMicrophoneIsKeptAsItIs() = test {
    val error = IOException("microphone busy")
    val busy = mockk<MediaRecorder>(relaxed = true)
    every { busy.prepare() } throws error
    val failing =
        AndroidAudioRecorder(context, StandardTestDispatcher(testScheduler), { busy }) {
          now.inWholeMilliseconds
        }

    val thrown = assertFails<IOException> { failing.start() }

    // not wrapped into "Couldn't open the microphone"; coroutines may copy it, so not the same
    // instance
    assertEquals(error.message, thrown.message)
    assertTrue(recordedDir.listFiles().isNullOrEmpty())
  }

  @Test
  fun aSecurityExceptionWhileOpeningTheMicrophoneIsKeptAsItIs() = test {
    val error = SecurityException("microphone blocked")
    val blocked = mockk<MediaRecorder>(relaxed = true)
    every { blocked.start() } throws error
    val failing =
        AndroidAudioRecorder(context, StandardTestDispatcher(testScheduler), { blocked }) {
          now.inWholeMilliseconds
        }

    val thrown = assertFails<SecurityException> { failing.start() }

    // not wrapped into "Couldn't open the microphone"; coroutines may copy it, so not the same
    // instance
    assertEquals(error.message, thrown.message)
    verify { blocked.release() }
  }

  @Test
  fun aFolderThatCantBeCreatedFailsAndReleasesTheRecorder() = test {
    // a file where the folder should be, so no recording can be created in it
    recordedDir.parentFile!!.mkdirs()
    recordedDir.writeText("not a folder")

    assertFails<IOException> { recorder.start() }

    assertFalse(recorder.isRecording.value)
    verify { mediaRecorders.single().release() }
  }

  @Test
  fun theDefaultRecorderUsesAndroidsMediaRecorder() = test {
    val default = AndroidAudioRecorder(context, StandardTestDispatcher(testScheduler))

    default.start()
    assertTrue(default.isRecording.value)
    default.cancel()

    assertFalse(default.isRecording.value)
    assertTrue(recordedDir.listFiles().isNullOrEmpty())
  }
}

/**
 * Like `assertThrows`, for a block that suspends, so it runs on the test's dispatcher.
 *
 * @return the exception thrown.
 */
private suspend inline fun <reified T : Throwable> assertFails(block: suspend () -> Unit): T {
  try {
    block()
  } catch (e: Throwable) {
    if (e is T) return e
    throw e
  }
  fail("Expected ${T::class.simpleName}")
  throw AssertionError()
}
