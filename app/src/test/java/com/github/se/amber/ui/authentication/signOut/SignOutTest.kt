// Written by Alaabrine, with assistance from Cursor.
package com.github.se.amber.ui.authentication.signOut

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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

  private fun setScreen() {
    composeTestRule.setContent {
      AmberAppTheme {
        SignOutScreen(
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
    stateFlow.value = SignOutUIState(errorMsg = "error")
    setScreen()

    composeTestRule.waitForIdle()

    verify { viewModel.clearErrorMsg() }
  }

  @Test
  fun noToastIsShownWhenThereIsNoError() {
    setScreen()

    composeTestRule.waitForIdle()

    assertEquals(0, ShadowToast.shownToastCount())
  }
}
