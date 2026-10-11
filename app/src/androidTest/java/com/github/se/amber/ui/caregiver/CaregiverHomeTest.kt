package com.github.se.amber.ui.caregiver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.se.amber.ui.caregiver.CaregiverHomeTestTags.WELCOME_TEXT
import com.github.se.amber.ui.navigation.BottomNavigationTestTags
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CaregiverHomeTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var viewModel: HomeViewModel

  @Before
  fun setUp() {
    viewModel = HomeViewModel()
  }

  private fun setScreen(goBack: () -> Unit = {}) {
    composeTestRule.setContent { CaregiverHomeScreen(viewModel = viewModel, goBack = goBack) }
  }

  // UnExtract once Utils.kt is merge
  //  @Test
  //  fun title_isDisplayed() {
  //    setScreen()
  //    composeTestRule.onNodeWithTag(CaregiverHomeTestTags.TITLE).assertIsDisplayed()
  //  }

  @Test
  fun welcomeText_isDisplayed() {
    setScreen()
    composeTestRule.onNodeWithTag(WELCOME_TEXT).assertIsDisplayed()
  }

  @Test
  fun topNavigationMenu_isDisplayed() {
    setScreen()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.ROOT).assertIsDisplayed()
  }

  @Test
  fun bottomNavigationMenu_isDisplayed() {
    setScreen()
    composeTestRule
        .onNodeWithTag(BottomNavigationTestTags.BOTTOM_NAVIGATION_MENU)
        .assertIsDisplayed()
  }

  @Test
  fun screen_rendersWithoutNavigationActions() {
    composeTestRule.setContent {
      CaregiverHomeScreen(viewModel = viewModel, navigationActions = null)
    }
    composeTestRule.onNodeWithTag(WELCOME_TEXT).assertIsDisplayed()
  }
}
