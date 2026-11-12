// Written by Lou-Anne Maier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.utils

import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tests [DisplayInformation]: its icon and its lines are displayed. */
@RunWith(RobolectricTestRunner::class)
class InformationUtilsTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private fun launch() {
    composeTestRule.setContent {
      DisplayInformation(
          modifier = Modifier.testTag(TAG),
          contentDescription = ICON,
          sentences = listOf(FIRST, SECOND),
      )
    }
  }

  @Test
  fun displaysTheInformation() {
    launch()
    composeTestRule.onNodeWithTag(TAG).assertIsDisplayed()
  }

  @Test
  fun displaysTheIcon() {
    launch()
    composeTestRule.onNodeWithContentDescription(ICON).assertIsDisplayed()
  }

  @Test
  fun displaysTheFirstLine() {
    launch()
    composeTestRule.onNodeWithText(FIRST).assertIsDisplayed()
  }

  @Test
  fun displaysTheSecondLine() {
    launch()
    composeTestRule.onNodeWithText(SECOND).assertIsDisplayed()
  }

  private companion object {
    const val TAG = "information"
    const val ICON = "icon description"
    const val FIRST = "first line"
    const val SECOND = "second line"
  }
}
