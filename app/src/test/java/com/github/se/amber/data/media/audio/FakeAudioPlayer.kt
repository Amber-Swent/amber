// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * An [AudioPlayer] without a speaker, for the ViewModel tests. Every file it plays lasts
 * [durationMs] and is recorded in [played]; [play] throws [playError] instead while it is set (e.g.
 * an `IOException` for a file that can't be decoded), leaving nothing loaded. The position only
 * moves through [seekTo] and [finish], which ends playback as reaching the end of the file would.
 * Pass the same instance to two ViewModels to test that one memo stops the other.
 */
class FakeAudioPlayer(var durationMs: Long = 5_000L) : AudioPlayer {

  override val state = MutableStateFlow(AudioState())

  /** Thrown by every [play] while set, after what was loaded is stopped. */
  var playError: Exception? = null

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
   * Ends playback as reaching the end of the file does: stopped, back at 0, file and duration kept.
   */
  fun finish() {
    check(state.value.file != null) { "Nothing loaded" }
    state.value = state.value.copy(isPlaying = false, positionMs = 0L)
  }
}
