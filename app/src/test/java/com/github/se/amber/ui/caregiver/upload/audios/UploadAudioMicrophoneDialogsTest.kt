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
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tests the microphone popups of [UploadAudioScreen] - see [UploadAudioMicrophoneDialogs]: the
 * explanation shown when the user taps record (before the Android permission popup), the pointer to
 * the phone's settings once Android stops asking, the answer of the Android popup, and the previews
 * of both popups. The ViewModel is mocked and Robolectric records the permission request instead of
 * showing the system popup, so nothing touches the network.
 */
@RunWith(RobolectricTestRunner::class)
class UploadAudioMicrophoneDialogsTest {

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
  fun byDefault_noMicrophonePopupIsShownAndAndroidIsNotAsked() {
    launchScreen()
    assertNull(lastRequest())
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_CONSENT_DIALOG)
        .assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_SETTINGS_DIALOG)
        .assertDoesNotExist()
  }

  @Test
  fun clickingRecord_whenNeverAsked_tellsTheViewModelAndroidWillAskAndDoesNotAskYet() {
    launchScreen()
    clickRecord()
    verify(exactly = 1) { viewModel.onRecordClick(granted = false, androidWillAsk = true) }
    assertNull(lastRequest())
  }

  @Test
  fun clickingRecord_whenARationaleIsAvailable_tellsTheViewModelAndroidWillAsk() {
    composeTestRule.activity.markMicrophoneAsked()
    rationaleAvailable()
    launchScreen()
    clickRecord()
    verify(exactly = 1) { viewModel.onRecordClick(granted = false, androidWillAsk = true) }
  }

  @Test
  fun clickingRecord_afterTwoRefusals_tellsTheViewModelAndroidWillNotAsk() {
    composeTestRule.activity.markMicrophoneAsked()
    launchScreen()
    clickRecord()
    verify(exactly = 1) { viewModel.onRecordClick(granted = false, androidWillAsk = false) }
  }

  @Test
  fun clickingRecord_whenTheMicrophoneIsGranted_tellsTheViewModelAndDoesNotAskAndroid() {
    grantMicrophone()
    launchScreen()
    clickRecord()
    verify(exactly = 1) { viewModel.onRecordClick(granted = true, any()) }
    assertNull(lastRequest())
  }

  // ---------- consent popup ----------

  @Test
  fun consentPopup_whenOpen_displaysItsTitleItsTwoSentencesAndItsButton() {
    state.value = UploadAudioUIState(isMicrophoneConsentOpen = true)
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_CONSENT_DIALOG)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_CONSENT_TITLE)
        .assertTextEquals(composeTestRule.activity.getString(R.string.uploadAudioMicrophoneTitle))
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_CONSENT_TEXT_FIRST)
        .assertTextEquals(
            composeTestRule.activity.getString(R.string.uploadAudioMicrophoneConsentTextFirst)
        )
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_CONSENT_TEXT_SECOND)
        .assertTextEquals(
            composeTestRule.activity.getString(R.string.uploadAudioMicrophoneConsentTextSecond)
        )
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_CONSENT_ACTION)
        .assertTextContains(
            composeTestRule.activity.getString(R.string.uploadAudioMicrophoneUnderstood)
        )
  }

  @Test
  fun consentPopup_understood_closesItRemembersAndroidWasAskedAndAsksForTheMicrophone() {
    launchScreen()
    understandConsent()
    verify(exactly = 1) { viewModel.dismissMicrophoneConsent() }
    assertTrue(composeTestRule.activity.hasAskedMicrophone())
    assertTrue(Manifest.permission.RECORD_AUDIO in lastRequest().requestedPermissions)
  }

  @Test
  fun consentPopup_closingIt_closesItWithoutAskingForTheMicrophone() {
    state.value = UploadAudioUIState(isMicrophoneConsentOpen = true)
    launchScreen()
    composeTestRule.onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_CONSENT_CLOSE).performClick()
    verify(exactly = 1) { viewModel.dismissMicrophoneConsent() }
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
  fun refusingTheMicrophone_withoutRationale_tellsTheViewModelAndroidWillNotAskAgain() {
    launchScreen()
    understandConsent()
    answerPopup(PackageManager.PERMISSION_DENIED)
    verify(exactly = 1) {
      viewModel.onMicrophonePermissionResult(granted = false, canAskAgain = false)
    }
  }

  @Test
  fun refusingTheMicrophone_withRationale_tellsTheViewModelAndroidWillAskAgain() {
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
  fun settingsPopup_whenOpen_displaysItsTitleItsTwoSentencesAndItsButton() {
    state.value = UploadAudioUIState(isMicrophoneSettingsOpen = true)
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_SETTINGS_DIALOG)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_SETTINGS_TITLE)
        .assertTextEquals(composeTestRule.activity.getString(R.string.uploadAudioMicrophoneTitle))
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_SETTINGS_TEXT_FIRST)
        .assertTextEquals(
            composeTestRule.activity.getString(R.string.uploadAudioMicrophoneSettingsTextFirst)
        )
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_SETTINGS_TEXT_SECOND)
        .assertTextEquals(
            composeTestRule.activity.getString(R.string.uploadAudioMicrophoneSettingsTextSecond)
        )
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_SETTINGS_ACTION)
        .assertTextContains(
            composeTestRule.activity.getString(R.string.uploadAudioMicrophoneUnderstood)
        )
  }

  @Test
  fun settingsPopup_understood_closesItWithoutAskingForTheMicrophone() {
    state.value = UploadAudioUIState(isMicrophoneSettingsOpen = true)
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_SETTINGS_ACTION)
        .performClick()
    verify(exactly = 1) { viewModel.dismissMicrophoneSettings() }
    assertNull(lastRequest())
  }

  @Test
  fun settingsPopup_closingIt_closesIt() {
    state.value = UploadAudioUIState(isMicrophoneSettingsOpen = true)
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_SETTINGS_CLOSE)
        .performClick()
    verify(exactly = 1) { viewModel.dismissMicrophoneSettings() }
  }

  // ---------- previews ----------

  @Test
  fun consentDialogPreview_displaysTheConsentPopup() {
    composeTestRule.setContent { MicrophoneConsentDialogPreview() }
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_CONSENT_DIALOG)
        .assertIsDisplayed()
  }

  @Test
  fun settingsDialogPreview_displaysTheSettingsPopup() {
    composeTestRule.setContent { MicrophoneSettingsDialogPreview() }
    composeTestRule
        .onNodeWithTag(UploadAudioScreenTestTags.MICROPHONE_SETTINGS_DIALOG)
        .assertIsDisplayed()
  }

  // ---------- French ----------

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
