// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import android.app.Application
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Looper
import java.io.File
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

/**
 * Checks [AndroidAudioPlayer] on Robolectric's [ShadowMediaPlayer], which plays the files it is
 * told about with the main looper's clock, and its [android.media.AudioManager] shadow for the
 * audio focus.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidAudioPlayerTest {

  private val context: Application = RuntimeEnvironment.getApplication()
  private val audioManager = context.getSystemService(AudioManager::class.java)

  private val first = File(context.cacheDir, "first.m4a")
  private val second = File(context.cacheDir, "second.m4a")

  private val testScope = TestScope()
  private val player = AndroidAudioPlayer(context, StandardTestDispatcher(testScope.testScheduler))

  @Before
  fun describeTheFiles() {
    ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(first.path), mediaInfo(5.seconds))
    ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(second.path), mediaInfo(8.seconds))
  }

  private fun mediaInfo(duration: Duration) =
      ShadowMediaPlayer.MediaInfo(duration.inWholeMilliseconds.toInt(), 0)

  /**
   * Runs a test, then unloads what is left, before `runTest` moves the clock on: a position job
   * left running would never let it finish.
   */
  private fun test(body: suspend TestScope.() -> Unit) = testScope.runTest {
    try {
      body()
    } finally {
      player.state.value.file?.let(player::stop)
    }
  }

  /** Lets the shadow play on for [duration]. */
  private fun playFor(duration: Duration) =
      shadowOf(Looper.getMainLooper()).idleFor(duration.toJavaDuration())

  @Test
  fun playLoadsTheFileAndPlaysIt() = test {
    player.play(first)

    assertEquals(
        AudioState(first, isPlaying = true, positionMs = 0, durationMs = 5_000),
        player.state.value,
    )
  }

  @Test
  fun thePositionIsUpdatedWhilePlaying() = test {
    player.play(first)

    playFor(1.seconds)
    advanceTimeBy(250.milliseconds)
    runCurrent()

    assertEquals(1_000L, player.state.value.positionMs)
  }

  @Test
  fun playingAnotherFileReplacesTheFirst() = test {
    player.play(first)

    player.play(second)

    assertEquals(second, player.state.value.file)
    assertEquals(8_000L, player.state.value.durationMs)
    assertTrue(player.state.value.isPlaying)
  }

  @Test
  fun pauseAndResumeToggleThePlayback() = test {
    player.play(first)
    playFor(1.seconds)

    player.pause()
    assertFalse(player.state.value.isPlaying)
    assertEquals(1_000L, player.state.value.positionMs)
    // a paused player doesn't move on
    playFor(1.seconds)

    player.resume()
    assertTrue(player.state.value.isPlaying)
    assertEquals(1_000L, player.state.value.positionMs)
    // a resumed one does, from where it was paused
    playFor(1.seconds)
    advanceTimeBy(250.milliseconds)
    runCurrent()
    assertEquals(2_000L, player.state.value.positionMs)
  }

  @Test
  fun pauseResumeAndSeekDoNothingWhenNothingIsLoaded() = test {
    player.pause()
    player.resume()
    player.seekTo(1_000)

    assertEquals(AudioState(), player.state.value)
  }

  @Test
  fun seekToIsKeptWithinTheFile() = test {
    player.play(first)

    player.seekTo(2_000)
    assertEquals(2_000L, player.state.value.positionMs)
    player.seekTo(-50)
    assertEquals(0L, player.state.value.positionMs)
    player.seekTo(60_000)
    assertEquals(5_000L, player.state.value.positionMs)
  }

  @Test
  fun theEndOfTheFileGoesBackToTheStartAndKeepsTheFile() = test {
    player.play(first)
    // the position moves on first, so going back to 0 is seen
    playFor(4.seconds)
    advanceTimeBy(250.milliseconds)
    runCurrent()
    assertEquals(4_000L, player.state.value.positionMs)

    playFor(2.seconds)

    assertEquals(
        AudioState(first, isPlaying = false, positionMs = 0, durationMs = 5_000),
        player.state.value,
    )
    // "play again"
    player.resume()
    assertTrue(player.state.value.isPlaying)
  }

  @Test
  fun aFileThatCantBeReadFailsAndLeavesNothingLoaded() = test {
    player.play(first)
    val unknown = File(context.cacheDir, "not-audio.m4a")

    try {
      player.play(unknown)
      fail("Expected an IOException")
    } catch (_: IOException) {}

    assertEquals(AudioState(), player.state.value)
  }

  @Test
  fun stoppingAnotherFileDoesNothing() = test {
    player.play(second)

    player.stop(first)

    assertEquals(second, player.state.value.file)
    assertTrue(player.state.value.isPlaying)
  }

  @Test
  fun stoppingTheLoadedFileEmptiesTheStateAndAllowsAnotherPlay() = test {
    player.play(first)

    player.stop(first)
    assertEquals(AudioState(), player.state.value)

    player.play(first)
    assertTrue(player.state.value.isPlaying)
  }

  @Test
  fun theFocusIsRequestedOnPlayAndAbandonedOnStop() = test {
    player.play(first)
    val request = shadowOf(audioManager).lastAudioFocusRequest
    assertNotNull(request)
    assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, request.audioFocusRequest.focusGain)
    val attributes = request.audioFocusRequest.audioAttributes
    // played as media, so the media volume controls it
    assertEquals(AudioAttributes.USAGE_MEDIA, attributes.usage)
    // without these two, Android would play us lower by itself, without telling the listener
    assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, attributes.contentType)
    assertTrue(request.audioFocusRequest.willPauseWhenDucked())
    assertNull(shadowOf(audioManager).lastAbandonedAudioFocusRequest)

    player.stop(first)

    assertSame(request.audioFocusRequest, shadowOf(audioManager).lastAbandonedAudioFocusRequest)
  }

  @Test
  fun losingTheFocusForAWhilePauses() = test {
    player.play(first)

    // e.g. a phone call
    player.focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)

    assertFalse(player.state.value.isPlaying)
    assertEquals(first, player.state.value.file)
  }

  @Test
  fun losingTheFocusForGoodPauses() = test {
    player.play(first)

    // e.g. another app starts playing music
    player.focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)

    assertFalse(player.state.value.isPlaying)
    assertEquals(first, player.state.value.file)
  }

  /** A file that is described while it is prepared, by [whilePreparing], instead of up front. */
  private val prepared = File(context.cacheDir, "prepared.m4a")

  /** Runs [action] while [prepared] is being prepared, as if a screen called in at that moment. */
  private fun whilePreparing(action: () -> Unit): () -> Int {
    var calls = 0
    ShadowMediaPlayer.setMediaInfoProvider {
      calls++
      action()
      mediaInfo(5.seconds)
    }
    return { calls }
  }

  @Test
  fun stoppingTheFileWhileItIsPreparedKeepsItFromPlaying() = test {
    val calls = whilePreparing { player.stop(prepared) }

    player.play(prepared)

    assertEquals(1, calls())
    assertEquals(AudioState(), player.state.value)
    assertNull(shadowOf(audioManager).lastAudioFocusRequest)
  }

  @Test
  fun stoppingAnotherFileWhileOneIsPreparedDoesNothing() = test {
    val calls = whilePreparing { player.stop(second) }

    player.play(prepared)

    assertEquals(1, calls())
    assertEquals(prepared, player.state.value.file)
    assertTrue(player.state.value.isPlaying)
  }

  @Test
  fun aNewerPlayWhileOneIsPreparedWins() = test {
    // this player prepares at once, so the newer play() can run inside the first one's preparing
    val eager = AndroidAudioPlayer(context, UnconfinedTestDispatcher(testScheduler))
    whilePreparing { runBlocking { eager.play(second) } }

    eager.play(prepared)

    assertEquals(second, eager.state.value.file)
    assertTrue(eager.state.value.isPlaying)
    eager.stop(second)
  }

  @Test
  fun aPlayWhoseCallerIsCancelledWhileItIsPreparedDoesntPlay() = test {
    lateinit var playing: Job
    // the screen is closed while its file is prepared, before it could call stop()
    whilePreparing { playing.cancel() }

    playing = launch { player.play(prepared) }
    runCurrent()

    assertTrue(playing.isCancelled)
    assertEquals(AudioState(), player.state.value)
    assertNull(shadowOf(audioManager).lastAudioFocusRequest)
  }

  @Test
  fun seekToMovesThePlayback() = test {
    player.play(first)

    player.seekTo(2_000)
    // the shadow finishes seeking on the main looper
    shadowOf(Looper.getMainLooper()).idle()
    advanceTimeBy(250.milliseconds)
    runCurrent()

    // the position read back from the player, not just the one asked for
    assertEquals(2_000L, player.state.value.positionMs)
  }

  @Test
  fun theFocusIsGivenBackOnPause() = test {
    player.play(first)
    val request = shadowOf(audioManager).lastAudioFocusRequest.audioFocusRequest

    player.pause()

    assertSame(request, shadowOf(audioManager).lastAbandonedAudioFocusRequest)
  }

  @Test
  fun theFocusIsGivenBackAtTheEndOfTheFile() = test {
    player.play(first)
    val request = shadowOf(audioManager).lastAudioFocusRequest.audioFocusRequest

    playFor(6.seconds)

    assertSame(request, shadowOf(audioManager).lastAbandonedAudioFocusRequest)
  }

  @Test
  fun aRefusedFocusLoadsTheFileWithoutPlayingIt() = test {
    shadowOf(audioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)

    player.play(first)
    assertEquals(
        AudioState(first, isPlaying = false, positionMs = 0, durationMs = 5_000),
        player.state.value,
    )
    player.resume()
    assertFalse(player.state.value.isPlaying)

    shadowOf(audioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
    player.resume()
    assertTrue(player.state.value.isPlaying)
  }

  @Test
  fun beingAskedToPlayLowerPauses() = test {
    player.play(first)

    player.focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)

    assertFalse(player.state.value.isPlaying)
  }

  @Test
  fun anErrorWhilePlayingUnloadsTheFile() = test {
    val broken = File(context.cacheDir, "broken.m4a")
    ShadowMediaPlayer.addMediaInfo(
        DataSource.toDataSource(broken.path),
        mediaInfo(5.seconds).apply {
          scheduleErrorAtOffset(1_000, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
        },
    )
    player.play(broken)

    playFor(2.seconds)

    assertEquals(AudioState(), player.state.value)
    assertSame(
        shadowOf(audioManager).lastAudioFocusRequest.audioFocusRequest,
        shadowOf(audioManager).lastAbandonedAudioFocusRequest,
    )
  }

  @Test
  fun anUnknownDurationCountsAsZero() = test {
    val unknown = File(context.cacheDir, "unknown.m4a")
    ShadowMediaPlayer.addMediaInfo(
        DataSource.toDataSource(unknown.path),
        mediaInfo((-1).milliseconds),
    )

    player.play(unknown)
    player.seekTo(1_000)
    player.pause()

    assertEquals(0L, player.state.value.durationMs)
    assertEquals(0L, player.state.value.positionMs)
  }
}
