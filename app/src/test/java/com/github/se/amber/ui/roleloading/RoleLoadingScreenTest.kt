// this code was written with the aid of AI
package com.github.se.amber.ui.roleloading

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.se.amber.model.user.Role
import com.github.se.amber.ui.authentication.roleLoading.RoleLoadingScreen
import com.github.se.amber.ui.authentication.roleLoading.RoleLoadingTestTags
import com.github.se.amber.ui.authentication.roleLoading.RoleLoadingUIState
import com.github.se.amber.ui.authentication.roleLoading.RoleLoadingViewModel
import com.github.se.amber.ui.authentication.roleLoading.RoleState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoleLoadingScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var viewModel: RoleLoadingViewModel
  private lateinit var stateFlow: MutableStateFlow<RoleLoadingUIState>

  // Spy: onRoleLoaded adds each role it receives to this list (see setScreen),
  // so the tests can check how many times it was called and with which role.
  private val loadedRoles = mutableListOf<Role>()

  // Spy: onSignedOut increments this counter (see setScreen).
  private var signedOutCount = 0

  // Spy: noProfile increments this counter (see setScreen).
  private var noProfileCount = 0

  @Before
  fun setUp() {
    loadedRoles.clear()
    signedOutCount = 0
    noProfileCount = 0
    stateFlow = MutableStateFlow(RoleLoadingUIState())
    viewModel = mockk(relaxed = true)
    every { viewModel.state } returns stateFlow
  }

  private fun setScreen(initialState: RoleLoadingUIState) {
    stateFlow.value = initialState
    composeTestRule.setContent {
      RoleLoadingScreen(
          onRoleLoaded = { loadedRoles.add(it) },
          noProfile = { noProfileCount++ },
          onSignedOut = { signedOutCount++ },
          viewModel = viewModel,
      )
    }
  }

  private fun setScreen(roleState: RoleState) = setScreen(RoleLoadingUIState(roleState = roleState))

  private fun assertNothingButLoadingIsShown() {
    composeTestRule.onNodeWithTag(RoleLoadingTestTags.ERROR_MESSAGE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(RoleLoadingTestTags.RETRY_BUTTON).assertDoesNotExist()
    composeTestRule.onNodeWithTag(RoleLoadingTestTags.BACK_TO_LOGIN_BUTTON).assertDoesNotExist()
  }

  // ---------- Loading ----------

  @Test
  fun loading_showsProgressIndicator() {
    setScreen(RoleState.Loading)

    composeTestRule.onNodeWithTag(RoleLoadingTestTags.LOADING_INDICATOR).assertIsDisplayed()
  }

  @Test
  fun loading_doesNotShowErrorNorBackToLogin() {
    setScreen(RoleState.Loading)

    assertNothingButLoadingIsShown()
  }

  @Test
  fun loading_triggersLoadRoleOnce() {
    setScreen(RoleState.Loading)
    composeTestRule.waitForIdle()

    verify(exactly = 1) { viewModel.loadRole() }
  }

  // ---------- Not loading at start: no automatic load ----------

  @Test
  fun initialStateError_doesNotTriggerLoadRole() {
    setScreen(RoleState.Error)
    composeTestRule.waitForIdle()

    verify(exactly = 0) { viewModel.loadRole() }
  }

  @Test
  fun initialStateNoProfile_doesNotTriggerLoadRole() {
    setScreen(RoleState.NoProfile)
    composeTestRule.waitForIdle()

    verify(exactly = 0) { viewModel.loadRole() }
  }

  // ---------- Error ----------

  @Test
  fun error_showsMessageAndRetryButton() {
    setScreen(RoleState.Error)

    composeTestRule.onNodeWithTag(RoleLoadingTestTags.ERROR_MESSAGE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(RoleLoadingTestTags.RETRY_BUTTON).assertIsDisplayed()
  }

  @Test
  fun error_clickOnRetry_callsLoadRole() {
    setScreen(RoleState.Error)

    composeTestRule.onNodeWithTag(RoleLoadingTestTags.RETRY_BUTTON).performClick()

    verify(exactly = 1) { viewModel.loadRole() }
  }

  @Test
  fun error_doesNotCallCallbacks() {
    setScreen(RoleState.Error)
    composeTestRule.waitForIdle()

    assertTrue(loadedRoles.isEmpty())
    assertEquals(0, signedOutCount)
    assertEquals(0, noProfileCount)
  }

  @Test
  fun error_showsBackToLoginButton() {
    setScreen(RoleState.Error)

    composeTestRule.onNodeWithTag(RoleLoadingTestTags.BACK_TO_LOGIN_BUTTON).assertIsDisplayed()
  }

  @Test
  fun error_backToLoginButton_isEnabledByDefault() {
    setScreen(RoleState.Error)

    composeTestRule.onNodeWithTag(RoleLoadingTestTags.BACK_TO_LOGIN_BUTTON).assertIsEnabled()
  }

  @Test
  fun error_clickOnBackToLogin_callsSignOut() {
    setScreen(RoleState.Error)

    composeTestRule.onNodeWithTag(RoleLoadingTestTags.BACK_TO_LOGIN_BUTTON).performClick()

    verify(exactly = 1) { viewModel.signOut() }
  }

  @Test
  fun error_backToLoginButton_isDisabledWhileSigningOut() {
    setScreen(RoleLoadingUIState(roleState = RoleState.Error, isSigningOut = true))

    composeTestRule.onNodeWithTag(RoleLoadingTestTags.BACK_TO_LOGIN_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun error_backToLoginButton_isDisabledOnceSignedOut() {
    setScreen(RoleLoadingUIState(roleState = RoleState.Error, signedOut = true))

    composeTestRule.onNodeWithTag(RoleLoadingTestTags.BACK_TO_LOGIN_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun transitionToSigningOut_disablesBackToLoginButton() {
    setScreen(RoleState.Error)
    composeTestRule.onNodeWithTag(RoleLoadingTestTags.BACK_TO_LOGIN_BUTTON).assertIsEnabled()

    stateFlow.value = stateFlow.value.copy(isSigningOut = true)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(RoleLoadingTestTags.BACK_TO_LOGIN_BUTTON).assertIsNotEnabled()
  }

  // ---------- Sign-out ----------

  @Test
  fun signedOut_callsOnSignedOutOnce() {
    setScreen(RoleLoadingUIState(roleState = RoleState.Error, signedOut = true))
    composeTestRule.waitForIdle()

    assertEquals(1, signedOutCount)
    assertEquals(0, noProfileCount)
    assertTrue(loadedRoles.isEmpty())
  }

  @Test
  fun notSignedOut_doesNotCallOnSignedOut() {
    setScreen(RoleState.Error)
    composeTestRule.waitForIdle()

    assertEquals(0, signedOutCount)
  }

  // ---------- Error message ----------

  @Test
  fun errorMsg_isClearedAfterBeingShown() {
    setScreen(RoleLoadingUIState(roleState = RoleState.Error, errorMsg = "Something failed"))
    composeTestRule.waitForIdle()

    verify(exactly = 1) { viewModel.clearErrorMsg() }
  }

  @Test
  fun noErrorMsg_doesNotCallClearErrorMsg() {
    setScreen(RoleState.Error)
    composeTestRule.waitForIdle()

    verify(exactly = 0) { viewModel.clearErrorMsg() }
  }

  // ---------- NoProfile ----------

  @Test
  fun noProfile_callsNoProfileCallback() {
    setScreen(RoleState.NoProfile)
    composeTestRule.waitForIdle()

    assertEquals(1, noProfileCount)
    assertTrue(loadedRoles.isEmpty())
    assertEquals(0, signedOutCount)
  }

  @Test
  fun noProfile_displaysNothing() {
    setScreen(RoleState.NoProfile)
    composeTestRule.waitForIdle()

    assertNothingButLoadingIsShown()
    composeTestRule.onNodeWithTag(RoleLoadingTestTags.LOADING_INDICATOR).assertDoesNotExist()
  }

  // ---------- Loaded ----------

  @Test
  fun loadedPatient_callsOnRoleLoadedWithPatient() {
    setScreen(RoleState.Loaded(Role.PATIENT))
    composeTestRule.waitForIdle()

    assertEquals(listOf(Role.PATIENT), loadedRoles)
    assertEquals(0, signedOutCount)
    assertEquals(0, noProfileCount)
  }

  @Test
  fun loadedCaregiver_callsOnRoleLoadedWithCaregiver() {
    setScreen(RoleState.Loaded(Role.CAREGIVER))
    composeTestRule.waitForIdle()

    assertEquals(listOf(Role.CAREGIVER), loadedRoles)
    assertEquals(0, signedOutCount)
    assertEquals(0, noProfileCount)
  }

  @Test
  fun loaded_displaysNothing() {
    setScreen(RoleState.Loaded(Role.PATIENT))
    composeTestRule.waitForIdle()

    assertNothingButLoadingIsShown()
  }

  // ---------- State transitions ----------

  @Test
  fun transitionLoadingToLoaded_callsOnRoleLoaded() {
    setScreen(RoleState.Loading)
    composeTestRule.waitForIdle()
    assertTrue(loadedRoles.isEmpty())

    stateFlow.value = RoleLoadingUIState(roleState = RoleState.Loaded(Role.CAREGIVER))
    composeTestRule.waitForIdle()

    assertEquals(listOf(Role.CAREGIVER), loadedRoles)
  }

  @Test
  fun transitionLoadingToError_showsRetry() {
    setScreen(RoleState.Loading)
    composeTestRule.waitForIdle()

    stateFlow.value = RoleLoadingUIState(roleState = RoleState.Error)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(RoleLoadingTestTags.RETRY_BUTTON).assertIsDisplayed()
  }

  @Test
  fun transitionLoadingToNoProfile_callsNoProfile() {
    setScreen(RoleState.Loading)
    composeTestRule.waitForIdle()
    assertEquals(0, noProfileCount)

    stateFlow.value = RoleLoadingUIState(roleState = RoleState.NoProfile)
    composeTestRule.waitForIdle()

    assertEquals(1, noProfileCount)
  }
}
