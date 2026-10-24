// this code was written with the aid of AI

package com.github.se.amber.ui.patient

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.ui.theme.AmberAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PatientUtilsTest {

  @get:Rule val composeTestRule = createComposeRule()

  // ---------- DisplayPatientTitle ----------

  @Test
  fun title_isDisplayedWithGivenText() {
    composeTestRule.setContent { AmberAppTheme { DisplayPatientTitle(title = "My Stories") } }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.TITLE)
        .assertIsDisplayed()
        .assertTextEquals("My Stories")
  }

  @Test
  fun title_isFoundByText() {
    composeTestRule.setContent { AmberAppTheme { DisplayPatientTitle(title = "Pictures") } }

    composeTestRule.onNodeWithText("Pictures").assertIsDisplayed()
  }

  @Test
  fun title_fillsParentWidth() {
    composeTestRule.setContent {
      AmberAppTheme { Box(Modifier.width(300.dp)) { DisplayPatientTitle(title = "Width") } }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TITLE).assertWidthIsEqualTo(300.dp)
  }

  @Test
  fun title_updatesWhenTextChanges() {
    val title = mutableStateOf("First")

    composeTestRule.setContent { AmberAppTheme { DisplayPatientTitle(title = title.value) } }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TITLE).assertTextEquals("First")

    title.value = "Second"
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TITLE).assertTextEquals("Second")
  }

  @Test
  fun title_usesCustomTestTag() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientTitle(title = "Custom", testTag = "custom_title") }
    }

    composeTestRule.onNodeWithTag("custom_title").assertIsDisplayed().assertTextEquals("Custom")
  }

  // ---------- DisplayPatientButton ----------

  @Test
  fun button_isDisplayed_withGivenText() {
    composeTestRule.setContent { AmberAppTheme { DisplayPatientButton(text = "Go") } }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON)
        .assertIsDisplayed()
        .assertTextEquals("Go")
  }

  @Test
  fun button_click_invokesCallbackOnce() {
    var clickCount = 0
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientButton(text = "Go", onClick = { clickCount++ }) }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).performClick()

    assertEquals(1, clickCount)
  }

  @Test
  fun button_usesCustomTestTag() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientButton(text = "Go", testTag = "custom_button") }
    }

    composeTestRule.onNodeWithTag("custom_button").assertIsDisplayed()
  }

  // ---------- DisplayPatientButtonWithIcon ----------

  @Test
  fun buttonWithIcon_isDisplayed_withGivenText() {
    composeTestRule.setContent { AmberAppTheme { DisplayPatientButtonWithIcon(text = "Add") } }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON)
        .assertIsDisplayed()
        .assertTextEquals("Add")
  }

  @Test
  fun buttonWithIcon_displaysIconWithDescription() {
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientButtonWithIcon(
            text = "Add",
            icon = Icons.Default.Add,
            description = "add icon",
        )
      }
    }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_ICON, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertContentDescriptionEquals("add icon")
  }

  @Test
  fun buttonWithIcon_click_invokesCallbackOnce() {
    var clickCount = 0
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientButtonWithIcon(text = "Add", onClick = { clickCount++ }) }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON).performClick()

    assertEquals(1, clickCount)
  }

  @Test
  fun buttonWithIcon_usesCustomTestTags() {
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientButtonWithIcon(
            text = "Add",
            testTag = "custom_button",
            iconTestTag = "custom_icon",
        )
      }
    }

    composeTestRule.onNodeWithTag("custom_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("custom_icon", useUnmergedTree = true).assertIsDisplayed()
  }

  // ---------- Several buttons in the same screen ----------

  @Test
  fun twoButtons_withDifferentTags_areClickedIndependently() {
    var firstClicks = 0
    var secondClicks = 0
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          DisplayPatientButton(text = "First", testTag = "first", onClick = { firstClicks++ })
          DisplayPatientButton(text = "Second", testTag = "second", onClick = { secondClicks++ })
        }
      }
    }

    composeTestRule.onNodeWithTag("second").performClick()

    assertEquals(0, firstClicks)
    assertEquals(1, secondClicks)
  }
}
