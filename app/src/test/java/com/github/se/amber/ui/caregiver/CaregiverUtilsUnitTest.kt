// Written by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.caregiver

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.github.se.amber.ui.theme.AmberAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Covers the icon-above-text preview and the case where that flag is set without an icon. */
@RunWith(RobolectricTestRunner::class)
class CaregiverUtilsUnitTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun iconAboveTextPreviewShowsTheIconAndTheLabel() {
    composeTestRule.setContent { DisplayCaregiverButtonIconAboveTextPreview() }

    composeTestRule
        .onNodeWithTag(DisplayCaregiverButtonTestTags.ICON, useUnmergedTree = true)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(DisplayCaregiverButtonTestTags.TEXT, useUnmergedTree = true)
        .assertIsDisplayed()
  }

  @Test
  fun iconAboveTextIsIgnoredWhenThereIsNoIcon() {
    composeTestRule.setContent { AmberAppTheme { DisplayCaregiverButton(iconAboveText = true) } }

    composeTestRule
        .onAllNodesWithTag(DisplayCaregiverButtonTestTags.ICON, useUnmergedTree = true)
        .assertCountEquals(0)
    composeTestRule
        .onNodeWithTag(DisplayCaregiverButtonTestTags.TEXT, useUnmergedTree = true)
        .assertIsDisplayed()
  }
}
