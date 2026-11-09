// Written by Alaabrine, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.patient.parameters

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.theme.AmberAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class PatientParameterScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** Text of [id] in the current test language, with [args] for its placeholders. */
  private fun string(id: Int, vararg args: Any): String =
      ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

  private fun setScreen(
      viewModel: PatientParameterViewModel = PatientParameterViewModel(),
      onTabSelected: (Tab) -> Unit = {},
      onSignOutClick: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      AmberAppTheme {
        PatientParameterScreen(
            viewModel = viewModel,
            onTabSelected = onTabSelected,
            onSignOutClick = onSignOutClick,
        )
      }
    }
  }

  @Test
  fun titleIsDisplayed() {
    setScreen()
    composeTestRule.onNodeWithTag(PatientParameterTestTags.TITLE).assertIsDisplayed()
  }

  @Test
  fun signOutButtonIsDisplayed() {
    setScreen()
    composeTestRule.onNodeWithTag(PatientParameterTestTags.SIGN_OUT_BUTTON).assertIsDisplayed()
  }

  @Test
  fun bottomBarIsDisplayed() {
    setScreen()
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU)
        .assertIsDisplayed()
  }

  @Test
  fun signOutClickInvokesTheCallback() {
    var clicks = 0
    setScreen(onSignOutClick = { clicks++ })

    composeTestRule.onNodeWithTag(PatientParameterTestTags.SIGN_OUT_BUTTON).performClick()

    assertEquals(1, clicks)
  }

  @Test
  fun errorMessageIsClearedFromTheStateAfterTheToast() {
    val viewModel = PatientParameterViewModel().apply { setErrorMsg(R.string.signOutError) }
    setScreen(viewModel = viewModel)

    composeTestRule.waitForIdle()

    assertNull(viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun errorToastShowsTheTranslatedMessage() {
    val viewModel = PatientParameterViewModel().apply { setErrorMsg(R.string.signOutError) }
    setScreen(viewModel = viewModel)

    composeTestRule.waitForIdle()

    assertEquals(string(R.string.signOutError), ShadowToast.getTextOfLatestToast())
  }

  @Test
  fun titleAndSignOutButtonShowTheirText() {
    setScreen()

    composeTestRule
        .onNodeWithTag(PatientParameterTestTags.TITLE)
        .assertTextEquals(string(R.string.patientParameterTitle))
    composeTestRule
        .onNodeWithTag(PatientParameterTestTags.SIGN_OUT_BUTTON)
        .assertTextEquals(string(R.string.patientParameterSignOut))
  }

  @Test
  fun noToastIsShownWhenThereIsNoError() {
    setScreen()

    composeTestRule.waitForIdle()

    assertEquals(0, ShadowToast.shownToastCount())
  }

  @Test
  fun screenUsesDefaultCallbacksWhenOmitted() {
    val viewModel = PatientParameterViewModel()
    composeTestRule.setContent { AmberAppTheme { PatientParameterScreen(viewModel = viewModel) } }

    composeTestRule.onNodeWithTag(PatientParameterTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(PatientParameterTestTags.SIGN_OUT_BUTTON).performClick()
    composeTestRule.waitForIdle()

    // Default onSignOutClick is a no-op; screen remains displayed.
    composeTestRule.onNodeWithTag(PatientParameterTestTags.TITLE).assertIsDisplayed()
  }

  @Test
  fun previewRendersWithoutCrashing() {
    composeTestRule.setContent { PatientParameterScreenPreview() }

    composeTestRule.onNodeWithTag(PatientParameterTestTags.TITLE).assertIsDisplayed()
  }
}
