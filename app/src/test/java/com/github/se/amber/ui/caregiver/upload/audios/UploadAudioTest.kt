// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.upload.audios

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import com.github.se.amber.R
import com.github.se.amber.model.media.AudioRecordingState
import com.github.se.amber.ui.caregiver.upload.media.CommonMediaUIState
import com.github.se.amber.ui.caregiver.upload.media.MediaEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * Tests what [UploadAudioScreen] does around its form: stopping the recording when the app goes to
 * the background, leaving once the upload is over, and the error shown in French. The microphone
 * popups are tested in [UploadAudioMicrophoneDialogsTest]. The ViewModel is mocked, so nothing
 * touches the network.
 */
@RunWith(RobolectricTestRunner::class)
class UploadAudioTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var viewModel: UploadAudioViewModel
  private val state = MutableStateFlow(UploadAudioUIState())

  @Before
  fun setUp() {
    viewModel = mockk(relaxed = true)
    state.value = UploadAudioUIState()
    every { viewModel.uiState } returns state
    every { viewModel.involvedOptions } returns MutableStateFlow(emptyMap())
    every { viewModel.placeOptions } returns MutableStateFlow(emptyMap())
    every { viewModel.events } returns emptyFlow()
  }

  private fun launchScreen(onUpload: () -> Unit = {}) {
    composeTestRule.setContent { UploadAudioScreen(viewModel = viewModel, onUpload = onUpload) }
    composeTestRule.waitForIdle()
  }

  // ---------- information under the recorder ----------

  private fun showRecordingState(recordingState: AudioRecordingState) {
    every { viewModel.uiState } returns
        MutableStateFlow(UploadAudioUIState(recordingState = recordingState))
    launchScreen()
  }

  @Test
  fun recordingInfo_isDisplayedWhileRecording() {
    showRecordingState(AudioRecordingState.RECORDING)
    composeTestRule.onNodeWithTag(UploadAudioScreenTestTags.RECORDING_INFO).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.uploadAudioRecordingInfoFirst))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.uploadAudioRecordingInfoSecond))
        .assertIsDisplayed()

    composeTestRule.onNodeWithTag(UploadAudioScreenTestTags.RECORDED_INFO).assertDoesNotExist()
  }

  @Test
  fun recordingInfo_All_areHiddenBeforeRecording() {
    showRecordingState(AudioRecordingState.IDLE)
    composeTestRule.onNodeWithTag(UploadAudioScreenTestTags.RECORDING_INFO).assertDoesNotExist()
    composeTestRule.onNodeWithTag(UploadAudioScreenTestTags.RECORDED_INFO).assertDoesNotExist()
  }

  @Test
  fun recordedInfo_isDisplayedOnceRecorded() {
    showRecordingState(AudioRecordingState.RECORDED)
    composeTestRule.onNodeWithTag(UploadAudioScreenTestTags.RECORDED_INFO).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.uploadAudioRecordedInfo))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(UploadAudioScreenTestTags.RECORDING_INFO).assertDoesNotExist()
  }

  @Test
  @Config(qualifiers = "fr")
  fun recordedInfoIsShownInFrench() {
    showRecordingState(AudioRecordingState.RECORDED)
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.uploadAudioRecordedInfo))
        .assertIsDisplayed()
  }

  private fun showMaximumLengthReached(errorRes: Int? = R.string.uploadAudioErrorMaxLength) {
    every { viewModel.uiState } returns
        MutableStateFlow(
            UploadAudioUIState(
                recordingState = AudioRecordingState.RECORDED,
                recordingErrorRes = errorRes,
            )
        )
    launchScreen()
  }

  @Test
  fun recordingError_isDisplayedWhenTheMaximumLengthWasReached() {
    showMaximumLengthReached()
    composeTestRule.onNodeWithTag(UploadAudioScreenTestTags.RECORDING_ERROR).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.uploadAudioErrorMaxLength))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(UploadAudioScreenTestTags.RECORDING_INFO).assertDoesNotExist()
  }

  @Test
  fun recordedInfo_isDisplayedWhenThereIsNoRecordingError() {
    showMaximumLengthReached(errorRes = null)
    composeTestRule.onNodeWithTag(UploadAudioScreenTestTags.RECORDED_INFO).assertIsDisplayed()
    composeTestRule.onNodeWithTag(UploadAudioScreenTestTags.RECORDING_ERROR).assertDoesNotExist()
  }

  @Test
  @Config(qualifiers = "fr")
  fun recordingErrorIsShownInFrench() {
    showMaximumLengthReached()
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.uploadAudioErrorMaxLength))
        .assertIsDisplayed()
  }

  // ---------- app in the background ----------

  @Test
  fun movingTheAppToTheBackground_stopsTheRecording() {
    launchScreen()
    composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
    composeTestRule.waitForIdle()
    verify(exactly = 1) { viewModel.stopRecording() }
  }

  // ---------- upload event ----------

  @Test
  fun theUploadedEventCallsOnUpload() {
    every { viewModel.events } returns flowOf(MediaEvent.Uploaded)
    var uploads = 0
    launchScreen(onUpload = { uploads++ })
    assertEquals(1, uploads)
  }

  // ---------- French ----------

  @Test
  @Config(qualifiers = "fr")
  fun noAudioRecordedErrorIsShownInFrenchWhenItOccurs() {
    every { viewModel.uiState } returns
        MutableStateFlow(
            UploadAudioUIState(
                common =
                    CommonMediaUIState(
                        errorMsgRes = UploadAudioViewModelErrorMessage.RECORD_AUDIO.messageRes
                    )
            )
        )
    launchScreen()
    assertEquals(
        composeTestRule.activity.getString(R.string.uploadAudioErrorRecord),
        ShadowToast.getTextOfLatestToast(),
    )
  }
}
