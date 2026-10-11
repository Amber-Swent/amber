// this code was written with the aid of AI
package com.github.se.amber.ui.roleLoading

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.se.amber.model.user.Role
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
  private lateinit var stateFlow: MutableStateFlow<RoleState>

  // Spy: onRoleLoaded adds each role it receives to this list (see setScreen),
  // so the tests can check how many times it was called and with which role.

  private val loadedRoles = mutableListOf<Role>()
  // Spy: onSignedOut increments this counter (see setScreen),
  // so the tests can check how many times it was called.
  private var signedOutCount = 0

  @Before
  fun setUp() {
    loadedRoles.clear()
    signedOutCount = 0
    stateFlow = MutableStateFlow(RoleState.Loading)
    viewModel = mockk(relaxed = true)
    every { viewModel.state } returns stateFlow
  }

  private fun setScreen(initialState: RoleState) {
    stateFlow.value = initialState
    composeTestRule.setContent {
      RoleLoadingScreen(
          onRoleLoaded = { loadedRoles.add(it) },
          viewModel = viewModel,
          onSignedOut = { signedOutCount++ },
      )
    }
  }

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
  }

  // ---------- NoProfile ----------

  @Test
  fun noProfile_showsBackToLoginButton() {
    setScreen(RoleState.NoProfile)

    composeTestRule.onNodeWithTag(RoleLoadingTestTags.BACK_TO_LOGIN_BUTTON).assertIsDisplayed()
  }

  @Test
  fun noProfile_clickOnBackToLogin_signsOutAndCallsOnSignedOut() {
    setScreen(RoleState.NoProfile)

    composeTestRule.onNodeWithTag(RoleLoadingTestTags.BACK_TO_LOGIN_BUTTON).performClick()

    verify(exactly = 1) { viewModel.signOut() }
    assertEquals(1, signedOutCount)
    assertTrue(loadedRoles.isEmpty())
  }

  // ---------- Loaded ----------

  @Test
  fun loadedPatient_callsOnRoleLoadedWithPatient() {
    setScreen(RoleState.Loaded(Role.PATIENT))
    composeTestRule.waitForIdle()

    assertEquals(listOf(Role.PATIENT), loadedRoles)
    assertEquals(0, signedOutCount)
  }

  @Test
  fun loadedCaregiver_callsOnRoleLoadedWithCaregiver() {
    setScreen(RoleState.Loaded(Role.CAREGIVER))
    composeTestRule.waitForIdle()

    assertEquals(listOf(Role.CAREGIVER), loadedRoles)
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

    stateFlow.value = RoleState.Loaded(Role.CAREGIVER)
    composeTestRule.waitForIdle()

    assertEquals(listOf(Role.CAREGIVER), loadedRoles)
  }

  @Test
  fun transitionLoadingToError_showsRetry() {
    setScreen(RoleState.Loading)
    composeTestRule.waitForIdle()

    stateFlow.value = RoleState.Error
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(RoleLoadingTestTags.RETRY_BUTTON).assertIsDisplayed()
  }

  @Test
  fun transitionLoadingToNoProfile_showsBackToLogin() {
    setScreen(RoleState.Loading)
    composeTestRule.waitForIdle()

    stateFlow.value = RoleState.NoProfile
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(RoleLoadingTestTags.BACK_TO_LOGIN_BUTTON).assertIsDisplayed()
  }
}
