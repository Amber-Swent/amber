/* Written by Yuhan YANG, with assistance from
 * Cursor.
 */
package com.github.se.amber.ui.caregiver.parameters

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.credentials.CredentialManager
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tests the caregiver parameters buttons, bars, hint, and sign-out action. */
@RunWith(RobolectricTestRunner::class)
class CaregiverParameterScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun createInvitationButtonIsDisplayed() {
    setScreen()

    composeTestRule
        .onNodeWithTag(CaregiverParametersTestTags.CREATE_INVITATION_BUTTON)
        .assertIsDisplayed()
        .assertTextContains("Create Invitation")
  }

  @Test
  fun enterInvitationButtonIsDisplayed() {
    setScreen()

    composeTestRule
        .onNodeWithTag(CaregiverParametersTestTags.ENTER_INVITATION_BUTTON)
        .assertIsDisplayed()
        .assertTextContains("Enter Invitation")
  }

  @Test
  fun invitationHintIsDisplayed() {
    setScreen()

    composeTestRule
        .onNodeWithTag(CaregiverParametersTestTags.INVITATION_HINT)
        .assertTextEquals(
            "Invitation code is needed to add families/patient into the family circle",
        )
  }

  @Test
  fun topBarIsDisplayed() {
    setScreen()

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU).assertIsDisplayed()
  }

  @Test
  fun bottomBarIsDisplayed() {
    setScreen()

    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU)
        .assertIsDisplayed()
  }

  @Test
  fun createClickInvokesTheCreateCallback() {
    var createClicks = 0
    setScreen(onCreateInvitationClick = { createClicks++ })

    composeTestRule
        .onNodeWithTag(CaregiverParametersTestTags.CREATE_INVITATION_BUTTON)
        .performClick()

    assertEquals(1, createClicks)
  }

  @Test
  fun enterClickInvokesTheEnterCallback() {
    var enterClicks = 0
    setScreen(onEnterInvitationClick = { enterClicks++ })

    composeTestRule
        .onNodeWithTag(CaregiverParametersTestTags.ENTER_INVITATION_BUTTON)
        .performClick()

    assertEquals(1, enterClicks)
  }

  @Test
  fun checkoutCallsViewModelSignOut() {
    val credentialManager = mockk<CredentialManager>(relaxed = true)
    val viewModel = fakeViewModel()
    setScreen(viewModel = viewModel, credentialManager = credentialManager)

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    verify(exactly = 1) { viewModel.signOut(credentialManager) }
  }

  @Test
  fun signedOutInvokesOnSignedOut() {
    var signedOutClicks = 0
    setScreen(
        viewModel = fakeViewModel(CaregiverParameterUIState(signedOut = true)),
        onSignedOut = { signedOutClicks++ },
    )

    composeTestRule.waitForIdle()

    assertEquals(1, signedOutClicks)
  }

  private fun fakeViewModel(
      state: CaregiverParameterUIState = CaregiverParameterUIState(),
  ): CaregiverParameterViewModel {
    val viewModel = mockk<CaregiverParameterViewModel>(relaxed = true)
    every { viewModel.uiState } returns MutableStateFlow(state)
    return viewModel
  }

  private fun setScreen(
      viewModel: CaregiverParameterViewModel = fakeViewModel(),
      credentialManager: CredentialManager = mockk(relaxed = true),
      onSignedOut: () -> Unit = {},
      onCreateInvitationClick: () -> Unit = {},
      onEnterInvitationClick: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      CaregiverParameterScreen(
          viewModel = viewModel,
          credentialManager = credentialManager,
          onSignedOut = onSignedOut,
          onCreateInvitationClick = onCreateInvitationClick,
          onEnterInvitationClick = onEnterInvitationClick,
      )
    }
  }
}
