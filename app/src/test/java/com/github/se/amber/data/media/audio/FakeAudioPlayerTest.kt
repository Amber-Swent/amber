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
  private val other = File("other.m4a")

  @Test
  fun playLoadsTheFileAndPlaysFromTheStart() = runTest {
    player.play(file)

    assertEquals(AudioState(file, isPlaying = true, durationMs = 5_000L), player.state.value)
    assertEquals(listOf(file), player.played)
  }

  @Test
  fun playingAnotherFileReplacesTheFirst() = runTest {
    player.play(file)
    player.seekTo(2_000L)

    player.play(other)

    assertEquals(AudioState(other, isPlaying = true, durationMs = 5_000L), player.state.value)
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
  fun finishStopsAtTheStartAndKeepsTheFileAndDuration() = runTest {
    player.play(file)
    player.seekTo(3_000L)

    player.finish()

    assertEquals(AudioState(file, isPlaying = false, durationMs = 5_000L), player.state.value)
  }

  @Test
  fun stopUnloadsOnlyTheFileItIsGiven() = runTest {
    player.play(file)

    player.stop(other)
    assertEquals(file, player.state.value.file)

    player.stop(file)
    assertEquals(AudioState(), player.state.value)
    player.resume()
    assertEquals(AudioState(), player.state.value)

    player.play(file)
    assertEquals(true, player.state.value.isPlaying)
  }

  @Test
  fun playThrowsTheErrorItIsGivenAndLeavesNothingLoaded() = runTest {
    player.play(file)
    val undecodable = IOException("can't decode")
    player.playError = undecodable

    assertSame(undecodable, runCatching { player.play(other) }.exceptionOrNull())
    assertEquals(AudioState(), player.state.value)
  }
}
