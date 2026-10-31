// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import com.github.se.amber.data.media.MAX_FILE_BYTES
import kotlinx.coroutines.flow.StateFlow

/**
 * Records voice memos from the microphone, one at a time.
 *
 * ViewModels see only this interface, so tests can pass a fake. They receive it as a constructor
 * parameter. The app has a single instance, since the device has a single microphone; it will come
 * from `AudioRecorderProvider`.
 */
interface AudioRecorder {

  /**
   * Whether a recording is under way. It turns true on [start], and false on [stop], on [cancel],
   * or when the recording ends by itself at [MAX_RECORDING_MS] or near [MAX_FILE_BYTES]; the screen
   * then calls [stop] to get it.
   */
  val isRecording: StateFlow<Boolean>

  /**
   * Starts recording from the microphone into a new .m4a file in the app's cache.
   *
   * @throws IllegalStateException if a recording is under way, or ended at a limit and hasn't been
   *   taken with [stop] or dropped with [cancel] yet.
   * @throws SecurityException if RECORD_AUDIO isn't granted (the screen asks for it, not this).
   * @throws java.io.IOException if the microphone can't be opened (e.g. used by another app).
   */
  suspend fun start()

  /**
   * Ends the recording, or takes the one that ended by itself at a limit, and returns it.
   *
   * @throws IllegalStateException if there is no recording to return, or if it is empty or too
   *   short to be kept (its file is then deleted).
   */
  suspend fun stop(): LocalAudio

  /**
   * Ends the recording, if any, and deletes its file. No effect otherwise; safe in `onCleared()`.
   */
  fun cancel()
}

/** Longest recording; the recorder stops by itself when it is reached. */
const val MAX_RECORDING_MS = 5 * 60 * 1000L
