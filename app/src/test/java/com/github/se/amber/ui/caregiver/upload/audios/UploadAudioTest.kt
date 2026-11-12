// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Lou-Anne Maier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.upload.audios

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * Tests what [UploadAudioScreen] does around its form: asking for the microphone permission when
 * the user taps record, and leaving once the upload is over. The ViewModel is mocked and
 * Robolectric records the permission request instead of showing the system popup, so nothing
 * touches the network.
 */
@RunWith(RobolectricTestRunner::class)
class UploadAudioTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var viewModel: UploadAudioViewModel

  @Before
  fun setUp() {
    viewModel = mockk(relaxed = true)
    every { viewModel.uiState } returns MutableStateFlow(UploadAudioUIState())
    every { viewModel.involvedOptions } returns MutableStateFlow(emptyMap())
    every { viewModel.placeOptions } returns MutableStateFlow(emptyMap())
    every { viewModel.events } returns emptyFlow()
  }

  private fun launchScreen(onUpload: () -> Unit = {}) {
    composeTestRule.setContent { UploadAudioScreen(viewModel = viewModel, onUpload = onUpload) }
    composeTestRule.waitForIdle()
  }

  private fun lastRequest() = shadowOf(composeTestRule.activity).lastRequestedPermission

  // ---------- microphone permission ----------

  private fun clickRecord() {
    composeTestRule.onNodeWithTag(UploadAudioScreenTestTags.RECORD_AUDIO).performClick()
    composeTestRule.waitForIdle()
  }

  private fun grantMicrophone() {
    shadowOf(ApplicationProvider.getApplicationContext<Application>())
        .grantPermissions(Manifest.permission.RECORD_AUDIO)
  }

  /** Answers the permission popup that [clickRecord] opened, the way the system would. */
  // onRequestPermissionsResult is deprecated, but the permission launcher of the screen receives
  // its result through it, so calling it is how the test plays the system
  @Suppress("DEPRECATION")
  private fun answerPopup(result: Int) {
    val request = lastRequest()
    composeTestRule.runOnUiThread {
      composeTestRule.activity.onRequestPermissionsResult(
          request.requestCode,
          request.requestedPermissions,
          intArrayOf(result),
      )
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun opening_doesNotAskForTheMicrophone() {
    launchScreen()
    assertNull(lastRequest())
  }

  @Test
  fun clickingRecord_asksForTheMicrophoneWhenItIsNotGranted() {
    launchScreen()
    clickRecord()
    assertTrue(Manifest.permission.RECORD_AUDIO in lastRequest().requestedPermissions)
  }

  @Test
  fun clickingRecord_doesNotStartRecordingWhenTheMicrophoneIsNotGranted() {
    launchScreen()
    clickRecord()
    verify(exactly = 0) { viewModel.startRecording() }
  }

  @Test
  fun clickingRecord_startsRecordingWhenTheMicrophoneIsGranted() {
    grantMicrophone()
    launchScreen()
    clickRecord()
    verify(exactly = 1) { viewModel.startRecording() }
  }

  @Test
  fun clickingRecord_doesNotAskWhenTheMicrophoneIsGranted() {
    grantMicrophone()
    launchScreen()
    clickRecord()
    assertNull(lastRequest())
  }

  @Test
  fun grantingTheMicrophoneInThePopup_startsRecording() {
    launchScreen()
    clickRecord()
    answerPopup(PackageManager.PERMISSION_GRANTED)
    verify(exactly = 1) { viewModel.startRecording() }
  }

  @Test
  fun refusingTheMicrophoneInThePopup_doesNotStartRecording() {
    launchScreen()
    clickRecord()
    answerPopup(PackageManager.PERMISSION_DENIED)
    verify(exactly = 0) { viewModel.startRecording() }
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

  @Test
  fun maximumLengthMessageIsShownAsAToastWhenItOccurs() {
    every { viewModel.uiState } returns
        MutableStateFlow(
            UploadAudioUIState(
                common =
                    CommonMediaUIState(
                        errorMsgRes = UploadAudioViewModelErrorMessage.MAX_LENGTH_REACHED.messageRes
                    )
            )
        )
    launchScreen()
    assertEquals(
        composeTestRule.activity.getString(R.string.uploadAudioErrorMaxLength),
        ShadowToast.getTextOfLatestToast(),
    )
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
