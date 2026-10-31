// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import java.io.File
import kotlinx.coroutines.flow.StateFlow

/**
 * Plays voice memos, one at a time for the whole app.
 *
 * The app has a single player, as the device has a single speaker: starting a memo stops any other,
 * whichever screen played it. Each screen reads [state] for its own memo by comparing
 * [AudioState.file] with it. ViewModels receive the player as a constructor parameter, so tests can
 * pass a fake; the app's instance will come from `AudioPlayerProvider`.
 */
interface AudioPlayer {

  /**
   * What is loaded and how far it has played. It changes on every call below, and about every 200
   * ms while playing. At the end of the file it stops back at position 0, keeping the file and the
   * duration, so the screen can show a "play again" state.
   */
  val state: StateFlow<AudioState>

  /**
   * Plays [file] from the start, stopping whatever was loaded, on this screen or another.
   *
   * @throws java.io.IOException if it can't be read or decoded; nothing is loaded then.
   */
  suspend fun play(file: File)

  /** Pauses what is loaded. No effect if not playing. */
  fun pause()

  /** Plays on from the current position. No effect if nothing is loaded or already playing. */
  fun resume()

  /** Moves to [positionMs], clamped to `[0, durationMs]`. No effect if nothing is loaded. */
  fun seekTo(positionMs: Long)

  /**
   * Stops and unloads [file] if it is the one loaded, which empties [state]; no effect otherwise.
   * Call it in `onCleared()` with the screen's memo: leaving a screen stops its sound, but not a
   * memo another screen has started since.
   */
  fun stop(file: File)
}

/** Playback state of an [AudioPlayer]: the [file] loaded, if any; all empty when there is none. */
data class AudioState(
    val file: File? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)
