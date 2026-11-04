// Written by Yuhan YANG, with assistance from Cursor.
package com.github.se.amber.ui.media.audio

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PATIENT_BUTTON_HEIGHT_SMALL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

private const val TRANSITION_DURATION_MS = 190_000L
private const val TRANSITION_PLAYING_POSITION_MS = 42_000L
private const val TRANSITION_PAUSED_POSITION_MS = 95_000L
private const val TRANSITION_ERROR_MESSAGE = "Playback failed"

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AudioPlaybackTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun idle_showsPlayRestartAndZeroProgress() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayAudioPlayback(state = AudioPlaybackState.Idle(durationMs = 190_000L)) }
    }

    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE).assertIsEnabled()
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE)
        .assertContentDescriptionEquals(AudioPlaybackDescriptions.PLAY)
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.RESTART).assertIsEnabled()
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.RESTART)
        .assertContentDescriptionEquals(AudioPlaybackDescriptions.RESTART)
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.TIME).assertTextEquals("0:00 / 3:10")
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.PROGRESS)
        .assertRangeInfoEquals(rangeInfo(0f))
    composeTestRule.onAllNodesWithTag(AudioPlaybackTestTags.SPINNER).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(AudioPlaybackTestTags.RETRY).assertCountEquals(0)
    assertSquareButton(AudioPlaybackTestTags.PLAY_PAUSE)
    assertSquareButton(AudioPlaybackTestTags.RESTART)
  }

  @Test
  fun defaults_showZeroTimeAndReportPlayAndRestart() {
    var playClicks = 0
    var restartClicks = 0
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayAudioPlayback(onPlayPause = { playClicks++ }, onRestart = { restartClicks++ })
      }
    }

    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.TIME).assertTextEquals("0:00 / 0:00")
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE).performClick()
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.RESTART).performClick()
    assertEquals(1, playClicks)
    assertEquals(1, restartClicks)
  }

  @Test
  fun omittedCallbacks_leaveTheIdleClockUnchanged() {
    composeTestRule.setContent { AmberAppTheme { DisplayAudioPlayback() } }

    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE).performClick()
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.RESTART).performClick()
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.TIME).assertTextEquals("0:00 / 0:00")
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE)
        .assertContentDescriptionEquals(AudioPlaybackDescriptions.PLAY)
  }

  @Test
  fun omittedRetry_leavesTheErrorMessage() {
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayAudioPlayback(
            state = AudioPlaybackState.Error(message = "This memo could not be played.")
        )
      }
    }

    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.RETRY).performClick()
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.ERROR)
        .assertTextEquals("This memo could not be played.")
    composeTestRule.onAllNodesWithTag(AudioPlaybackTestTags.PLAY_PAUSE).assertCountEquals(0)
  }

  @Test
  fun transportDefaults_showPlayAtZeroAndIgnoreClicks() {
    composeTestRule.setContent { AmberAppTheme { PlaybackTransport() } }

    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE).assertIsEnabled()
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE)
        .assertContentDescriptionEquals(AudioPlaybackDescriptions.PLAY)
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.RESTART)
        .assertContentDescriptionEquals(AudioPlaybackDescriptions.RESTART)
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.TIME).assertTextEquals("0:00 / 0:00")
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.PROGRESS)
        .assertRangeInfoEquals(rangeInfo(0f))
    composeTestRule.onAllNodesWithTag(AudioPlaybackTestTags.SPINNER).assertCountEquals(0)
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE).performClick()
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.RESTART).performClick()
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.TIME).assertTextEquals("0:00 / 0:00")
  }

  @Test
  fun errorDefaults_keepTheRetryButton() {
    composeTestRule.setContent { AmberAppTheme { PlaybackError() } }

    composeTestRule.onNodeWithText(AudioPlaybackDescriptions.RETRY).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(AudioPlaybackTestTags.PLAY_PAUSE).assertCountEquals(0)
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.RETRY).performClick()
    composeTestRule.onNodeWithText(AudioPlaybackDescriptions.RETRY).assertIsDisplayed()
  }

  @Test
  fun progressDefault_isEmpty() {
    composeTestRule.setContent { AmberAppTheme { PlaybackProgress() } }

    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.PROGRESS)
        .assertRangeInfoEquals(rangeInfo(0f))
  }

  @Test
  fun spinner_isTaggedWithTheAudioSpinnerTag() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayAudioPlayback(state = AudioPlaybackState.Loading()) }
    }

    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.SPINNER).assertIsDisplayed()
  }

  @Test
  fun playing_showsPauseTimeAndProgressAndReportsTheClick() {
    var playClicks = 0
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayAudioPlayback(
            state = AudioPlaybackState.Playing(positionMs = 42_000L, durationMs = 190_000L),
            onPlayPause = { playClicks++ },
        )
      }
    }

    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE)
        .assertContentDescriptionEquals(AudioPlaybackDescriptions.PAUSE)
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.TIME).assertTextEquals("0:42 / 3:10")
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.PROGRESS)
        .assertRangeInfoEquals(rangeInfo(42_000f / 190_000f))
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE).performClick()
    assertEquals(1, playClicks)
  }

  @Test
  fun paused_showsPlayAndReportsTheClick() {
    var playClicks = 0
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayAudioPlayback(
            state = AudioPlaybackState.Paused(positionMs = 95_000L, durationMs = 190_000L),
            onPlayPause = { playClicks++ },
        )
      }
    }

    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE)
        .assertContentDescriptionEquals(AudioPlaybackDescriptions.PLAY)
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.TIME).assertTextEquals("1:35 / 3:10")
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.PROGRESS)
        .assertRangeInfoEquals(rangeInfo(0.5f))
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE).performClick()
    assertEquals(1, playClicks)
  }

  @Test
  fun restart_reportsTheClickWhilePlaying() {
    var restartClicks = 0
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayAudioPlayback(
            state = AudioPlaybackState.Playing(positionMs = 1_000L, durationMs = 4_000L),
            onRestart = { restartClicks++ },
        )
      }
    }
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.RESTART).performClick()
    assertEquals(1, restartClicks)
  }

  @Test
  fun restart_reportsTheClickWhilePaused() {
    var restartClicks = 0
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayAudioPlayback(
            state = AudioPlaybackState.Paused(positionMs = 1_000L, durationMs = 4_000L),
            onRestart = { restartClicks++ },
        )
      }
    }
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.RESTART).performClick()
    assertEquals(1, restartClicks)
  }

  @Test
  fun loading_disablesTheButtonsAndShowsASpinner() {
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayAudioPlayback(state = AudioPlaybackState.Loading(durationMs = 190_000L))
      }
    }

    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.RESTART).assertIsNotEnabled()
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.RESTART)
        .assertContentDescriptionEquals(AudioPlaybackDescriptions.RESTART)
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.SPINNER).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.TIME).assertTextEquals("0:00 / 3:10")
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.PROGRESS)
        .assertRangeInfoEquals(rangeInfo(0f))
  }

  @Test
  fun error_showsTheMessageAndRetryAndHidesPlayback() {
    var retries = 0
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayAudioPlayback(
            state = AudioPlaybackState.Error(message = "This memo could not be played."),
            onRetry = { retries++ },
        )
      }
    }

    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.ERROR)
        .assertTextEquals("This memo could not be played.")
    composeTestRule.onNodeWithText(AudioPlaybackDescriptions.RETRY).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.RETRY)
        .assertHeightIsEqualTo(PATIENT_BUTTON_HEIGHT_SMALL)
    composeTestRule.onAllNodesWithTag(AudioPlaybackTestTags.PLAY_PAUSE).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(AudioPlaybackTestTags.RESTART).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(AudioPlaybackTestTags.PROGRESS).assertCountEquals(0)
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.RETRY).performClick()
    assertEquals(1, retries)
  }

  @Test
  fun progress_fillsWhenPositionPassesTheEnd() {
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayAudioPlayback(
            state = AudioPlaybackState.Playing(positionMs = 9_000L, durationMs = 4_000L)
        )
      }
    }
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.TIME).assertTextEquals("0:09 / 0:04")
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.PROGRESS)
        .assertRangeInfoEquals(rangeInfo(1f))
  }

  @Test
  fun progress_isEmptyWhenPositionIsNegative() {
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayAudioPlayback(
            state = AudioPlaybackState.Paused(positionMs = -1_500L, durationMs = 4_000L)
        )
      }
    }
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.TIME).assertTextEquals("0:00 / 0:04")
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.PROGRESS)
        .assertRangeInfoEquals(rangeInfo(0f))
  }

  @Test
  fun progress_isEmptyWhenTheDurationIsZero() {
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayAudioPlayback(state = AudioPlaybackState.Idle(positionMs = 5_000L, durationMs = 0L))
      }
    }
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.TIME).assertTextEquals("0:05 / 0:00")
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.PROGRESS)
        .assertRangeInfoEquals(rangeInfo(0f))
  }

  @Test
  fun progress_isEmptyWhenTheDurationIsNegative() {
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayAudioPlayback(
            state = AudioPlaybackState.Loading(positionMs = 1_000L, durationMs = -1L)
        )
      }
    }
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.PROGRESS)
        .assertRangeInfoEquals(rangeInfo(0f))
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.SPINNER).assertIsDisplayed()
  }

  @Test
  fun modifier_isAppliedToTheCard() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayAudioPlayback(modifier = Modifier.testTag("custom_playback")) }
    }
    composeTestRule.onNodeWithTag("custom_playback").assertIsDisplayed()
  }

  @Test
  fun playbackStateChanges_updateTheDisplayedControls() {
    val playbackState =
        mutableStateOf<AudioPlaybackState>(
            AudioPlaybackState.Idle(durationMs = TRANSITION_DURATION_MS)
        )
    composeTestRule.setContent {
      AmberAppTheme { DisplayAudioPlayback(state = playbackState.value) }
    }

    // Idle
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE)
        .assertContentDescriptionEquals(AudioPlaybackDescriptions.PLAY)
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.TIME).assertTextEquals("0:00 / 3:10")

    // Idle -> Loading
    composeTestRule.runOnIdle {
      playbackState.value = AudioPlaybackState.Loading(durationMs = TRANSITION_DURATION_MS)
    }
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.RESTART).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.SPINNER).assertIsDisplayed()

    // Loading -> Playing
    composeTestRule.runOnIdle {
      playbackState.value =
          AudioPlaybackState.Playing(
              positionMs = TRANSITION_PLAYING_POSITION_MS,
              durationMs = TRANSITION_DURATION_MS,
          )
    }
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE).assertIsEnabled()
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE)
        .assertContentDescriptionEquals(AudioPlaybackDescriptions.PAUSE)
    composeTestRule.onAllNodesWithTag(AudioPlaybackTestTags.SPINNER).assertCountEquals(0)
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.TIME).assertTextEquals("0:42 / 3:10")
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.PROGRESS)
        .assertRangeInfoEquals(
            rangeInfo(TRANSITION_PLAYING_POSITION_MS.toFloat() / TRANSITION_DURATION_MS)
        )

    // Playing -> Paused
    composeTestRule.runOnIdle {
      playbackState.value =
          AudioPlaybackState.Paused(
              positionMs = TRANSITION_PAUSED_POSITION_MS,
              durationMs = TRANSITION_DURATION_MS,
          )
    }
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE)
        .assertContentDescriptionEquals(AudioPlaybackDescriptions.PLAY)
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.TIME).assertTextEquals("1:35 / 3:10")

    // Paused -> Error
    composeTestRule.runOnIdle {
      playbackState.value = AudioPlaybackState.Error(message = TRANSITION_ERROR_MESSAGE)
    }
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.ERROR)
        .assertTextEquals(TRANSITION_ERROR_MESSAGE)
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.RETRY).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(AudioPlaybackTestTags.PLAY_PAUSE).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(AudioPlaybackTestTags.RESTART).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(AudioPlaybackTestTags.PROGRESS).assertCountEquals(0)

    // Error -> Playing: the controls come back
    composeTestRule.runOnIdle {
      playbackState.value =
          AudioPlaybackState.Playing(
              positionMs = TRANSITION_PLAYING_POSITION_MS,
              durationMs = TRANSITION_DURATION_MS,
          )
    }
    composeTestRule.onAllNodesWithTag(AudioPlaybackTestTags.ERROR).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(AudioPlaybackTestTags.RETRY).assertCountEquals(0)
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE)
        .assertContentDescriptionEquals(AudioPlaybackDescriptions.PAUSE)
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.TIME).assertTextEquals("0:42 / 3:10")
  }

  @Test
  fun preview_showsEveryPlaybackState() {
    composeTestRule.setContent { AudioPlaybackPreview() }

    composeTestRule.onNodeWithText("0:42 / 3:10").assertIsDisplayed()
    composeTestRule.onNodeWithText("1:35 / 3:10").assertIsDisplayed()
    composeTestRule.onNodeWithText("This memo could not be played.").assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(AudioPlaybackTestTags.SPINNER).assertCountEquals(1)
    composeTestRule.onAllNodesWithTag(AudioPlaybackTestTags.PLAY_PAUSE).assertCountEquals(4)
  }

  @Test
  fun clockAndFraction_coverTheBoundaries() {
    assertEquals("0:00", formatPlaybackClock())
    assertEquals("0:00", formatPlaybackClock(-1_500L))
    assertEquals("1:00", formatPlaybackClock(60_000L))
    assertEquals("1:30", formatPlaybackClock(90_000L))
    assertEquals("60:00", formatPlaybackClock(3_600_000L))
    assertEquals("0:00 / 0:00", formatPlaybackTime())
    assertEquals("0:42 / 3:10", formatPlaybackTime(42_000L, 190_000L))

    assertEquals(0f, playbackFraction())
    assertEquals(0f, playbackFraction(positionMs = 5_000L, durationMs = 0L))
    assertEquals(0f, playbackFraction(positionMs = 5_000L, durationMs = -5L))
    assertEquals(0f, playbackFraction(positionMs = -1_000L, durationMs = 4_000L))
    assertEquals(0.25f, playbackFraction(positionMs = 1_000L, durationMs = 4_000L))
    assertEquals(1f, playbackFraction(positionMs = 9_000L, durationMs = 4_000L))
  }

  @Test
  fun buttonContent_matchesTheState() {
    val idle = AudioPlaybackState.Idle()
    val loading = AudioPlaybackState.Loading()
    val playing = AudioPlaybackState.Playing()
    val paused = AudioPlaybackState.Paused()
    val error = AudioPlaybackState.Error(message = "gone")

    assertEquals(AudioPlaybackDescriptions.PLAY, playPauseDescription())
    assertEquals(AudioPlaybackDescriptions.PLAY, playPauseDescription(idle))
    assertEquals(AudioPlaybackDescriptions.LOADING, playPauseDescription(loading))
    assertEquals(AudioPlaybackDescriptions.PAUSE, playPauseDescription(playing))
    assertEquals(AudioPlaybackDescriptions.PLAY, playPauseDescription(paused))
    assertEquals(AudioPlaybackDescriptions.PLAY, playPauseDescription(error))

    assertEquals(Icons.Outlined.PlayArrow, playPauseIcon())
    assertEquals(Icons.Outlined.Pause, playPauseIcon(playing))
    assertEquals(Icons.Outlined.PlayArrow, playPauseIcon(paused))

    assertTrue(playbackControlsEnabled())
    assertTrue(playbackControlsEnabled(idle))
    assertTrue(playbackControlsEnabled(playing))
    assertTrue(playbackControlsEnabled(paused))
    assertTrue(playbackControlsEnabled(error))
    assertFalse(playbackControlsEnabled(loading))
  }

  private fun assertSquareButton(tag: String) {
    composeTestRule.onNodeWithTag(tag).assertWidthIsEqualTo(PATIENT_BUTTON_HEIGHT_SMALL)
    composeTestRule.onNodeWithTag(tag).assertHeightIsEqualTo(PATIENT_BUTTON_HEIGHT_SMALL)
  }

  private fun rangeInfo(progress: Float) = ProgressBarRangeInfo(progress, 0f..1f)
}
