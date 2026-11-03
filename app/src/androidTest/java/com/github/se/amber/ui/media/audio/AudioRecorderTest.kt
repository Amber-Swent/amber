/* Written by LaMaier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.media.audio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.se.amber.data.media.audio.WAVEFORM_BARS
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Compose tests for [DisplayAudioRecorder]. The composable is state-hoisted: no ViewModel. */
class AudioRecorderTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val waveform = List(WAVEFORM_BARS) { it / WAVEFORM_BARS.toFloat() }

  private var deleteCount = 0
  private var stopCount = 0

  @Before
  fun setUp() {
    deleteCount = 0
    stopCount = 0
  }

  private fun launch(isRecording: Boolean, waveform: List<Float> = this.waveform) {
    composeTestRule.setContent {
      DisplayAudioRecorder(
          isRecording = isRecording,
          waveform = waveform,
          onDelete = { deleteCount++ },
          onStop = { stopCount++ },
      )
    }
  }

  // ---------- while recording ----------

  @Test
  fun whileRecordingTheDelete_andStop_ButtonIsDisplayed() {
    launch(isRecording = true)
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.DELETE_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.STOP_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.WAVEFORM).assertIsDisplayed()
  }

  @Test
  fun whileRecordingAWaveformWithoutLevelsYetIsDisplayed() {
    launch(isRecording = true, waveform = emptyList())
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.WAVEFORM).assertIsDisplayed()
  }

  @Test
  fun clickingStopNotifiesOnce() {
    launch(isRecording = true)
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.STOP_BUTTON).performClick()
    Assert.assertEquals(1, stopCount)
    Assert.assertEquals(0, deleteCount)
  }

  @Test
  fun clickingDeleteWhileRecordingNotifiesOnce() {
    launch(isRecording = true)
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.DELETE_BUTTON).performClick()
    Assert.assertEquals(1, deleteCount)
    Assert.assertEquals(0, stopCount)
  }

  // ---------- once the recording is over ----------

  @Test
  fun onceRecordedTheStopButtonIsHidden_andDeleteButtonIsDisplay() {
    launch(isRecording = false)
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.STOP_BUTTON).assertDoesNotExist()
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.DELETE_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.WAVEFORM).assertIsDisplayed()
  }

  @Test
  fun clickingDeleteOnceRecordedNotifiesOnce() {
    launch(isRecording = false)
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.DELETE_BUTTON).performClick()
    Assert.assertEquals(1, deleteCount)
  }

  // ---------- previews ----------

  @Test
  fun recordingPreview_showsButtons_andWaveform() {
    composeTestRule.setContent { DisplayAudioRecorderRecordingPreview() }
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.DELETE_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.WAVEFORM).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.STOP_BUTTON).assertIsDisplayed()
  }

  @Test
  fun recordedPreview_showsButton_andWaveform() {
    composeTestRule.setContent { DisplayAudioRecorderRecordedPreview() }
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.DELETE_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.WAVEFORM).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.STOP_BUTTON).assertDoesNotExist()
  }
}
