// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.media.audio

import java.io.File
import kotlinx.coroutines.flow.StateFlow

/**
 * Plays audio files, one at a time for the whole app.
 *
 * The app has a single player, since the phone has a single speaker. Playing an audio stops any
 * other one, even if another screen started it. To know whether [state] is about its own audio, a
 * screen compares [AudioState.file] with it.
 *
 * ViewModels receive the player as a constructor parameter, so their tests can use a fake instead.
 * It will be provided by `AudioPlayerProvider`.
 */
interface AudioPlayer {

  /**
   * What is loaded and how far it has played. It changes after every call below, and about every
   * 200 ms while playing.
   *
   * At the end of the file, playback stops and goes back to position 0, but the file and the
   * duration are kept, so the screen can show a "play again" button. If another audio is played
   * instead, [AudioState.file] changes to it.
   *
   * If the file fails while playing, it is unloaded, which empties the state as [stop] does. A file
   * that can't be read at all fails earlier, in [play].
   */
  val state: StateFlow<AudioState>

  /**
   * Plays a file from the start. Whatever was loaded before is stopped, even if another screen
   * started it.
   *
   * @param file the .m4a file to play.
   * @throws java.io.IOException if the file can't be read or decoded. Nothing is loaded then.
   */
  suspend fun play(file: File)

  /** Pauses playback. It does nothing if nothing is playing. */
  fun pause()

  /**
   * Plays on from the current position. It does nothing if nothing is loaded or already playing.
   */
  fun resume()

  /**
   * Moves to another position in the loaded file. It does nothing if nothing is loaded.
   *
   * @param positionMs the position to move to, in milliseconds. It is kept between 0 and the
   *   duration.
   */
  fun seekTo(positionMs: Long)

  /**
   * Stops and unloads a file, which empties [state]. It does nothing if another file is loaded, so
   * a screen can call it in `onCleared()` without cutting an audio that another screen has started
   * in the meantime.
   *
   * @param file the screen's audio file.
   */
  fun stop(file: File)
}

/**
 * The playback state of an [AudioPlayer]. When nothing is loaded, [file] is null and the other
 * values are false or 0.
 *
 * @property file the file that is loaded, or null if there is none.
 * @property isPlaying whether the file is playing right now (false when paused or finished).
 * @property positionMs how far it has played, in milliseconds.
 * @property durationMs how long the file lasts, in milliseconds.
 * @throws IllegalArgumentException if there is no [file] but it is playing or has a position or a
 *   duration.
 */
data class AudioState(
    val file: File? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
) {
  init {
    require(file != null || (!isPlaying && positionMs == 0L && durationMs == 0L)) {
      "Nothing is loaded, so it can't be playing or have a position or a duration"
    }
  }
}
