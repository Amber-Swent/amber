/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code
 */
package com.github.se.amber.ui.navigation

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TopNavigationMenuTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** Fake callback: counts how many times the back action was triggered. */
  private class FakeBackHandler {
    var clickCount = 0
      private set

    fun onBackClick() {
      clickCount++
    }
  }

  private fun setMenu(
      action: TopNavigationAction = TopNavigationAction.CANCEL,
      handler: FakeBackHandler = FakeBackHandler(),
  ) {
    composeTestRule.setContent {
      TopNavigationMenu(action = action, onBackClick = handler::onBackClick)
    }
  }

  // ---------- Display ----------

  @Test
  fun rootIsDisplayed() {
    setMenu()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.ROOT).assertIsDisplayed()
  }

  @Test
  fun titleIsDisplayed() {
    setMenu()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.TITLE).assertIsDisplayed()
  }

  @Test
  fun backButtonIsDisplayed() {
    setMenu()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).assertIsDisplayed()
  }

  @Test
  fun dividerIsDisplayed() {
    setMenu()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.DIVIDER).assertIsDisplayed()
  }

  // ---------- Title text mapping ----------
  @Test
  fun cancelActionDisplaysCancelText() {
    setMenu(action = TopNavigationAction.CANCEL)
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.TITLE).assertTextEquals("Cancel")
  }

  @Test
  fun checkoutActionDisplaysCheckoutText() {
    setMenu(action = TopNavigationAction.CHECKOUT)
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.TITLE).assertTextEquals("Checkout")
  }

  // ---------- Back button state ----------
  @Test
  fun backButtonHasClickAction() {
    setMenu()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).assertHasClickAction()
  }

  // ---------- Callback behaviour ----------

  @Test
  fun backClickIsNotTriggeredWithoutInteraction() {
    val handler = FakeBackHandler()
    setMenu(handler = handler)

    assertEquals(0, handler.clickCount)
  }

  @Test
  fun clickingBackWithCancelActionInvokesCallbackOnce() {
    val handler = FakeBackHandler()
    setMenu(handler = handler)

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    assertEquals(1, handler.clickCount)
  }

  @Test
  fun clickingBackWithCheckoutActionInvokesCallbackOnce() {
    val handler = FakeBackHandler()
    setMenu(action = TopNavigationAction.CHECKOUT, handler = handler)

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    assertEquals(1, handler.clickCount)
  }
}
