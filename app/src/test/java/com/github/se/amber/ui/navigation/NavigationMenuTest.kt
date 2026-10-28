// Written by Yuhan YANG, with assistance from Cursor.
package com.github.se.amber.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Covers the default arguments of the shared navigation menus. */
@RunWith(RobolectricTestRunner::class)
class NavigationMenuTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun topMenuDefaultsShowCancelAndIgnoreBackClick() {
    composeTestRule.setContent { TopNavigationMenu() }

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.TITLE).assertTextEquals("Cancel")
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.DIVIDER).assertIsDisplayed()
  }

  @Test
  fun bottomMenuDefaultsShowCaregiverTabs() {
    composeTestRule.setContent { BottomNavigationMenu() }

    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU)
        .assertIsDisplayed()
    CaregiverTab.caregiverTabs.forEach { tab ->
      composeTestRule.onNodeWithTag(BottomNavigationMenuTestTags.tabTag(tab)).assertIsDisplayed()
    }
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.tabTag(CaregiverTab.Home))
        .performClick()
  }
}
