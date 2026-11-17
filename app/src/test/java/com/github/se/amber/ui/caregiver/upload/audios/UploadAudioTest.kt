// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.upload.audios

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.github.se.amber.R
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
