// Written by Alaabrine, with assistance from Cursor.
package com.github.se.amber.ui.caregiver.upload

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.se.amber.ui.theme.AmberAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tests [DisplayButton] defaults and the explicit icon, label, and click action. */
@RunWith(RobolectricTestRunner::class)
class DisplayButtonTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun displayButtonUsesAllDefaults() {
    composeTestRule.setContent { AmberAppTheme { DisplayButton() } }

    composeTestRule.waitForIdle()
  }

  @Test
  fun displayButtonUsesDefaultOnClickWhenOmitted() {
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayButton(
            modifier = Modifier.testTag(BUTTON_TAG),
            text = SAMPLE_TEXT,
            description = SAMPLE_DESCRIPTION,
        )
      }
    }

    composeTestRule.onNodeWithTag(BUTTON_TAG).assertIsDisplayed().performClick()
  }

  @Test
  fun displayButtonShowsLabelIconAndInvokesClick() {
    var clicks = 0
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayButton(
            modifier = Modifier.testTag(BUTTON_TAG),
            icon = Icons.Outlined.Description,
            text = SAMPLE_TEXT,
            description = SAMPLE_DESCRIPTION,
            onClick = { clicks++ },
        )
      }
    }

    composeTestRule
        .onNodeWithTag(BUTTON_TAG)
        .assertIsDisplayed()
        .assertTextContains(SAMPLE_TEXT)
        .assertContentDescriptionEquals(SAMPLE_DESCRIPTION)
        .assertHasClickAction()
        .performClick()

    assertEquals(1, clicks)
  }

  private companion object {
    const val BUTTON_TAG = "display_button"
    const val SAMPLE_TEXT = "Sample label"
    const val SAMPLE_DESCRIPTION = "Sample description"
  }
}
