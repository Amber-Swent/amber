// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Checks that [FakeAudioPlayer] follows the [AudioPlayer] contract, which the ViewModel tests using
 * it rely on.
 */
class FakeAudioPlayerTest {

  private val player = FakeAudioPlayer(durationMs = 5_000L)
  private val file = File("memo.m4a")

  @Test
  fun playLoadsTheFileAndPlaysFromTheStart() = runTest {
    player.play(file)

    assertEquals(
        AudioState(isPlaying = true, positionMs = 0L, durationMs = 5_000L),
        player.state.value,
    )
    assertEquals(listOf(file), player.played)
  }

  @Test
  fun pauseAndResumeToggleIsPlaying() = runTest {
    player.resume()
    assertEquals(AudioState(), player.state.value)

    player.play(file)
    player.pause()
    assertEquals(false, player.state.value.isPlaying)

    player.resume()
    assertEquals(true, player.state.value.isPlaying)
  }

  @Test
  fun seekToIsClampedAndIgnoredWhenNothingIsLoaded() = runTest {
    player.seekTo(1_000L)
    assertEquals(AudioState(), player.state.value)

    player.play(file)
    player.seekTo(9_000L)
    assertEquals(5_000L, player.state.value.positionMs)

    player.seekTo(-1L)
    assertEquals(0L, player.state.value.positionMs)
  }

  @Test
  fun finishStopsAtTheStartAndKeepsTheDuration() = runTest {
    player.play(file)
    player.seekTo(3_000L)

    player.finish()

    assertEquals(
        AudioState(isPlaying = false, positionMs = 0L, durationMs = 5_000L),
        player.state.value,
    )
  }

  @Test
  fun releaseResetsTheStateAndAllowsAnotherPlay() = runTest {
    player.play(file)

    player.release()
    assertEquals(AudioState(), player.state.value)
    player.resume()
    assertEquals(AudioState(), player.state.value)

    player.play(file)
    assertEquals(true, player.state.value.isPlaying)
  }

  @Test
  fun playThrowsTheErrorItIsGiven() = runTest {
    val undecodable = IOException("can't decode")
    player.playError = undecodable

    assertSame(undecodable, runCatching { player.play(file) }.exceptionOrNull())
    assertEquals(AudioState(), player.state.value)
  }
}
