// Written by Yuhan YANG, with assistance from Cursor.
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.media.audio

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.R
import com.github.se.amber.data.media.audio.WAVEFORM_BARS
import com.github.se.amber.ui.theme.AUDIO_WAVEFORM_MIN_LEVEL
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_HEIGHT_SMALL
import com.github.se.amber.ui.theme.HALF
import com.github.se.amber.ui.theme.WEIGHT_ONE
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaAudioTest {

  @get:Rule val composeTestRule = createComposeRule()

  /** Text of [id] in the current test language. */
  private fun string(id: Int): String =
      ApplicationProvider.getApplicationContext<Context>().getString(id)

  @Test
  fun leftButton_callsTheActionItWasGiven() {
    var clicks = 0
    composeTestRule.setContent {
      AmberAppTheme {
        MediaAudio(
            leftIcon = Icons.Outlined.Mic,
            leftDescription = string(R.string.audioRecorderRecord),
            onLeftClick = { clicks++ },
        )
      }
    }

    composeTestRule
        .onNodeWithTag(MediaAudioTestTags.LEFT_BUTTON)
        .assertContentDescriptionEquals(string(R.string.audioRecorderRecord))
    composeTestRule.onNodeWithTag(MediaAudioTestTags.LEFT_BUTTON).performClick()
    assertEquals(1, clicks)
  }

  @Test
  fun rightButton_callsTheActionItWasGiven() {
    var clicks = 0
    composeTestRule.setContent {
      AmberAppTheme {
        MediaAudio(
            leftIcon = Icons.Outlined.Delete,
            leftDescription = string(R.string.audioRecorderDelete),
            rightButton =
                MediaAudioButton(
                    icon = Icons.Outlined.Stop,
                    description = string(R.string.audioRecorderStop),
                    onClick = { clicks++ },
                ),
        )
      }
    }

    composeTestRule
        .onNodeWithTag(MediaAudioTestTags.RIGHT_BUTTON)
        .assertContentDescriptionEquals(string(R.string.audioRecorderStop))
    composeTestRule.onNodeWithTag(MediaAudioTestTags.RIGHT_BUTTON).performClick()
    assertEquals(1, clicks)
  }

  @Test
  fun caregiverRow_usesTheSmallerCaregiverButton() {
    var clicks = 0
    composeTestRule.setContent {
      AmberAppTheme {
        MediaAudio(
            isPatient = false,
            leftDescription = string(R.string.audioRecorderDelete),
            onLeftClick = { clicks++ },
            rightButton =
                MediaAudioButton(
                    icon = Icons.Outlined.Stop,
                    description = string(R.string.audioRecorderStop),
                    onClick = { clicks++ },
                ),
        )
      }
    }

    composeTestRule
        .onNodeWithTag(MediaAudioTestTags.LEFT_BUTTON)
        .assertHeightIsEqualTo(CAREGIVER_BUTTON_HEIGHT_SMALL)
        .performClick()
    composeTestRule.onNodeWithTag(MediaAudioTestTags.RIGHT_BUTTON).performClick()
    assertEquals(2, clicks)
  }

  @Test
  fun missingRightButton_isNotShown() {
    composeTestRule.setContent { AmberAppTheme { MediaAudio() } }

    composeTestRule.onNodeWithTag(MediaAudioTestTags.LEFT_BUTTON).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(MediaAudioTestTags.RIGHT_BUTTON).assertCountEquals(0)
  }

  @Test
  fun waveform_isShownWhenNothingIsRecorded() {
    showWaveform(AudioWaveformState.None)
  }

  @Test
  fun waveform_isShownWhileRecording() {
    showWaveform(AudioWaveformState.Recording(levels = List(WAVEFORM_BARS / 2) { HALF }))
  }

  @Test
  fun waveform_isShownOnceRecorded() {
    showWaveform(AudioWaveformState.Recorded(levels = List(WAVEFORM_BARS) { HALF }))
  }

  @Test
  fun waveform_isShownWhileListening() {
    showWaveform(
        AudioWaveformState.Listening(levels = List(WAVEFORM_BARS) { HALF }, playedFraction = HALF)
    )
  }

  @Test
  fun preview_showsEachWaveformState() {
    composeTestRule.setContent { MediaAudioPreview() }

    composeTestRule
        .onAllNodesWithContentDescription(string(R.string.audioRecorderRecord))
        .assertCountEquals(1)
    composeTestRule
        .onAllNodesWithContentDescription(string(R.string.audioRecorderDelete))
        .assertCountEquals(2)
    composeTestRule
        .onAllNodesWithContentDescription(string(R.string.audioRecorderStop))
        .assertCountEquals(1)
    composeTestRule
        .onAllNodesWithContentDescription(string(R.string.audioPlaybackPlay))
        .assertCountEquals(1)
  }

  @Test
  fun defaults_drawAnEmptyWaveformAndKeepTheGivenLevels() {
    assertEquals(AudioWaveformState.Recording(), AudioWaveformState.Recording(emptyList()))
    assertEquals(AudioWaveformState.Recorded(), AudioWaveformState.Recorded(emptyList()))
    assertEquals(
        AudioWaveformState.Listening(),
        AudioWaveformState.Listening(emptyList(), playedFraction = 0f),
    )
    assertEquals(emptyList<Float>(), displayedWaveformLevels())
    assertEquals(listOf(AUDIO_WAVEFORM_MIN_LEVEL), displayedWaveformLevels(listOf(0f)))

    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          MediaAudio(
              waveform = AudioWaveformState.Recording(),
              waveformTestTag = "recording_default",
          )
          MediaAudio(waveform = AudioWaveformState.Recorded(), waveformTestTag = "recorded_default")
          MediaAudio(
              waveform = AudioWaveformState.Listening(),
              waveformTestTag = "listening_default",
          )
          DisplayAudioWaveform(modifier = Modifier.testTag("waveform_default"))
        }
      }
    }

    composeTestRule.onNodeWithTag("recording_default").assertIsDisplayed()
    composeTestRule.onNodeWithTag("recorded_default").assertIsDisplayed()
    composeTestRule.onNodeWithTag("listening_default").assertIsDisplayed()
    composeTestRule.onNodeWithTag("waveform_default").assertIsDisplayed()
  }

  @Test
  fun listening_clampsAPlayedPortionOutsideZeroToOne() {
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          MediaAudio(
              waveform = AudioWaveformState.Listening(playedFraction = -1f),
              waveformTestTag = "below",
          )
          MediaAudio(
              waveform =
                  AudioWaveformState.Listening(
                      levels = listOf(HALF),
                      playedFraction = WEIGHT_ONE + HALF,
                  ),
              waveformTestTag = "above",
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("below").assertIsDisplayed()
    composeTestRule.onNodeWithTag("above").assertIsDisplayed()
  }

  @Test
  fun passedParameters_disableTheButtonsAndShowTheSpinner() {
    composeTestRule.setContent {
      AmberAppTheme {
        MediaAudio(
            modifier = Modifier.testTag("row"),
            waveform = AudioWaveformState.Recorded(levels = listOf(0f)),
            leftIcon = Icons.Outlined.Mic,
            leftDescription = string(R.string.audioRecorderRecord),
            leftEnabled = false,
            showLeftSpinner = true,
            leftTestTag = "left",
            leftSpinnerTestTag = "spin",
            rightButton =
                MediaAudioButton(
                    icon = Icons.Outlined.Stop,
                    description = string(R.string.audioRecorderStop),
                    onClick = {},
                    enabled = false,
                    testTag = "right",
                ),
            waveformTestTag = "wave",
            waveformModifier = Modifier,
        )
      }
    }

    composeTestRule.onNodeWithTag("row").assertIsDisplayed()
    composeTestRule.onNodeWithTag("spin").assertIsDisplayed()
    composeTestRule.onNodeWithTag("left").assertIsNotEnabled()
    composeTestRule.onNodeWithTag("right").assertIsNotEnabled()
    composeTestRule.onNodeWithTag("wave").assertIsDisplayed()
  }

  private fun showWaveform(waveform: AudioWaveformState) {
    composeTestRule.setContent { AmberAppTheme { MediaAudio(waveform = waveform) } }
    composeTestRule.onNodeWithTag(MediaAudioTestTags.WAVEFORM).assertIsDisplayed()
  }
}
