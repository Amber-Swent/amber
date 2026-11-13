// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.upload.audios

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.ui.caregiver.upload.media.CommonMediaUIState
import com.github.se.amber.ui.caregiver.upload.media.MediaEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
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
 * Tests what [UploadAudioScreen] does around its form: the microphone popups (an explanation before
 * the Android permission popup, and a pointer to the settings once Android stops asking) when the
 * user taps record, and leaving once the upload is over. The ViewModel is mocked and Robolectric
 * records the permission request instead of showing the system popup, so nothing touches the
 * network.
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

  @After
  fun tearDown() {
    unmockkAll()
  }

  private fun launchScreen(onUpload: () -> Unit = {}) {
    composeTestRule.setContent { UploadAudioScreen(viewModel = viewModel, onUpload = onUpload) }
    composeTestRule.waitForIdle()
  }

  /** Makes Android offer a rationale, as it does after the microphone was refused once. */
  private fun rationaleAvailable() {
    mockkStatic(ActivityCompat::class)
    every {
      ActivityCompat.shouldShowRequestPermissionRationale(
          any(),
          Manifest.permission.RECORD_AUDIO,
      )
    } returns true
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

  /** Shows the consent popup, then taps "I've understood": this is what asks Android. */
  private fun understandConsent() {
    state.value = UploadAudioUIState(isMicrophoneConsentOpen = true)
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_CONSENT_ACTION)
        .performClick()
    composeTestRule.waitForIdle()
  }

  /** Answers the permission popup that [understandConsent] opened, the way the system would. */
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
  fun clickingRecord_tellsTheViewModel_AndroidWillAskWhenItWasNeverAsked() {
    launchScreen()
    clickRecord()
    verify(exactly = 1) { viewModel.onRecordClick(granted = false, androidWillAsk = true) }
  }

  @Test
  fun clickingRecord_tellsTheViewModel_AndroidWillAskWhenARationaleIsAvailable() {
    composeTestRule.activity.markMicrophoneAsked()
    rationaleAvailable()
    launchScreen()
    clickRecord()
    verify(exactly = 1) { viewModel.onRecordClick(granted = false, androidWillAsk = true) }
  }

  @Test
  fun clickingRecord_tellsTheViewModelAndroidWillNotAskAfterTwoRefusals() {
    composeTestRule.activity.markMicrophoneAsked()
    launchScreen()
    clickRecord()
    verify(exactly = 1) { viewModel.onRecordClick(granted = false, androidWillAsk = false) }
  }

  @Test
  fun clickingRecord_tellsTheViewModelTheMicrophoneIsGranted() {
    grantMicrophone()
    launchScreen()
    clickRecord()
    verify(exactly = 1) { viewModel.onRecordClick(granted = true, any()) }
  }

  @Test
  fun clickingRecord_doesNotAskAndroidBeforeTheConsentIsUnderstood() {
    launchScreen()
    clickRecord()
    assertNull(lastRequest())
  }

  @Test
  fun clickingRecord_doesNotAskAndroidWhenTheMicrophoneIsGranted() {
    grantMicrophone()
    launchScreen()
    clickRecord()
    assertNull(lastRequest())
  }

  // ---------- consent popup ----------

  @Test
  fun consentPopup_isHiddenByDefault() {
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_CONSENT_DIALOG)
        .assertDoesNotExist()
  }

  @Test
  fun consentPopup_isDisplayedWhenOpen() {
    state.value = UploadAudioUIState(isMicrophoneConsentOpen = true)
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_CONSENT_DIALOG)
        .assertIsDisplayed()
  }

  @Test
  fun consentPopup_displaysItsTitle() {
    state.value = UploadAudioUIState(isMicrophoneConsentOpen = true)
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_CONSENT_TITLE)
        .assertTextEquals(composeTestRule.activity.getString(R.string.uploadAudioMicrophoneTitle))
  }

  @Test
  fun consentPopup_displaysItsTextFirstSentence() {
    state.value = UploadAudioUIState(isMicrophoneConsentOpen = true)
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_CONSENT_TEXT_FIRST)
        .assertTextEquals(
            composeTestRule.activity.getString(R.string.uploadAudioMicrophoneConsentTextFirst)
        )
  }

  @Test
  fun consentPopup_displaysItsTextSecondSentence() {
    state.value = UploadAudioUIState(isMicrophoneConsentOpen = true)
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_CONSENT_TEXT_SECOND)
        .assertTextEquals(
            composeTestRule.activity.getString(R.string.uploadAudioMicrophoneConsentTextSecond)
        )
  }

  @Test
  fun consentPopup_displaysTheUnderstoodButton() {
    state.value = UploadAudioUIState(isMicrophoneConsentOpen = true)
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_CONSENT_ACTION)
        .assertTextContains(
            composeTestRule.activity.getString(R.string.uploadAudioMicrophoneUnderstood)
        )
  }

  @Test
  fun consentPopup_understoodClosesIt() {
    launchScreen()
    understandConsent()
    verify(exactly = 1) { viewModel.dismissMicrophoneConsent() }
  }

  @Test
  fun consentPopup_understoodRemembersAndroidWasAsked() {
    launchScreen()
    understandConsent()
    assertTrue(composeTestRule.activity.hasAskedMicrophone())
  }

  @Test
  fun consentPopup_understoodAsksForTheMicrophone() {
    launchScreen()
    understandConsent()
    assertTrue(Manifest.permission.RECORD_AUDIO in lastRequest().requestedPermissions)
  }

  @Test
  fun consentPopup_closingItClosesIt() {
    state.value = UploadAudioUIState(isMicrophoneConsentOpen = true)
    launchScreen()
    composeTestRule.onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_CONSENT_CLOSE).performClick()
    verify(exactly = 1) { viewModel.dismissMicrophoneConsent() }
  }

  @Test
  fun consentPopup_closingItDoesNotAskForTheMicrophone() {
    state.value = UploadAudioUIState(isMicrophoneConsentOpen = true)
    launchScreen()
    composeTestRule.onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_CONSENT_CLOSE).performClick()
    assertNull(lastRequest())
  }

  // ---------- Android popup answer ----------

  @Test
  fun allowingTheMicrophoneInAndroidPopup_tellsTheViewModelItWasGranted() {
    launchScreen()
    understandConsent()
    answerPopup(PackageManager.PERMISSION_GRANTED)
    verify(exactly = 1) { viewModel.onMicrophonePermissionResult(granted = true, any()) }
  }

  @Test
  fun refusingTheMicrophoneInAndroidPopup_tellsTheViewModelItWasRefused() {
    launchScreen()
    understandConsent()
    answerPopup(PackageManager.PERMISSION_DENIED)
    verify(exactly = 1) { viewModel.onMicrophonePermissionResult(granted = false, any()) }
  }

  @Test
  fun refusingTheMicrophone_tellsTheViewModelAndroidWillNotAskAgainWithoutRationale() {
    launchScreen()
    understandConsent()
    answerPopup(PackageManager.PERMISSION_DENIED)
    verify(exactly = 1) {
      viewModel.onMicrophonePermissionResult(granted = false, canAskAgain = false)
    }
  }

  @Test
  fun refusingTheMicrophone_tellsTheViewModelAndroidWillAskAgainWithRationale() {
    rationaleAvailable()
    launchScreen()
    understandConsent()
    answerPopup(PackageManager.PERMISSION_DENIED)
    verify(exactly = 1) {
      viewModel.onMicrophonePermissionResult(granted = false, canAskAgain = true)
    }
  }

  // ---------- settings popup ----------

  @Test
  fun settingsPopup_isHiddenByDefault() {
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_SETTINGS_DIALOG)
        .assertDoesNotExist()
  }

  @Test
  fun settingsPopup_isDisplayedWhenOpen() {
    state.value = UploadAudioUIState(isMicrophoneSettingsOpen = true)
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_SETTINGS_DIALOG)
        .assertIsDisplayed()
  }

  @Test
  fun settingsPopup_displaysItsTitle() {
    state.value = UploadAudioUIState(isMicrophoneSettingsOpen = true)
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_SETTINGS_TITLE)
        .assertTextEquals(composeTestRule.activity.getString(R.string.uploadAudioMicrophoneTitle))
  }

  @Test
  fun settingsPopup_displaysItsTextFirstSentence() {
    state.value = UploadAudioUIState(isMicrophoneSettingsOpen = true)
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_SETTINGS_TEXT_FIRST)
        .assertTextEquals(
            composeTestRule.activity.getString(R.string.uploadAudioMicrophoneSettingsTextFirst)
        )
  }

  @Test
  fun settingsPopup_displaysItsTextSecondSentence() {
    state.value = UploadAudioUIState(isMicrophoneSettingsOpen = true)
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_SETTINGS_TEXT_SECOND)
        .assertTextEquals(
            composeTestRule.activity.getString(R.string.uploadAudioMicrophoneSettingsTextSecond)
        )
  }

  @Test
  fun settingsPopup_displaysTheUnderstoodButton() {
    state.value = UploadAudioUIState(isMicrophoneSettingsOpen = true)
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_SETTINGS_ACTION)
        .assertTextContains(
            composeTestRule.activity.getString(R.string.uploadAudioMicrophoneUnderstood)
        )
  }

  @Test
  fun settingsPopup_understoodClosesIt() {
    state.value = UploadAudioUIState(isMicrophoneSettingsOpen = true)
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_SETTINGS_ACTION)
        .performClick()
    verify(exactly = 1) { viewModel.dismissMicrophoneSettings() }
  }

  @Test
  fun settingsPopup_closingItClosesIt() {
    state.value = UploadAudioUIState(isMicrophoneSettingsOpen = true)
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_SETTINGS_CLOSE)
        .performClick()
    verify(exactly = 1) { viewModel.dismissMicrophoneSettings() }
  }

  @Test
  fun settingsPopup_doesNotAskForTheMicrophone() {
    state.value = UploadAudioUIState(isMicrophoneSettingsOpen = true)
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_SETTINGS_ACTION)
        .performClick()
    assertNull(lastRequest())
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

  @Test
  @Config(qualifiers = "fr")
  fun consentPopupTitleIsShownInFrench() {
    state.value = UploadAudioUIState(isMicrophoneConsentOpen = true)
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_CONSENT_TITLE)
        .assertTextEquals(composeTestRule.activity.getString(R.string.uploadAudioMicrophoneTitle))
  }

  @Test
  @Config(qualifiers = "fr")
  fun settingsPopupFirstSentenceIsShownInFrench() {
    state.value = UploadAudioUIState(isMicrophoneSettingsOpen = true)
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_SETTINGS_TEXT_FIRST)
        .assertTextEquals(
            composeTestRule.activity.getString(R.string.uploadAudioMicrophoneSettingsTextFirst)
        )
  }
}
