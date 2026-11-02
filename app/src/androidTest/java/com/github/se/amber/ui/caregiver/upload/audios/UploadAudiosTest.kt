/* Written by LaMaier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload.audios

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.se.amber.data.media.audio.WAVEFORM_BARS
import com.github.se.amber.model.media.AudioRecordingState
import com.github.se.amber.ui.caregiver.DisplayBoxWithIconTextTestTags
import com.github.se.amber.ui.media.audio.AudioRecorderTestTags
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Compose tests for the recording part of [UploadAudioScreen]. The ViewModel is mocked. */
class UploadAudiosTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var viewModel: UploadAudioViewModel
  private lateinit var uiStateFlow: MutableStateFlow<UploadAudioUIState>

  private val recording =
      UploadAudioUIState(
          recordingState = AudioRecordingState.RECORDING,
          waveform = List(WAVEFORM_BARS / 2) { 0.5f },
      )
  private val recorded =
      UploadAudioUIState(
          recordingState = AudioRecordingState.RECORDED,
          audioFile = File("audio.m4a"),
          waveform = List(WAVEFORM_BARS) { 0.5f },
      )

  @Before
  fun setUp() {
    uiStateFlow = MutableStateFlow(UploadAudioUIState())
    viewModel = mockk(relaxed = true)
    every { viewModel.uiState } returns uiStateFlow
    every { viewModel.involvedOptions } returns MutableStateFlow(emptyMap())
  }

  private fun launchScreen(state: UploadAudioUIState = UploadAudioUIState()) {
    uiStateFlow.value = state
    composeTestRule.setContent { UploadAudioScreen(viewModel = viewModel) }
  }

  private fun setState(state: UploadAudioUIState) {
    composeTestRule.runOnIdle { uiStateFlow.value = state }
  }

  private fun tagged(tag: String) = composeTestRule.onNodeWithTag(tag)

  // ---------- nothing recorded ----------

  @Test
  fun initiallyOnlyTheRecordButtonIsDisplayed() {
    launchScreen()
    tagged(UploadAudioScreenTestTags.RECORD_AUDIO).assertIsDisplayed()
    tagged(UploadAudioScreenTestTags.RECORDING).assertDoesNotExist()
    tagged(UploadAudioScreenTestTags.AUDIO_RECORDED).assertDoesNotExist()
  }

  @Test
  fun clickingTheRecordButtonStartsTheRecording() {
    launchScreen()
    composeTestRule
        .onNode(
            hasTestTag(DisplayBoxWithIconTextTestTags.ICON_BUTTON) and
                hasAnyAncestor(hasTestTag(UploadAudioScreenTestTags.RECORD_AUDIO))
        )
        .performScrollTo()
        .performClick()
    verify(exactly = 1) { viewModel.startRecording() }
  }

  // ---------- recording ----------

  @Test
  fun whileRecordingOnlyTheRecordingIsDisplayed() {
    launchScreen(recording)
    tagged(UploadAudioScreenTestTags.RECORD_AUDIO).assertDoesNotExist()
    tagged(UploadAudioScreenTestTags.RECORDING).assertIsDisplayed()
    tagged(UploadAudioScreenTestTags.AUDIO_RECORDED).assertDoesNotExist()
  }

  @Test
  fun whileRecordingTheNecessaryIsDisplayed() {
    // necessary means the waveform, the delete Button and the stop Button
    launchScreen(recording)
    tagged(AudioRecorderTestTags.WAVEFORM).assertIsDisplayed()
    tagged(AudioRecorderTestTags.DELETE_BUTTON).assertIsDisplayed()
    tagged(AudioRecorderTestTags.STOP_BUTTON).assertIsDisplayed()
  }

  @Test
  fun clickingStopStopsTheRecording() {
    launchScreen(recording)
    tagged(AudioRecorderTestTags.STOP_BUTTON).performScrollTo().performClick()
    verify(exactly = 1) { viewModel.stopRecording() }
  }

  @Test
  fun clickingDeleteWhileRecordingDeletesTheRecording() {
    launchScreen(recording)
    tagged(AudioRecorderTestTags.DELETE_BUTTON).performScrollTo().performClick()
    verify(exactly = 1) { viewModel.deleteRecording() }
  }

  // ---------- recording over ----------

  @Test
  fun onceRecordedOnlyTheRecordedAudioIsDisplayed() {
    launchScreen(recorded)
    tagged(UploadAudioScreenTestTags.RECORD_AUDIO).assertDoesNotExist()
    tagged(UploadAudioScreenTestTags.RECORDING).assertDoesNotExist()
    tagged(UploadAudioScreenTestTags.AUDIO_RECORDED).assertIsDisplayed()
  }

  @Test
  fun onceRecordedTheNecessaryIsDisplayed() {
    // necessary means the full waveform and the delete Button
    launchScreen(recorded)
    tagged(AudioRecorderTestTags.WAVEFORM).assertIsDisplayed()
    tagged(AudioRecorderTestTags.STOP_BUTTON).assertDoesNotExist()
    tagged(AudioRecorderTestTags.DELETE_BUTTON).assertIsDisplayed()
  }

  @Test
  fun clickingDeleteOnceRecordedDeletesTheAudio() {
    launchScreen(recorded)
    tagged(AudioRecorderTestTags.DELETE_BUTTON).performScrollTo().performClick()
    verify(exactly = 1) { viewModel.deleteRecording() }
  }

  // ---------- transitions ----------

  @Test
  fun stoppingTheRecordingSwapsItForTheRecordedAudio() {
    launchScreen(recording)
    setState(recorded)
    tagged(UploadAudioScreenTestTags.AUDIO_RECORDED).assertIsDisplayed()
  }

  @Test
  fun deletingTheRecordedAudioBringsTheRecordButtonBack() {
    launchScreen(recorded)
    setState(UploadAudioUIState())
    tagged(UploadAudioScreenTestTags.RECORD_AUDIO).assertIsDisplayed()
  }
}
