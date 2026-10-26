// Written by Alaabrine, with assistance from Cursor.
package com.github.se.amber.ui.patient.parameters

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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

  private companion object {
    const val ERROR_MESSAGE = "error"
  }

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
    val viewModel = PatientParameterViewModel().apply { setErrorMsg(ERROR_MESSAGE) }
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
