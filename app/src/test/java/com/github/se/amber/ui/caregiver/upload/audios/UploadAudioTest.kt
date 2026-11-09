// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.upload.audios

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
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

  // ---------- upload event ----------

  @Test
  fun theUploadedEventCallsOnUpload() {
    every { viewModel.events } returns flowOf(MediaEvent.Uploaded)
    var uploads = 0
    launchScreen(onUpload = { uploads++ })
    assertEquals(1, uploads)
  }
}
