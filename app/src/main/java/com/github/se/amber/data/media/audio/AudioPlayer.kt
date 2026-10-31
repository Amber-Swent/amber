// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import java.io.File
import kotlinx.coroutines.flow.StateFlow

/**
 * Plays voice memos, one file at a time.
 *
 * It holds one screen's playback, so it isn't shared: each ViewModel creates its own (no provider),
 * so one screen's [release] can't cut another screen's sound. Tests pass a fake.
 */
interface AudioPlayer {

  /**
   * What is playing. It changes on every call below, about every 200 ms while playing, and at the
   * end of the file, where it becomes `isPlaying = false` and `positionMs = 0` with the duration
   * kept, so the screen can show a "play again" state.
   */
  val state: StateFlow<AudioState>

  /**
   * Plays [file] from the start, replacing whatever was loaded.
   *
   * @throws java.io.IOException if it can't be read or decoded.
   */
  suspend fun play(file: File)

  /** Pauses playback. No effect if not playing. */
  fun pause()

  /** Plays on from the current position. No effect if nothing is loaded or already playing. */
  fun resume()

  /** Moves to [positionMs], clamped to `[0, durationMs]`. No effect if nothing is loaded. */
  fun seekTo(positionMs: Long)

  /** Stops and frees the player; [play] may be called again afterwards. Call in `onCleared()`. */
  fun release()
}

/** Playback state of an [AudioPlayer]; all zero or false when nothing is loaded. */
data class AudioState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)
