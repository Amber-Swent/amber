/* Written by Yuhan YANG, with assistance from
 * Cursor.
 * Modify by Lou-Anne Maier, with assistance from
 * Claude Code
 */
package com.github.se.amber.ui.caregiver.parameters

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

/** Tests the caregiver parameters screen: displayed elements, click callbacks and error toast. */
@RunWith(RobolectricTestRunner::class)
class CaregiverParameterScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private companion object {
    const val ERROR_MESSAGE = "error"
  }

  private fun setScreen(
      viewModel: CaregiverParameterViewModel = CaregiverParameterViewModel(),
      onCheckout: () -> Unit = {},
      onTabSelected: (Tab) -> Unit = {},
      onCreateInvitationClick: () -> Unit = {},
      onEnterInvitationClick: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      CaregiverParameterScreen(
          viewModel = viewModel,
          onCheckout = onCheckout,
          onTabSelected = onTabSelected,
          onCreateInvitationClick = onCreateInvitationClick,
          onEnterInvitationClick = onEnterInvitationClick,
      )
    }
  }

  // --------- is Display ------------
  @Test
  fun titleIsDisplayed() {
    setScreen()
    composeTestRule.onNodeWithTag(CaregiverParametersTestTags.TITLE).assertIsDisplayed()
  }

  @Test
  fun createInvitationButtonIsDisplayed() {
    setScreen()

    composeTestRule
        .onNodeWithTag(CaregiverParametersTestTags.CREATE_INVITATION_BUTTON)
        .assertIsDisplayed()
  }

  @Test
  fun enterInvitationButtonIsDisplayed() {
    setScreen()

    composeTestRule
        .onNodeWithTag(CaregiverParametersTestTags.ENTER_INVITATION_BUTTON)
        .assertIsDisplayed()
  }

  @Test
  fun invitationHintIsDisplayed() {
    setScreen()

    composeTestRule.onNodeWithTag(CaregiverParametersTestTags.INVITATION_HINT).assertIsDisplayed()
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
  fun previewShowsTheDefaultParameters() {
    composeTestRule.setContent { CaregiverParameterScreenPreview() }

    composeTestRule.onNodeWithTag(CaregiverParametersTestTags.TITLE).assertIsDisplayed()
  }

  // ---------- on Click ----------
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
  fun checkoutClickInvokesTheCheckoutCallback() {
    var checkoutClicks = 0
    setScreen(onCheckout = { checkoutClicks++ })

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    assertEquals(1, checkoutClicks)
  }

  // ---------------- Error message -------------
  @Test
  fun errorMessageIsClearedFromTheStateAfterTheToast() {
    val viewModel = CaregiverParameterViewModel().apply { setErrorMsg(ERROR_MESSAGE) }
    setScreen(viewModel = viewModel)

    composeTestRule.waitForIdle()

    assertNull(viewModel.uiState.value.errorMsg)
  }

  @Test
  fun noToastIsShownWhenThereIsNoError() {
    setScreen()

    composeTestRule.waitForIdle()

    assertEquals(0, ShadowToast.shownToastCount())
  }
}
