package com.github.se.amber.ui.onboarding

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.se.amber.ui.onboarding.DividerTestTags.HORIZONTAL_DIVIDER_LEFT
import com.github.se.amber.ui.onboarding.DividerTestTags.HORIZONTAL_DIVIDER_RIGHT
import com.github.se.amber.ui.onboarding.DividerTestTags.TEXT
import org.junit.Rule
import org.junit.Test

class DividerTest {
  @get:Rule val composeTestRule = createComposeRule()

  private fun setContent(customText: String? = null) {
    if (customText != null) {
      composeTestRule.setContent { Divider(text = customText) }
    } else {
      composeTestRule.setContent { Divider() }
    }
  }

  // ------- isDisplay -----
  @Test
  fun horizontalDivider_isDisplay() {
    setContent()
    composeTestRule.onNodeWithTag(HORIZONTAL_DIVIDER_LEFT).assertIsDisplayed()

    composeTestRule.onNodeWithTag(HORIZONTAL_DIVIDER_RIGHT).assertIsDisplayed()
  }

  @Test
  fun text_isDisplay() {
    setContent()
    composeTestRule.onNodeWithTag(TEXT).assertIsDisplayed()
  }

  // ---- take parameter into account
  @Test
  fun divider_appliesModifier() {
    composeTestRule.setContent { Divider(modifier = Modifier.testTag(LOCAL_TEST_TAG)) }

    composeTestRule.onNodeWithTag(LOCAL_TEST_TAG).assertIsDisplayed()
  }

  companion object {
    const val LOCAL_TEST_TAG = "utils_test_local_test_tag"
  }

  @Test
  fun divider_rendersWithCustomText() {
    val customText = "and"
    setContent(customText = customText)

    composeTestRule.onNodeWithTag(TEXT).assertExists().assertTextEquals(customText)
  }
}
