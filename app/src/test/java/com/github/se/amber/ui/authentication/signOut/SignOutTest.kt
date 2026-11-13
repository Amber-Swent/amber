// Written by Alaabrine, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.authentication.signOut

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.se.amber.R
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class SignOutScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var viewModel: SignOutViewModel
  private lateinit var stateFlow: MutableStateFlow<SignOutUIState>

  private var signedOutCount = 0
  private var backCount = 0

  @Before
  fun setUp() {
    signedOutCount = 0
    backCount = 0
    stateFlow = MutableStateFlow(SignOutUIState())
    viewModel = mockk(relaxed = true)
    every { viewModel.uiState } returns stateFlow
  }

  private fun setScreen(isPatient: Boolean = false) {
    composeTestRule.setContent {
      AmberAppTheme {
        SignOutScreen(
            isPatient = isPatient,
            viewModel = viewModel,
            onSignedOut = { signedOutCount++ },
            onBack = { backCount++ },
        )
      }
    }
  }

  @Test
  fun titleAndExplanationAreDisplayed() {
    setScreen()

    composeTestRule.onNodeWithTag(SignOutTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignOutTestTags.EXPLANATION).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignOutButtonTestTags.BUTTON).assertIsDisplayed()
  }

  @Test
  fun confirmClickCallsSignOut() {
    setScreen()

    composeTestRule.onNodeWithTag(SignOutButtonTestTags.BUTTON).performClick()

    verify(exactly = 1) { viewModel.signOut() }
  }

  @Test
  fun backClickInvokesOnBack() {
    setScreen()

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    assertEquals(1, backCount)
  }

  @Test
  fun patientTitle_andExplanation_areDisplayed() {
    setScreen(isPatient = true)

    composeTestRule.onNodeWithTag(SignOutTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignOutTestTags.EXPLANATION).assertIsDisplayed()
  }

  @Test
  fun patientConfirmClickCallsSignOut() {
    setScreen(isPatient = true)

    composeTestRule.onNodeWithTag(SignOutButtonTestTags.BUTTON).performClick()

    verify(exactly = 1) { viewModel.signOut() }
  }

  @Test
  fun patientBackClickInvokesOnBack() {
    setScreen(isPatient = true)

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    assertEquals(1, backCount)
  }

  @Test
  fun patientButtonIsDisabledWhileSigningOut() {
    stateFlow.value = SignOutUIState(isSigningOut = true)
    setScreen(isPatient = true)

    composeTestRule.onNodeWithTag(SignOutButtonTestTags.BUTTON).assertIsNotEnabled()
  }

  @Test
  fun onSignedOutIsCalledWhenStateIsSignedOut() {
    setScreen()

    stateFlow.value = stateFlow.value.copy(signedOut = true)
    composeTestRule.waitForIdle()

    assertEquals(1, signedOutCount)
  }

  @Test
  fun buttonIsDisabledWhileSigningOut() {
    stateFlow.value = SignOutUIState(isSigningOut = true)
    setScreen()

    composeTestRule.onNodeWithTag(SignOutButtonTestTags.BUTTON).assertIsNotEnabled()
  }

  @Test
  fun buttonIsEnabledByDefault() {
    setScreen()

    composeTestRule.onNodeWithTag(SignOutButtonTestTags.BUTTON).assertIsEnabled()
  }

  @Test
  fun errorMessageTriggersClearErrorMsg() {
    stateFlow.value = SignOutUIState(errorMsgRes = R.string.signOutError)
    setScreen()

    composeTestRule.waitForIdle()

    verify { viewModel.clearErrorMsg() }
  }

  @Test
  @Config(qualifiers = "fr")
  fun screenIsInFrench() {
    setScreen()

    composeTestRule.onNodeWithText("Déconnexion").assertIsDisplayed()
    composeTestRule.onNodeWithText("Se déconnecter").assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "fr")
  fun errorToastIsShownInFrench() {
    stateFlow.value = SignOutUIState(errorMsgRes = R.string.signOutError)
    setScreen()
    composeTestRule.waitForIdle()

    assertEquals(
        "Impossible de se déconnecter. Veuillez réessayer.",
        ShadowToast.getTextOfLatestToast(),
    )
  }

  @Test
  @Config(qualifiers = "fr")
  fun successToastIsShownInFrench() {
    stateFlow.value = SignOutUIState(signedOut = true)
    setScreen()
    composeTestRule.waitForIdle()

    assertEquals("Déconnexion réussie", ShadowToast.getTextOfLatestToast())
  }

  @Test
  fun noToastIsShownWhenThereIsNoError() {
    setScreen()

    composeTestRule.waitForIdle()

    assertEquals(0, ShadowToast.shownToastCount())
  }

  @Test
  fun screenUsesDefaultCallbacksWhenOmitted() {
    composeTestRule.setContent { AmberAppTheme { SignOutScreen(viewModel = viewModel) } }

    composeTestRule.onNodeWithTag(SignOutTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()
    composeTestRule.waitForIdle()

    // Default onBack is a no-op; screen remains displayed.
    composeTestRule.onNodeWithTag(SignOutTestTags.TITLE).assertIsDisplayed()
    assertEquals(0, backCount)
  }

  @Test
  fun previewRendersWithoutCrashing() {
    composeTestRule.setContent { SignOutScreenPreview() }

    composeTestRule.onNodeWithTag(SignOutTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignOutButtonTestTags.BUTTON).assertIsDisplayed()
  }
}
