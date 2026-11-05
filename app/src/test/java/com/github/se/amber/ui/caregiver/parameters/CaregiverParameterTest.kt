/* Written by Yuhan YANG, with assistance from
 * Cursor.
 * Modify by Lou-Anne Maier, with assistance from
 * Claude Code.
 * Modified by Alaabrine, with assistance from Cursor.
 */
package com.github.se.amber.ui.caregiver.parameters

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.theme.AmberAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
      onTabSelected: (Tab) -> Unit = {},
      onCreateInvitationClick: () -> Unit = {},
      onEnterInvitationClick: () -> Unit = {},
      onSignOutClick: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      AmberAppTheme {
        CaregiverParameterScreen(
            viewModel = viewModel,
            onTabSelected = onTabSelected,
            onCreateInvitationClick = onCreateInvitationClick,
            onEnterInvitationClick = onEnterInvitationClick,
            onSignOutClick = onSignOutClick,
        )
      }
    }
  }

  @Test
  fun titleIsDisplayed() {
    setScreen()
    composeTestRule.onNodeWithTag(CaregiverParametersTestTags.TITLE).assertIsDisplayed()
  }

  @Test
  fun createCareCircleButtonIsDisplayed() {
    setScreen()

    composeTestRule
        .onNodeWithTag(CaregiverParametersTestTags.CREATE_CARE_CIRCLE_BUTTON)
        .assertIsDisplayed()
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
  fun signOutButtonIsDisplayed() {
    setScreen()

    composeTestRule.onNodeWithTag(CaregiverParametersTestTags.SIGN_OUT_BUTTON).assertIsDisplayed()
  }

  @Test
  fun invitationHintIsDisplayed() {
    setScreen()

    composeTestRule
        .onNodeWithTag(CaregiverParametersTestTags.INVITATION_HINT)
        .assertIsDisplayed()
        .assertTextEquals(INVITATION_HINT_LABEL)
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
  fun signOutClickInvokesTheCallback() {
    var signOutClicks = 0
    setScreen(onSignOutClick = { signOutClicks++ })

    composeTestRule.onNodeWithTag(CaregiverParametersTestTags.SIGN_OUT_BUTTON).performClick()

    assertEquals(1, signOutClicks)
  }

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

  @Test
  fun screenUsesDefaultCallbacksWhenOmitted() {
    val viewModel = CaregiverParameterViewModel()
    composeTestRule.setContent { AmberAppTheme { CaregiverParameterScreen(viewModel = viewModel) } }

    composeTestRule.onNodeWithTag(CaregiverParametersTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(CaregiverParametersTestTags.SIGN_OUT_BUTTON).performClick()
    composeTestRule.waitForIdle()

    // Default onSignOutClick is a no-op; screen remains displayed.
    composeTestRule.onNodeWithTag(CaregiverParametersTestTags.TITLE).assertIsDisplayed()
  }

  @Test
  fun screenPreviewRendersWithoutCrashing() {
    composeTestRule.setContent { CaregiverParameterScreenPreview() }

    composeTestRule.onNodeWithTag(CaregiverParametersTestTags.TITLE).assertIsDisplayed()
  }

  @Test
  fun titleReadsParameters() {
    setScreen()

    composeTestRule
        .onNodeWithTag(CaregiverParametersTestTags.TITLE)
        .assertTextEquals(PARAMETERS_TITLE)
  }

  @Test
  fun createCareCircleButtonShowsItsLabel() {
    setScreen()

    composeTestRule.onNodeWithText(CREATE_CARE_CIRCLE_LABEL).assertIsDisplayed()
  }

  @Test
  fun createInvitationButtonShowsItsLabel() {
    setScreen()

    composeTestRule.onNodeWithText(CREATE_INVITATION_LABEL).assertIsDisplayed()
  }

  @Test
  fun enterInvitationButtonShowsItsLabel() {
    setScreen()

    composeTestRule.onNodeWithText(ENTER_INVITATION_LABEL).assertIsDisplayed()
  }

  @Test
  fun signOutButtonShowsItsLabel() {
    setScreen()

    composeTestRule.onNodeWithText(SIGN_OUT_LABEL).assertIsDisplayed()
  }

  @Test
  fun invitationHintPointsAtTheCreateInvitationButton() {
    assertTrue(INVITATION_HINT_LABEL.contains(CREATE_INVITATION_LABEL))
    assertTrue(INVITATION_HINT_LABEL.contains("send them the code"))
  }

  @Test
  fun invitationHintDoesNotAskAboutASecondPerson() {
    assertFalse(INVITATION_HINT_LABEL.contains("second person"))
    assertFalse(INVITATION_HINT_LABEL.contains("a new code from a caregiver"))
  }

  @Test
  fun actionsAreOrderedCreateCircleThenInvitationsThenSignOut() {
    setScreen()

    val createCircleTop = topOf(CaregiverParametersTestTags.CREATE_CARE_CIRCLE_BUTTON)
    val createInvitationTop = topOf(CaregiverParametersTestTags.CREATE_INVITATION_BUTTON)
    val enterInvitationTop = topOf(CaregiverParametersTestTags.ENTER_INVITATION_BUTTON)
    val signOutTop = topOf(CaregiverParametersTestTags.SIGN_OUT_BUTTON)
    val hintTop = topOf(CaregiverParametersTestTags.INVITATION_HINT)

    assertTrue(createCircleTop < createInvitationTop)
    assertTrue(createInvitationTop < enterInvitationTop)
    assertTrue(enterInvitationTop < signOutTop)
    assertTrue(signOutTop < hintTop)
  }

  @Test
  fun createCareCircleClickDoesNotTriggerTheOtherActions() {
    var createInvitationClicks = 0
    var enterInvitationClicks = 0
    var signOutClicks = 0
    setScreen(
        onCreateInvitationClick = { createInvitationClicks++ },
        onEnterInvitationClick = { enterInvitationClicks++ },
        onSignOutClick = { signOutClicks++ },
    )

    composeTestRule
        .onNodeWithTag(CaregiverParametersTestTags.CREATE_CARE_CIRCLE_BUTTON)
        .performClick()

    assertEquals(0, createInvitationClicks)
    assertEquals(0, enterInvitationClicks)
    assertEquals(0, signOutClicks)
  }

  @Test
  fun screenPreviewShowsTheCreateCareCircleButton() {
    composeTestRule.setContent { CaregiverParameterScreenPreview() }

    composeTestRule
        .onNodeWithTag(CaregiverParametersTestTags.CREATE_CARE_CIRCLE_BUTTON)
        .assertIsDisplayed()
    composeTestRule.onNodeWithText(CREATE_CARE_CIRCLE_LABEL).assertIsDisplayed()
  }

  @Test
  fun screenPreviewShowsTheInvitationHint() {
    composeTestRule.setContent { CaregiverParameterScreenPreview() }

    composeTestRule
        .onNodeWithTag(CaregiverParametersTestTags.INVITATION_HINT)
        .assertIsDisplayed()
        .assertTextEquals(INVITATION_HINT_LABEL)
  }

  private fun topOf(tag: String) = composeTestRule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top
}
