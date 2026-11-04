/* Written by LaMaier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload.audios

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.github.se.amber.data.media.audio.WAVEFORM_BARS
import com.github.se.amber.model.media.AudioRecordingState
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
    tagged(UploadAudioScreenTestTags.RECORD_AUDIO).performScrollTo().performClick()
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

  // ---------- preview ----------

  /**
   * A ViewModel owner whose `viewModel()` returns [mockedViewModel], not the Firebase-backed one.
   */
  private class FakeViewModelStoreOwner(private val mockedViewModel: ViewModel) :
      ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val viewModelStore = ViewModelStore()
    override val defaultViewModelProviderFactory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T = mockedViewModel as T
        }
  }

  // The preview calls UploadAudioScreen() with its default viewModel(), which would build the real
  // repositories: it receives the mocked ViewModel instead, so nothing touches the network.
  private fun launchPreview() {
    composeTestRule.setContent {
      CompositionLocalProvider(
          LocalViewModelStoreOwner provides FakeViewModelStoreOwner(viewModel)
      ) {
        UploadAudioScreenPreview()
      }
    }
  }

  @Test
  fun preview_showsTitle_andRecordButton_andUploadButton() {
    launchPreview()
    tagged(UploadAudioScreenTestTags.TITLE).assertIsDisplayed()
    tagged(UploadAudioScreenTestTags.RECORD_AUDIO).assertIsDisplayed()
    tagged(UploadAudioScreenTestTags.UPLOAD_BUTTON).assertIsDisplayed()
  }
}
