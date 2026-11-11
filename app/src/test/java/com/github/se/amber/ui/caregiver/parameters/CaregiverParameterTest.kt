// Written by Yuhan YANG, with assistance from Cursor.
// Modify by Lou-Anne Maier, with assistance from Claude Code.
// Modified by Alaabrine, with assistance from Cursor.
// Modified by MeiliV, with assistance from OpenAI Codex.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.parameters

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
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
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** Tests the caregiver parameters screen: displayed elements, click callbacks and error toast. */
@RunWith(RobolectricTestRunner::class)
class CaregiverParameterScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** Text of [id] in the current test language, with [args] for its placeholders. */
  private fun string(id: Int, vararg args: Any): String =
      ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

  private val invitationHint: String
    get() =
        string(
            R.string.caregiverParameterInvitationHint,
            string(R.string.caregiverParameterCreateInvitation),
        )

  private fun setScreen(
      viewModel: CaregiverParameterViewModel = CaregiverParameterViewModel(),
      onTabSelected: (Tab) -> Unit = {},
      onCreateInvitationClick: () -> Unit = {},
      onEnterInvitationClick: () -> Unit = {},
      onSignOutClick: () -> Unit = {},
      onDeleteAccountClick: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      AmberAppTheme {
        CaregiverParameterScreen(
            viewModel = viewModel,
            onTabSelected = onTabSelected,
            onCreateInvitationClick = onCreateInvitationClick,
            onEnterInvitationClick = onEnterInvitationClick,
            onSignOutClick = onSignOutClick,
            onDeleteAccountClick = onDeleteAccountClick,
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
        .assertTextEquals(invitationHint)
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
  fun deleteAccountClickInvokesTheCallback() {
    var deleteAccountClicks = 0
    setScreen(onDeleteAccountClick = { deleteAccountClicks++ })

    composeTestRule
        .onNodeWithTag(CaregiverParametersTestTags.DELETE_ACCOUNT_BUTTON)
        .assertIsDisplayed()
        .performClick()

    assertEquals(1, deleteAccountClicks)
  }

  @Test
  fun errorMessageIsClearedFromTheStateAfterTheToast() {
    val viewModel = CaregiverParameterViewModel().apply { setErrorMsg(R.string.signOutError) }
    setScreen(viewModel = viewModel)

    composeTestRule.waitForIdle()

    assertNull(viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun errorToastShowsTheTranslatedMessage() {
    val viewModel = CaregiverParameterViewModel().apply { setErrorMsg(R.string.signOutError) }
    setScreen(viewModel = viewModel)

    composeTestRule.waitForIdle()

    assertEquals(string(R.string.signOutError), ShadowToast.getTextOfLatestToast())
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
        .assertTextEquals(string(R.string.caregiverParameterTitle))
  }

  @Test
  fun createCareCircleButtonShowsItsLabel() {
    setScreen()

    composeTestRule
        .onNodeWithText(string(R.string.caregiverParameterCreateCareCircle))
        .assertIsDisplayed()
  }

  @Test
  fun createInvitationButtonShowsItsLabel() {
    setScreen()

    composeTestRule
        .onNodeWithText(string(R.string.caregiverParameterCreateInvitation))
        .assertIsDisplayed()
  }

  @Test
  fun enterInvitationButtonShowsItsLabel() {
    setScreen()

    composeTestRule
        .onNodeWithText(string(R.string.caregiverParameterEnterInvitation))
        .assertIsDisplayed()
  }

  @Test
  fun signOutButtonShowsItsLabel() {
    setScreen()

    composeTestRule.onNodeWithText(string(R.string.caregiverParameterSignOut)).assertIsDisplayed()
  }

  // Checks the English wording itself, so it is pinned to English
  @Test
  @Config(qualifiers = "en")
  fun invitationHintPointsAtTheCreateInvitationButton() {
    assertTrue(invitationHint.contains(string(R.string.caregiverParameterCreateInvitation)))
    assertTrue(invitationHint.contains("send them the code"))
  }

  @Test
  @Config(qualifiers = "en")
  fun invitationHintDoesNotAskAboutASecondPerson() {
    assertFalse(invitationHint.contains("second person"))
    assertFalse(invitationHint.contains("a new code from a caregiver"))
  }

  @Test
  @Config(qualifiers = "fr")
  fun screenIsShownInFrench() {
    setScreen()

    composeTestRule.onNodeWithTag(CaregiverParametersTestTags.TITLE).assertTextEquals("Paramètres")
    composeTestRule.onNodeWithText("Créer un nouveau cercle de soins").assertExists()
    composeTestRule.onNodeWithText("Saisir une invitation").assertExists()
    composeTestRule.onNodeWithText("Se déconnecter").assertExists()
    composeTestRule
        .onNodeWithTag(CaregiverParametersTestTags.INVITATION_HINT)
        .assertTextEquals(
            "Pour ajouter quelqu'un à votre cercle, appuyez sur «\u00A0Créer une invitation\u00A0» et " +
                "envoyez-lui le code."
        )
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
    composeTestRule
        .onNodeWithText(string(R.string.caregiverParameterCreateCareCircle))
        .assertIsDisplayed()
  }

  @Test
  fun screenPreviewShowsTheInvitationHint() {
    composeTestRule.setContent { CaregiverParameterScreenPreview() }

    composeTestRule
        .onNodeWithTag(CaregiverParametersTestTags.INVITATION_HINT)
        .assertIsDisplayed()
        .assertTextEquals(invitationHint)
  }

  private fun topOf(tag: String) = composeTestRule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top
}
