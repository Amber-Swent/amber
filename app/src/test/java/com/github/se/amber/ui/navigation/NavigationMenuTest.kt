// Written by Yuhan YANG, with assistance from Cursor.
package com.github.se.amber.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Covers the default arguments and click callbacks of the shared navigation menus. */
@RunWith(RobolectricTestRunner::class)
class NavigationMenuTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun topMenuDefaultsShowCancel() {
    composeTestRule.setContent { TopNavigationMenu() }

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.TITLE).assertTextEquals(CANCEL_LABEL)
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.DIVIDER).assertIsDisplayed()
  }

  @Test
  fun topMenuBackClickInvokesCallback() {
    var clicks = 0
    composeTestRule.setContent { TopNavigationMenu(onBackClick = { clicks++ }) }

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    assertEquals(1, clicks)
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
  }

  @Test
  fun bottomMenuTabClickInvokesCallback() {
    var selected: Tab? = null
    composeTestRule.setContent { BottomNavigationMenu(onTabSelected = { selected = it }) }

    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.tabTag(CaregiverTab.Upload))
        .performClick()

    assertEquals(CaregiverTab.Upload, selected)
  }

  @Test
  fun bottomMenuPreviewRenders() {
    composeTestRule.setContent { BottomNavigationMenuPreview() }

    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU)
        .assertIsDisplayed()
  }
}
