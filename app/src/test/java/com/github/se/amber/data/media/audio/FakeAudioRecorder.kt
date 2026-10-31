// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * An [AudioRecorder] without a microphone, for the ViewModel tests. It follows the contract: a
 * second [start] or a [stop] without a recording throws, and [stop] returns a real .m4a file in
 * [directory] that lasts [durationMs]. [start] and [stop] throw [startError] and [stopError] while
 * they are set (e.g. a `SecurityException` for a missing permission); a failing [stop] deletes the
 * file, like the real one. [reachLimit] ends the recording as the time or size limit would.
 */
class FakeAudioRecorder(private val directory: File, var durationMs: Long = 3_000L) :
    AudioRecorder {

  override val isRecording = MutableStateFlow(false)

  /** Thrown by every [start] while set, before anything is recorded. */
  var startError: Exception? = null

  /** Thrown by every [stop] while set, after the recording's file is deleted. */
  var stopError: Exception? = null

  var startCalls = 0
    private set

  var stopCalls = 0
    private set

  var cancelCalls = 0
    private set

  /** The file of the recording under way, or of the one that ended at a limit; null otherwise. */
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
  }

  /**
   * Ends the recording under way as a limit would: [isRecording] turns false, [stop] returns it.
   */
  fun reachLimit() {
    check(file != null) { "Not recording" }
    isRecording.value = false
  }
}
