// this code was written with the aid of AI
package com.github.se.amber.ui.roleLoading

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.se.amber.model.user.Role
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

  private val errorMessage = "Could not load your profile. Try again."

  @Before
  fun setUp() {
    loadedRoles.clear()
    signedOutCount = 0
    stateFlow = MutableStateFlow(RoleState.Loading)
    viewModel = mockk(relaxed = true)
    io.mockk.every { viewModel.state } returns stateFlow
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

  // ---------- Loading ----------

  @Test
  fun loading_showsProgressIndicator() {
    setScreen(RoleState.Loading)

    composeTestRule
        .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
        .assertIsDisplayed()
  }

  @Test
  fun loading_doesNotShowErrorNorBackToLogin() {
    setScreen(RoleState.Loading)

    composeTestRule.onNodeWithText(errorMessage).assertDoesNotExist()
    composeTestRule.onNodeWithText("Retry").assertDoesNotExist()
    composeTestRule.onNodeWithText("Back to login").assertDoesNotExist()
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

    composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
  }

  @Test
  fun error_clickOnRetry_callsLoadRole() {
    setScreen(RoleState.Error)

    composeTestRule.onNodeWithText("Retry").performClick()

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

    composeTestRule.onNodeWithText("Back to login").assertIsDisplayed()
  }

  @Test
  fun noProfile_clickOnBackToLogin_signsOutAndCallsOnSignedOut() {
    setScreen(RoleState.NoProfile)

    composeTestRule.onNodeWithText("Back to login").performClick()

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

    composeTestRule.onNodeWithText(errorMessage).assertDoesNotExist()
    composeTestRule.onNodeWithText("Retry").assertDoesNotExist()
    composeTestRule.onNodeWithText("Back to login").assertDoesNotExist()
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

    composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
  }

  @Test
  fun transitionLoadingToNoProfile_showsBackToLogin() {
    setScreen(RoleState.Loading)
    composeTestRule.waitForIdle()

    stateFlow.value = RoleState.NoProfile
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Back to login").assertIsDisplayed()
  }
}
