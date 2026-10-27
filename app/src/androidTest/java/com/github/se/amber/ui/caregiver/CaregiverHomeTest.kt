/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.se.amber.ui.caregiver.CaregiverHomeTestTags.WELCOME_TEXT
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CaregiverHomeScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var viewModel: CaregiverHomeViewModel

  @Before
  fun setUp() {
    viewModel = CaregiverHomeViewModel()
  }

  private fun setScreen(signOut: () -> Unit = {}) {
    composeTestRule.setContent { CaregiverHomeScreen(viewModel = viewModel) }
  }

  @Test
  fun title_isDisplayed() {
    setScreen()
    composeTestRule.onNodeWithTag(CaregiverHomeTestTags.TITLE).assertIsDisplayed()
  }

  @Test
  fun welcomeText_isDisplayed() {
    setScreen()
    composeTestRule.onNodeWithTag(WELCOME_TEXT).assertIsDisplayed()
  }

  @Test
  fun topNavigationMenu_isDisplayed() {
    setScreen()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU).assertIsDisplayed()
  }

  @Test
  fun bottomNavigationMenu_isDisplayed() {
    setScreen()
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU)
        .assertIsDisplayed()
  }

  @Test
  fun launchedEffect_whenErrorMsgIsSet_clearErrorMsgIsCalled() {
    composeTestRule.setContent { CaregiverHomeScreen(viewModel = viewModel) }

    viewModel.setErrorMsg("Test error")
    composeTestRule.mainClock.advanceTimeByFrame()
    assertTrue(viewModel.uiState.value.errorMsg == null)
  }

  @Test
  fun launchedEffect_whenSignedOut_onSignedOutCallbackInvoked() {
    val signedOutCalled = mutableListOf<Boolean>()

    composeTestRule.setContent {
      CaregiverHomeScreen(
          viewModel = viewModel,
          onSignedOut = { signedOutCalled.add(true) },
      )
    }

    viewModel.setSignedOut(true)
    composeTestRule.mainClock.advanceTimeByFrame()

    assertTrue(signedOutCalled.size == 1)
  }
}
