// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * An [AudioRecorder] for the ViewModel tests, with no microphone. It follows the same rules as the
 * real recorder: calling [start] twice, or [stop] without a recording, throws. [stop] returns a
 * real .m4a file in [directory] that lasts [durationMs].
 *
 * It also has helpers that a real recorder doesn't, to make ViewModel tests easier, like a mock
 * would: [startError] and [stopError] make [start] and [stop] throw (e.g. a `SecurityException` for
 * a missing permission, and like the real recorder a failing [stop] deletes the file), [amplitude]
 * can be set to any level, [reachLimit] stops the recording the way a limit would, and the counters
 * tell how many times each function was called.
 */
class FakeAudioRecorder(private val directory: File, var durationMs: Long = 3_000L) :
    AudioRecorder {

  override val isRecording = MutableStateFlow(false)

  /** Set it to test a waveform. It goes back to 0 when the recording stops, like the real one. */
  override val amplitude = MutableStateFlow(0f)

  /** Thrown by every [start] while it is set, before anything is recorded. */
  var startError: Exception? = null

  /** Thrown by every [stop] while it is set, after the recording's file is deleted. */
  var stopError: Exception? = null

  /** How many times [start] was called, e.g. to check that a ViewModel didn't start twice. */
  var startCalls = 0
    private set

  /** How many times [stop] was called. */
  var stopCalls = 0
    private set

  /**
   * How many times [cancel] was called, e.g. to check that a ViewModel cancels in `onCleared()`.
   */
  var cancelCalls = 0
    private set

  /**
   * The file of the recording in progress, or of the one that stopped at a limit. It is null when
   * there is no recording.
   */
  var file: File? = null
    private set

  override suspend fun start() {
    startCalls++
    startError?.let { throw it }
    check(file == null) { "Already recording" }
    file = File.createTempFile("recorded_", ".m4a", directory)
    isRecording.value = true
  }

  override suspend fun stop(): LocalAudio {
    stopCalls++
    val recorded = checkNotNull(file) { "Not recording" }
    file = null
    isRecording.value = false
    amplitude.value = 0f
    stopError?.let {
      recorded.delete()
      throw it
    }
    recorded.writeText("fake audio")
    return LocalAudio(recorded, durationMs)
  }

  override fun cancel() {
    cancelCalls++
    file?.delete()
    file = null
    isRecording.value = false
    amplitude.value = 0f
  }

  /**
   * Stops the recording the way a limit would: [isRecording] becomes false, and [stop] still
   * returns the recording.
   */
  fun reachLimit() {
    check(file != null) { "Not recording" }
    isRecording.value = false
    amplitude.value = 0f
  }
}
