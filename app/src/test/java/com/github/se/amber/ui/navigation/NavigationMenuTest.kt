// Written by Yuhan YANG, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.navigation

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Covers the default arguments and click callbacks of the shared navigation menus. */
@RunWith(RobolectricTestRunner::class)
class NavigationMenuTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun topMenuDefaultsShowCancel() {
    composeTestRule.setContent { TopNavigationMenu() }

    val cancel =
        ApplicationProvider.getApplicationContext<Context>().getString(R.string.navigationCancel)
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.TITLE).assertTextEquals(cancel)
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
  @Config(qualifiers = "fr")
  fun bottomMenuTabsAreDescribedInFrench() {
    composeTestRule.setContent { BottomNavigationMenu() }

    listOf("Accueil", "Ajouter", "Paramètres").forEach { label ->
      composeTestRule.onNodeWithContentDescription(label, useUnmergedTree = true).assertExists()
    }
  }

  @Test
  @Config(qualifiers = "fr")
  fun patientBottomMenuTabsAreDescribedInFrench() {
    composeTestRule.setContent {
      BottomNavigationMenu(selectedTab = PatientTab.Home, tabs = PatientTab.patientTabs)
    }

    listOf("Accueil", "Médias", "Paramètres").forEach { label ->
      composeTestRule.onNodeWithContentDescription(label, useUnmergedTree = true).assertExists()
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
