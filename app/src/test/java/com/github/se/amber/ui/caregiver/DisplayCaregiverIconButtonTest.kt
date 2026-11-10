// Written by Yuhan YANG, with assistance from Cursor.
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.caregiver

import android.content.Context
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.R
import com.github.se.amber.ui.theme.AmberAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DisplayCaregiverIconButtonTest {

  @get:Rule val composeTestRule = createComposeRule()

  /** Text of [id] in the current test language. */
  private fun string(id: Int): String =
      ApplicationProvider.getApplicationContext<Context>().getString(id)

  @Test
  fun defaults_areEnabled() {
    composeTestRule.setContent { AmberAppTheme { DisplayCaregiverIconButton() } }

    composeTestRule
        .onNodeWithTag(DisplayCaregiverIconButtonTestTags.BUTTON)
        .assertIsDisplayed()
        .assertIsEnabled()
  }

  @Test
  fun description_isReadFromTheButton() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayCaregiverIconButton(description = string(R.string.audioRecorderStop)) }
    }

    composeTestRule
        .onNodeWithTag(DisplayCaregiverIconButtonTestTags.BUTTON)
        .assertContentDescriptionEquals(string(R.string.audioRecorderStop))
  }

  @Test
  fun click_invokesTheActionOnce() {
    var clicks = 0
    composeTestRule.setContent {
      AmberAppTheme { DisplayCaregiverIconButton(onClick = { clicks++ }) }
    }

    composeTestRule.onNodeWithTag(DisplayCaregiverIconButtonTestTags.BUTTON).performClick()
    assertEquals(1, clicks)
  }

  @Test
  fun disabled_ignoresClicks() {
    var clicks = 0
    composeTestRule.setContent {
      AmberAppTheme { DisplayCaregiverIconButton(enabled = false, onClick = { clicks++ }) }
    }

    composeTestRule
        .onNodeWithTag(DisplayCaregiverIconButtonTestTags.BUTTON)
        .assertIsNotEnabled()
        .performClick()
    assertEquals(0, clicks)
  }

  @Test
  fun spinner_isShownWhenAsked() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayCaregiverIconButton(showSpinner = true, spinnerTestTag = "spinner") }
    }

    composeTestRule.onNodeWithTag("spinner", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun spinnerWithoutATag_stillShowsTheButton() {
    composeTestRule.setContent { AmberAppTheme { DisplayCaregiverIconButton(showSpinner = true) } }

    composeTestRule.onNodeWithTag(DisplayCaregiverIconButtonTestTags.BUTTON).assertIsDisplayed()
  }

  @Test
  fun spinner_isAbsentUnlessRequested() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayCaregiverIconButton(spinnerTestTag = "spinner") }
    }

    composeTestRule.onAllNodesWithTag("spinner", useUnmergedTree = true).assertCountEquals(0)
  }

  @Test
  fun customTestTag_replacesTheDefault() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayCaregiverIconButton(testTag = "custom_icon_button") }
    }

    composeTestRule.onNodeWithTag("custom_icon_button").assertIsDisplayed()
  }

  @Test
  fun preview_showsTheButton() {
    composeTestRule.setContent { DisplayCaregiverIconButtonPreview() }

    composeTestRule
        .onNodeWithTag(DisplayCaregiverIconButtonTestTags.BUTTON)
        .assertContentDescriptionEquals(string(R.string.caregiverIconButtonAdd))
  }
}
