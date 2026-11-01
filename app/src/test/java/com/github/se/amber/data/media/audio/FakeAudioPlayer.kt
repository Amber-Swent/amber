// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * An [AudioPlayer] for the ViewModel tests, with no speaker. Every file it plays lasts [durationMs]
 * and is recorded in [played]. Time doesn't pass: the position only moves with [seekTo], and
 * [finish] ends playback the way reaching the end of the file would.
 *
 * To test the error cases, set [playError] (e.g. an `IOException` for a file that can't be
 * decoded). To test that one audio stops another, give the same player to two ViewModels.
 */
class FakeAudioPlayer(var durationMs: Long = 5_000L) : AudioPlayer {

  override val state = MutableStateFlow(AudioState())

  /** Thrown by every [play] while it is set, after what was loaded is stopped. */
  var playError: Exception? = null

  /** The files played so far, in order. */
  val played: MutableList<File> = mutableListOf()

  override suspend fun play(file: File) {
    state.value = AudioState()
    playError?.let { throw it }
    played += file
    state.value = AudioState(file, isPlaying = true, positionMs = 0L, durationMs = durationMs)
  }

  override fun pause() {
    state.value = state.value.copy(isPlaying = false)
  }

  override fun resume() {
    if (state.value.file != null) state.value = state.value.copy(isPlaying = true)
  }

  override fun seekTo(positionMs: Long) {
    if (state.value.file == null) return
    state.value = state.value.copy(positionMs = positionMs.coerceIn(0L, state.value.durationMs))
  }

  override fun stop(file: File) {
    if (state.value.file == file) state.value = AudioState()
  }

  /**
   * Ends playback the way reaching the end of the file does: it stops and goes back to 0, keeping
   * the file and the duration.
   */
  fun finish() {
    check(state.value.file != null) { "Nothing loaded" }
    state.value = state.value.copy(isPlaying = false, positionMs = 0L)
  }
}
