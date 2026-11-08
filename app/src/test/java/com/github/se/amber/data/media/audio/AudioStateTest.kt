// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.media.audio

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Checks that an [AudioState] can't describe playback without a file. */
class AudioStateTest {

  @Test
  fun aStateWithoutAFileIsEmpty() {
    assertEquals(
        AudioState(null, isPlaying = false, positionMs = 0L, durationMs = 0L),
        AudioState(),
    )

    assertThrows(IllegalArgumentException::class.java) { AudioState(isPlaying = true) }
    assertThrows(IllegalArgumentException::class.java) { AudioState(positionMs = 1L) }
    assertThrows(IllegalArgumentException::class.java) { AudioState(durationMs = 1L) }
  }

  @Test
  fun aStateWithAFileCanBePlayingAnywhere() {
    val state =
        AudioState(File("audio.m4a"), isPlaying = true, positionMs = 2_000L, durationMs = 5_000L)

    assertEquals(2_000L, state.positionMs)
  }
}
