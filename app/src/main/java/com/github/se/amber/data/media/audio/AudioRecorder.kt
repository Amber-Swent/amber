// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import com.github.se.amber.data.media.MAX_FILE_BYTES
import kotlinx.coroutines.flow.StateFlow

/**
 * Records audio from the microphone, one recording at a time.
 *
 * ViewModels only see this interface, so their tests can use a fake instead. They receive it as a
 * constructor parameter. The app has a single recorder, since the phone has a single microphone,
 * and it will be provided by `AudioRecorderProvider`.
 */
interface AudioRecorder {

  /**
   * Whether a recording is in progress. It becomes true when [start] is called, and false when
   * [stop] or [cancel] is called.
   *
   * It also becomes false when the recording stops by itself because it reached [MAX_RECORDING_MS]
   * or got close to [MAX_FILE_BYTES]. The screen can tell this happened because it didn't call
   * [stop] or [cancel] itself, and it should then call [stop] to get the recording.
   */
  val isRecording: StateFlow<Boolean>

  /**
   * How loud the microphone is right now, from 0 (silence) to 1 (the loudest it can record), so the
   * screen can draw a waveform or a level meter. It is updated about every 100 ms while recording,
   * and goes back to 0 when the recording stops.
   *
   * It is only for drawing while recording: the finished recording's waveform, to draw when
   * listening to it, comes with it in [LocalAudio.waveform].
   */
  val amplitude: StateFlow<Float>

  /**
   * Starts recording from the microphone into a new .m4a file in the app's cache.
   *
   * @throws IllegalStateException if a recording is already in progress, or if a recording that
   *   stopped at a limit hasn't been taken with [stop] or dropped with [cancel] yet.
   * @throws SecurityException if RECORD_AUDIO (the microphone use permission) isn't granted.
   * @throws java.io.IOException if the microphone can't be opened (e.g. used by another app).
   */
  suspend fun start()

  /**
   * Stops the recording and returns it. If the recording already stopped by itself at a limit, it
   * just returns it.
   *
   * @return the recording, with its waveform built from the [amplitude] read while recording. Its
   *   file now belongs to the caller.
   * @throws IllegalStateException if there is no recording to return (none was started, or it was
   *   already returned or cancelled), if it is empty (the file exists but contains nothing), or if
   *   it is too short to be kept. In the last two cases, the file is deleted.
   */
  suspend fun stop(): LocalAudio

  /**
   * Stops the recording, if there is one, and deletes its file. It does nothing otherwise, so it is
   * safe to call in `onCleared()`.
   */
  fun cancel()
}

/** The longest a recording can last. The recorder stops by itself when it reaches it. */
const val MAX_RECORDING_MS = 5 * 60 * 1000L
