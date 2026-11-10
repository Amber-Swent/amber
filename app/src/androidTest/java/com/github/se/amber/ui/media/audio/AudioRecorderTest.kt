// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.media.audio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.se.amber.data.media.audio.WAVEFORM_BARS
import com.github.se.amber.model.media.AudioRecordingState
import com.github.se.amber.ui.theme.AUDIO_WAVEFORM_MIN_LEVEL
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose tests for [DisplayAudioRecorder]. The composable is state-hoisted: no ViewModel.
 *
 * The waveform is only checked as a displayed canvas: the bars it draws are covered by
 * `DisplayedWaveformLevelsTest`.
 */
class AudioRecorderTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val waveform = List(WAVEFORM_BARS) { it / WAVEFORM_BARS.toFloat() }

  private var deleteCount = 0
  private var stopCount = 0
  private var playCount = 0

  private fun launch(
      recordingState: AudioRecordingState,
      waveform: List<Float> = this.waveform,
  ) {
    composeTestRule.setContent {
      DisplayAudioRecorder(
          recordingState = recordingState,
          waveform = waveform,
          onDelete = { deleteCount++ },
          onStop = { stopCount++ },
          onPlay = { playCount++ },
      )
    }
  }

  // ---------- while recording ----------

  @Test
  fun whileRecordingTheDeleteButton_theStopButton_andTheWaveformCanvasAreDisplayed() {
    launch(recordingState = AudioRecordingState.RECORDING)
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.DELETE_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.STOP_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.WAVEFORM).assertIsDisplayed()
  }

  @Test
  fun whileRecordingAWaveformCanvasWithoutLevelsYetIsDisplayed() {
    launch(recordingState = AudioRecordingState.RECORDING, waveform = emptyList())
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.WAVEFORM).assertIsDisplayed()
  }

  @Test
  fun clickingStopNotifiesOnce() {
    launch(recordingState = AudioRecordingState.RECORDING)
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.STOP_BUTTON).performClick()
    Assert.assertEquals(1, stopCount)
    Assert.assertEquals(0, deleteCount)
  }

  @Test
  fun clickingDeleteWhileRecordingNotifiesOnce() {
    launch(recordingState = AudioRecordingState.RECORDING)
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.DELETE_BUTTON).performClick()
    Assert.assertEquals(1, deleteCount)
    Assert.assertEquals(0, stopCount)
  }

  // ---------- once the recording is over ----------

  @Test
  fun onceRecordedTheStopButtonIsHidden_theDeleteButtonAndTheWaveformCanvasAreDisplayed() {
    launch(recordingState = AudioRecordingState.RECORDED)
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.STOP_BUTTON).assertDoesNotExist()
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.DELETE_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.WAVEFORM).assertIsDisplayed()
  }

  @Test
  fun clickingDeleteOnceRecordedNotifiesOnce() {
    launch(recordingState = AudioRecordingState.RECORDED)
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.DELETE_BUTTON).performClick()
    Assert.assertEquals(1, deleteCount)
  }

  @Test
  fun onceRecordedThePlayButtonIsDisplayed() {
    launch(recordingState = AudioRecordingState.RECORDED)
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.PLAY_BUTTON).assertIsDisplayed()
  }

  @Test
  fun whileRecordingThePlayButtonIsHidden() {
    launch(recordingState = AudioRecordingState.RECORDING)
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.PLAY_BUTTON).assertDoesNotExist()
  }

  @Test
  fun clickingPlayNotifiesOnce() {
    launch(recordingState = AudioRecordingState.RECORDED)
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.PLAY_BUTTON).performClick()
    Assert.assertEquals(1, playCount)
  }

  // ---------- while listening ----------

  @Test
  fun whileListeningTheStopButtonIsDisplayed() {
    launch(recordingState = AudioRecordingState.LISTENING)
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.STOP_BUTTON).assertIsDisplayed()
  }

  @Test
  fun whileListeningThePlayButtonIsHidden() {
    launch(recordingState = AudioRecordingState.LISTENING)
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.PLAY_BUTTON).assertDoesNotExist()
  }

  @Test
  fun whileListeningTheDeleteButtonIsDisplayed() {
    launch(recordingState = AudioRecordingState.LISTENING)
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.DELETE_BUTTON).assertIsDisplayed()
  }

  @Test
  fun clickingStopWhileListeningNotifiesOnce() {
    launch(recordingState = AudioRecordingState.LISTENING)
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.STOP_BUTTON).performClick()
    Assert.assertEquals(1, stopCount)
  }

  // ---------- Waveform Levels ----------

  @Test
  fun levelsAboveTheMinimumAreKept() {
    val levels = listOf(0.5f, 0.8f, 1f)
    assertEquals(levels, displayedWaveformLevels(levels))
  }

  @Test
  fun aSilentLevelIsRaisedToTheMinimum() {
    assertEquals(listOf(AUDIO_WAVEFORM_MIN_LEVEL), displayedWaveformLevels(listOf(0f)))
  }

  @Test
  fun aLevelEqualToTheMinimumIsKept() {
    assertEquals(
        listOf(AUDIO_WAVEFORM_MIN_LEVEL),
        displayedWaveformLevels(listOf(AUDIO_WAVEFORM_MIN_LEVEL)),
    )
  }

  @Test
  fun onlyTheMaximumNumberOfBarsIsDisplayed() {
    assertEquals(WAVEFORM_BARS, displayedWaveformLevels(List(WAVEFORM_BARS + 5) { 0.5f }).size)
  }

  @Test
  fun theFirstBarsAreTheOnesKeptWhenThereAreTooMany() {
    // In practice, this shouldn’t happen with a finished audio file.
    // The `buildWaveform` method in `AndroidAudioRecorder.kt` always truncates
    // the recording to exactly 64 bars, each representing the peak of its respective section.
    val levels = List(WAVEFORM_BARS + 1) { if (it < WAVEFORM_BARS) 0.5f else 1f }
    assertEquals(List(WAVEFORM_BARS) { 0.5f }, displayedWaveformLevels(levels))
  }

  // ---------- previews ----------

  @Test
  fun recordingPreview_showsButtons_andWaveformCanvas() {
    composeTestRule.setContent { DisplayAudioRecorderRecordingPreview() }
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.DELETE_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.WAVEFORM).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.STOP_BUTTON).assertIsDisplayed()
  }

  @Test
  fun recordedPreview_showsButton_andWaveformCanvas() {
    composeTestRule.setContent { DisplayAudioRecorderRecordedPreview() }
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.DELETE_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.WAVEFORM).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.PLAY_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.STOP_BUTTON).assertDoesNotExist()
  }

  @Test
  fun listeningPreview_showsButtons_andWaveformCanvas() {
    composeTestRule.setContent { DisplayAudioRecorderListeningPreview() }
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.DELETE_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.WAVEFORM).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.STOP_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AudioRecorderTestTags.PLAY_BUTTON).assertDoesNotExist()
  }
}
