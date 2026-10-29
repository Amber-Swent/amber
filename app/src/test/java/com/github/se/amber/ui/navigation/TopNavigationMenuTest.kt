// Written by Alaabrine, with assistance from Cursor.
package com.github.se.amber.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.se.amber.ui.theme.AmberAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TopNavigationMenuRobolectricTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun defaultActionIsCancelWhenOmitted() {
    composeTestRule.setContent { AmberAppTheme { TopNavigationMenu() } }

    composeTestRule
        .onNodeWithTag(TopNavigationMenuTestTags.TITLE)
        .assertIsDisplayed()
        .assertTextEquals("Cancel")
  }

  @Test
  fun defaultOnBackClickIsNoOp() {
    composeTestRule.setContent { AmberAppTheme { TopNavigationMenu() } }

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU).assertIsDisplayed()
  }

  @Test
  fun checkoutActionIsDisplayedWhenProvided() {
    var backClicks = 0
    composeTestRule.setContent {
      AmberAppTheme {
        TopNavigationMenu(
            action = TopNavigationAction.CHECKOUT,
            onBackClick = { backClicks++ },
        )
      }
    }

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.TITLE).assertTextEquals("Checkout")
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    assertEquals(1, backClicks)
  }

  @Test
  fun previewRendersWithoutCrashing() {
    composeTestRule.setContent { TopNavigationMenuPreview() }

    composeTestRule
        .onNodeWithTag(TopNavigationMenuTestTags.TITLE)
        .assertIsDisplayed()
        .assertTextEquals("Cancel")
  }
}
