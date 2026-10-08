// Written by YANG Yuhan, with assistance from
// Grok (xAI) via Cursor.
package com.github.se.amber.ui.patient

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.ui.navigation.BottomNavigationTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PatientHomeTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** Records home actions in the order they are invoked. */
  private class FakeActions {
    val calls = mutableListOf<String>()

    fun onSearchClick() {
      calls.add("search")
    }

    fun onCheckout() {
      calls.add("checkout")
    }

    fun onTabSelected(tab: Tab) {
      calls.add(tab.name)
    }
  }

  // ---------- Setup helpers ----------
  private fun setHome(actions: FakeActions = FakeActions()) {
    composeTestRule.setContent {
      PatientHome(
          onSearchClick = actions::onSearchClick,
          onCheckout = actions::onCheckout,
          onTabSelected = actions::onTabSelected,
      )
    }
  }

  private fun setHomeWithDefaultCallbacks() {
    composeTestRule.setContent { PatientHome() }
  }

  // ---------- Node / check helpers ----------
  private fun node(tag: String): SemanticsNodeInteraction = composeTestRule.onNodeWithTag(tag)

  private fun checkIsDisplayed(tag: String) {
    node(tag).assertIsDisplayed()
  }

  // ---------- PatientHome: display ----------
  @Test
  fun titleIsDisplayed() {
    setHome()
    node(PatientHomeTestTags.TITLE).assertTextEquals("You are on Home")
  }

  @Test
  fun searchButtonIsDisplayed() {
    setHome()
    checkIsDisplayed(PatientHomeTestTags.SEARCH_BUTTON)
  }

  @Test
  fun searchButtonShowsSearchLabel() {
    setHome()
    node(PatientHomeTestTags.SEARCH_BUTTON).assertTextContains("Search")
  }

  @Test
  fun pictureIsDisplayed() {
    setHome()
    node(PatientHomeTestTags.PICTURE).assertTextContains("One random Picture")
  }

  @Test
  fun topBarIsDisplayed() {
    setHome()
    checkIsDisplayed(TopNavigationMenuTestTags.ROOT)
  }

  @Test
  fun topBarShowsCheckoutAction() {
    setHome()
    node(TopNavigationMenuTestTags.TITLE).assertTextEquals("Checkout")
  }

  @Test
  fun homeTabIsSelected() {
    setHome()
    node(BottomNavigationTestTags.tabTag(PatientTab.Home)).assertIsSelected()
  }

  @Test
  fun picturesTabIsDisplayed() {
    setHome()
    checkIsDisplayed(BottomNavigationTestTags.tabTag(PatientTab.SeePictures))
  }

  // ---------- PatientHome: accessibility ----------
  @Test
  fun searchButtonHasClickAction() {
    setHome()
    node(PatientHomeTestTags.SEARCH_BUTTON).assertHasClickAction()
  }

  // ---------- PatientHome: callbacks ----------
  @Test
  fun clickingSearchInvokesOnlySearchCallback() {
    val actions = FakeActions()
    setHome(actions)
    node(PatientHomeTestTags.SEARCH_BUTTON).performClick()
    assertEquals(listOf("search"), actions.calls)
  }

  @Test
  fun clickingTopBarBackInvokesOnlyCheckoutCallback() {
    val actions = FakeActions()
    setHome(actions)
    node(TopNavigationMenuTestTags.BACK_BUTTON).performClick()
    assertEquals(listOf("checkout"), actions.calls)
  }

  @Test
  fun clickingPicturesTabInvokesOnlyThatTab() {
    val actions = FakeActions()
    setHome(actions)
    node(BottomNavigationTestTags.tabTag(PatientTab.SeePictures)).performClick()
    assertEquals(listOf("Pictures"), actions.calls)
  }

  @Test
  fun clickingHomeTabInvokesOnlyHome() {
    val actions = FakeActions()
    setHome(actions)
    node(BottomNavigationTestTags.tabTag(PatientTab.Home)).performClick()
    assertEquals(listOf("Home"), actions.calls)
  }

  // ---------- PatientHome: default callbacks ----------
  @Test
  fun defaultSearchCallbackIgnoresClick() {
    setHomeWithDefaultCallbacks()
    node(PatientHomeTestTags.SEARCH_BUTTON).performClick()
    checkIsDisplayed(PatientHomeTestTags.TITLE)
  }

  @Test
  fun defaultCheckoutCallbackIgnoresClick() {
    setHomeWithDefaultCallbacks()
    node(TopNavigationMenuTestTags.BACK_BUTTON).performClick()
    checkIsDisplayed(PatientHomeTestTags.TITLE)
  }

  @Test
  fun defaultHomeTabCallbackIgnoresClick() {
    setHomeWithDefaultCallbacks()
    node(BottomNavigationTestTags.tabTag(PatientTab.Home)).performClick()
    checkIsDisplayed(PatientHomeTestTags.TITLE)
  }
}
