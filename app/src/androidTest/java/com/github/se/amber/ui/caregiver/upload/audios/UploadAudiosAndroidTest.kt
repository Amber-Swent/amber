// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.upload.audios

import android.widget.Toast
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
import androidx.test.platform.app.InstrumentationRegistry
import com.github.se.amber.R
import com.github.se.amber.data.media.audio.WAVEFORM_BARS
import com.github.se.amber.model.media.AudioRecordingState
import com.github.se.amber.ui.caregiver.upload.media.CommonMediaUIState
import com.github.se.amber.ui.media.audio.AudioRecorderTestTags
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Compose tests for the recording part of [UploadAudioScreen]. The ViewModel is mocked. */
class UploadAudiosAndroidTest {

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

  private val listening = recorded.copy(recordingState = AudioRecordingState.LISTENING)

  @Before
  fun setUp() {
    // Already granted, so the permission popup of the screen doesn't cover the tests
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.uiAutomation.grantRuntimePermission(
        instrumentation.targetContext.packageName,
        android.Manifest.permission.RECORD_AUDIO,
    )
    uiStateFlow = MutableStateFlow(UploadAudioUIState())
    viewModel = mockk(relaxed = true)
    every { viewModel.uiState } returns uiStateFlow
    every { viewModel.involvedOptions } returns MutableStateFlow(emptyMap())
    every { viewModel.placeOptions } returns MutableStateFlow(emptyMap())
    every { viewModel.events } returns emptyFlow()
  }

  private fun launchScreen(
      state: UploadAudioUIState = UploadAudioUIState(),
      goBack: () -> Unit = {},
  ) {
    uiStateFlow.value = state
    composeTestRule.setContent { UploadAudioScreen(viewModel = viewModel, goBack = goBack) }
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
  fun clickingTheRecordButtonTellsTheViewModel() {
    launchScreen()
    tagged(UploadAudioScreenTestTags.RECORD_AUDIO).performScrollTo().performClick()
    verify(exactly = 1) { viewModel.onRecordClick(any(), any()) }
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
  fun onceRecordedThePlayButtonIsDisplayed() {
    launchScreen(recorded)
    tagged(AudioRecorderTestTags.PLAY_BUTTON).assertIsDisplayed()
  }

  @Test
  fun onceRecordedTheStopButtonIsNotDisplayed() {
    launchScreen(recorded)
    tagged(AudioRecorderTestTags.STOP_BUTTON).assertDoesNotExist()
  }

  @Test
  fun clickingPlayOnceRecordedStartsListening() {
    launchScreen(recorded)
    tagged(AudioRecorderTestTags.PLAY_BUTTON).performScrollTo().performClick()
    verify(exactly = 1) { viewModel.playRecording() }
  }

  // ---------- listening ----------

  @Test
  fun whileListeningOnlyTheListeningAudioIsDisplayed() {
    launchScreen(listening)
    tagged(UploadAudioScreenTestTags.LISTENING).assertIsDisplayed()
  }

  @Test
  fun whileListeningTheRecordedAudioIsNotDisplayed() {
    launchScreen(listening)
    tagged(UploadAudioScreenTestTags.AUDIO_RECORDED).assertDoesNotExist()
  }

  @Test
  fun whileListeningTheDeleteAndStopButtonsAreDisplayed() {
    launchScreen(listening)
    tagged(AudioRecorderTestTags.DELETE_BUTTON).assertIsDisplayed()
    tagged(AudioRecorderTestTags.STOP_BUTTON).assertIsDisplayed()
  }

  @Test
  fun whileListeningThePlayButtonIsNotDisplayed() {
    launchScreen(listening)
    tagged(AudioRecorderTestTags.PLAY_BUTTON).assertDoesNotExist()
  }

  @Test
  fun clickingStopWhileListeningStopsListening() {
    launchScreen(listening)
    tagged(AudioRecorderTestTags.STOP_BUTTON).performScrollTo().performClick()
    verify(exactly = 1) { viewModel.stopListening() }
  }

  @Test
  fun clickingDeleteWhileListeningDeletesTheAudio() {
    launchScreen(listening)
    tagged(AudioRecorderTestTags.DELETE_BUTTON).performScrollTo().performClick()
    verify(exactly = 1) { viewModel.deleteRecording() }
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

  // ---------- microphone error ----------

  private val microphoneError =
      UploadAudioUIState(
          common =
              CommonMediaUIState(
                  errorMsgRes = UploadAudioViewModelErrorMessage.MICROPHONE_NOT_ALLOWED.messageRes
              )
      )

  // The error is a Toast, which is not part of the Compose tree: Toast.makeText is mocked, so no
  // real toast is shown and the message given to it can be checked.
  private fun withMockedToast(block: () -> Unit) {
    mockkStatic(Toast::class)
    try {
      every { Toast.makeText(any(), any<CharSequence>(), any()) } returns mockk(relaxed = true)
      block()
    } finally {
      unmockkStatic(Toast::class)
    }
  }

  @Test
  fun aMicrophoneErrorIsShownInAToastWithTheMicrophoneMessage() = withMockedToast {
    launchScreen()
    setState(microphoneError)
    composeTestRule.waitForIdle()
    val expectedMessage =
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(R.string.uploadAudioErrorMicrophoneNotAllowed)
    verify { Toast.makeText(any(), expectedMessage, any()) }
  }

  @Test
  fun aMicrophoneErrorIsClearedOnceShown() = withMockedToast {
    launchScreen()
    setState(microphoneError)
    composeTestRule.waitForIdle()
    verify { viewModel.clearErrorMsg() }
  }

  // ---------- cancel ----------

  @Test
  fun cancelGoesBackWhenNothingIsBeingSaved() {
    var goBackCount = 0
    launchScreen(goBack = { goBackCount++ })
    tagged(TopNavigationMenuTestTags.BACK_BUTTON).performClick()
    assertEquals(1, goBackCount)
  }

  @Test
  fun cancelWhileTheAudioIsBeingSavedStillGoesBack() {
    var goBackCount = 0
    launchScreen(
        UploadAudioUIState(common = CommonMediaUIState(isUploading = true)),
        goBack = { goBackCount++ },
    )
    tagged(TopNavigationMenuTestTags.BACK_BUTTON).performClick()
    assertEquals(1, goBackCount)
  }

  @Test
  fun cancelTellsTheViewModelToStopTheUpload() {
    launchScreen(UploadAudioUIState(common = CommonMediaUIState(isUploading = true)))
    tagged(TopNavigationMenuTestTags.BACK_BUTTON).performClick()
    verify(exactly = 1) { viewModel.cancel() }
  }

  // ---------- scrolling ----------

  @Test
  fun scrollingToTheLastElementOfTheFormDisplaysTheDatePicker() {
    // the date picker is the last element of the scrollable form
    launchScreen()
    tagged(UploadAudioScreenTestTags.SELECT_DATE).performScrollTo().assertIsDisplayed()
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
