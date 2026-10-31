// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * An [AudioPlayer] without a speaker, for the ViewModel tests. Every file it plays lasts
 * [durationMs] and is recorded in [played]; [play] throws [playError] instead while it is set (e.g.
 * an `IOException` for a file that can't be decoded). The position only moves through [seekTo] and
 * [finish], which ends playback as reaching the end of the file would.
 */
class FakeAudioPlayer(var durationMs: Long = 5_000L) : AudioPlayer {

  override val state = MutableStateFlow(AudioState())

  /** Thrown by every [play] while set; what was loaded stays loaded. */
  var playError: Exception? = null

  val played: MutableList<File> = mutableListOf()

  private var loaded: File? = null

  override suspend fun play(file: File) {
    playError?.let { throw it }
    played += file
    loaded = file
    state.value = AudioState(isPlaying = true, positionMs = 0L, durationMs = durationMs)
  }

  override fun pause() {
    state.value = state.value.copy(isPlaying = false)
  }

  override fun resume() {
    if (loaded != null) state.value = state.value.copy(isPlaying = true)
  }

  override fun seekTo(positionMs: Long) {
    if (loaded == null) return
    state.value = state.value.copy(positionMs = positionMs.coerceIn(0L, state.value.durationMs))
  }

  override fun release() {
    loaded = null
    state.value = AudioState()
  }

  /** Ends playback as reaching the end of the file does: stopped, back at 0, duration kept. */
  fun finish() {
    check(loaded != null) { "Nothing loaded" }
    state.value = state.value.copy(isPlaying = false, positionMs = 0L)
  }
}
