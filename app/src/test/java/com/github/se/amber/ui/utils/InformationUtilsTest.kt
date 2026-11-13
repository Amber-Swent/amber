// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.utils

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
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

  // ---------- layout and parameters ----------

  @Test
  fun displaysTheSecondLineUnderTheFirst() {
    launch()
    val first = composeTestRule.onNodeWithText(FIRST).getUnclippedBoundsInRoot()
    val second = composeTestRule.onNodeWithText(SECOND).getUnclippedBoundsInRoot()
    assertTrue(second.top >= first.bottom)
  }

  @Test
  fun withDefaultParametersDisplaysTheInformation() {
    composeTestRule.setContent { DisplayInformation(modifier = Modifier.testTag(TAG)) }
    composeTestRule.onNodeWithTag(TAG).assertIsDisplayed()
  }

  @Test
  fun theIconHasTheRequestedSize() {
    composeTestRule.setContent {
      DisplayInformation(contentDescription = ICON, sentences = listOf(FIRST), size = ICON_SIZE)
    }
    composeTestRule.onNodeWithContentDescription(ICON).assertWidthIsEqualTo(ICON_SIZE)
  }

  @Test
  fun displaysACustomIcon() {
    composeTestRule.setContent {
      DisplayInformation(
          icon = Icons.Default.Warning,
          contentDescription = ICON,
          sentences = listOf(FIRST),
      )
    }
    composeTestRule.onNodeWithContentDescription(ICON).assertIsDisplayed()
  }

  // ---------- preview ----------

  @Test
  fun preview_displaysEverything() {
    composeTestRule.setContent { DisplayInformationPreview() }
    composeTestRule.onNodeWithContentDescription(PREVIEW_INFORMATION_ICON).assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription(PREVIEW_ERROR_ICON).assertIsDisplayed()
    composeTestRule.onNodeWithText(PREVIEW_FIRST_LINE).assertIsDisplayed()
    composeTestRule.onNodeWithText(PREVIEW_SECOND_LINE).assertIsDisplayed()
    composeTestRule.onNodeWithText(PREVIEW_ERROR_MESSAGE).assertIsDisplayed()
  }

  private companion object {
    val ICON_SIZE = 30.dp
    const val PREVIEW_INFORMATION_ICON = "Information"
    const val PREVIEW_ERROR_ICON = "Error"
    const val PREVIEW_FIRST_LINE = "First line of the hint."
    const val PREVIEW_SECOND_LINE = "Second line of the hint."
    const val PREVIEW_ERROR_MESSAGE = "An error message."

    const val TAG = "information"
    const val ICON = "icon description"
    const val FIRST = "first line"
    const val SECOND = "second line"
  }
}
