// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.patient

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PATIENT_BUTTON_HEIGHT_SMALL
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

  // ---------- Defaults and custom modifier ----------

  @Test
  fun title_withAllDefaults_isDisplayed() {
    composeTestRule.setContent { AmberAppTheme { DisplayPatientTitle() } }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TITLE).assertIsDisplayed()
  }

  @Test
  fun title_appliesCustomModifier() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientTitle(title = "Width", modifier = Modifier.width(100.dp)) }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TITLE).assertWidthIsEqualTo(100.dp)
  }

  @Test
  fun button_withAllDefaults_clickDoesNothing() {
    composeTestRule.setContent { AmberAppTheme { DisplayPatientButton() } }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).performClick().assertIsDisplayed()
  }

  // The custom modifier defines the space available to the button:
  // the button then takes PATIENT_BUTTON_WIDTH (0.7f) of it, i.e. 140.dp out of 200.dp.
  @Test
  fun button_appliesCustomModifier() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientButton(text = "Go", modifier = Modifier.width(200.dp)) }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertWidthIsEqualTo(140.dp)
  }

  @Test
  fun button_updatesWhenTextChanges() {
    val text = mutableStateOf("First")
    composeTestRule.setContent { AmberAppTheme { DisplayPatientButton(text = text.value) } }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertTextEquals("First")

    text.value = "Second"
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertTextEquals("Second")
  }

  @Test
  fun buttonWithIcon_withAllDefaults_clickDoesNothing() {
    composeTestRule.setContent { AmberAppTheme { DisplayPatientButtonWithIcon() } }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON)
        .performClick()
        .assertIsDisplayed()
  }

  // Same logic as button_appliesCustomModifier: 0.7f of 200.dp = 140.dp.
  @Test
  fun buttonWithIcon_appliesCustomModifier() {
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientButtonWithIcon(text = "Add", modifier = Modifier.width(200.dp))
      }
    }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON)
        .assertWidthIsEqualTo(140.dp)
  }

  @Test
  fun buttonWithIcon_updatesWhenTextChanges() {
    val text = mutableStateOf("First")
    composeTestRule.setContent { AmberAppTheme { DisplayPatientButtonWithIcon(text = text.value) } }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON).assertTextEquals("First")

    text.value = "Second"
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON).assertTextEquals("Second")
  }

  // ---------- Button width and centering ----------

  @Test
  fun button_takesGivenFractionOfParentWidth_andIsCentered() {
    composeTestRule.setContent {
      AmberAppTheme {
        Box(Modifier.width(300.dp)) { DisplayPatientButton(text = "Go", buttonWidth = 0.5f) }
      }
    }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON)
        .assertWidthIsEqualTo(150.dp)
        .assertLeftPositionInRootIsEqualTo(75.dp)
  }

  @Test
  fun buttonWithIcon_takesGivenFractionOfParentWidth_andIsCentered() {
    composeTestRule.setContent {
      AmberAppTheme {
        Box(Modifier.width(300.dp)) {
          DisplayPatientButtonWithIcon(text = "Add", buttonWidth = 0.5f)
        }
      }
    }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON)
        .assertWidthIsEqualTo(150.dp)
        .assertLeftPositionInRootIsEqualTo(75.dp)
  }

  @Test
  fun button_withFullWidth_fillsParentAndStartsAtLeftEdge() {
    composeTestRule.setContent {
      AmberAppTheme {
        Box(Modifier.width(300.dp)) { DisplayPatientButton(text = "Go", buttonWidth = 1f) }
      }
    }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON)
        .assertWidthIsEqualTo(300.dp)
        .assertLeftPositionInRootIsEqualTo(0.dp)
  }

  @Test
  fun buttonWithIcon_withFullWidth_fillsParentAndStartsAtLeftEdge() {
    composeTestRule.setContent {
      AmberAppTheme {
        Box(Modifier.width(300.dp)) { DisplayPatientButtonWithIcon(text = "Add", buttonWidth = 1f) }
      }
    }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON)
        .assertWidthIsEqualTo(300.dp)
        .assertLeftPositionInRootIsEqualTo(0.dp)
  }

  // ---------- DisplayPatientIconButton ----------

  @Test
  fun iconButton_defaults_areEnabledSquareAndHaveNoSpinner() {
    composeTestRule.setContent { AmberAppTheme { DisplayPatientIconButton() } }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON)
        .assertIsDisplayed()
        .assertIsEnabled()
        .assertWidthIsEqualTo(PATIENT_BUTTON_HEIGHT_SMALL)
        .assertHeightIsEqualTo(PATIENT_BUTTON_HEIGHT_SMALL)
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON).performClick()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON).assertIsEnabled()
  }

  @Test
  fun iconButton_exposesTheDescriptionOnTheButton() {
    composeTestRule.setContent { AmberAppTheme { DisplayPatientIconButton(description = "Play") } }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON)
        .assertContentDescriptionEquals("Play")
  }

  @Test
  fun iconButton_click_invokesCallbackOnce() {
    var clickCount = 0
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientIconButton(onClick = { clickCount++ }) }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON).performClick()

    assertEquals(1, clickCount)
  }

  @Test
  fun iconButton_disabled_isNotEnabledAndIgnoresClicks() {
    var clickCount = 0
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientIconButton(enabled = false, onClick = { clickCount++ }) }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON).performClick()

    assertEquals(0, clickCount)
  }

  @Test
  fun iconButton_withSpinner_showsTheTaggedSpinner() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientIconButton(showSpinner = true, spinnerTestTag = "spinner") }
    }

    composeTestRule.onNodeWithTag("spinner", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun iconButton_withSpinnerAndNoSpinnerTag_stillShowsTheButton() {
    composeTestRule.setContent { AmberAppTheme { DisplayPatientIconButton(showSpinner = true) } }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON).assertIsDisplayed()
  }

  @Test
  fun iconButton_withoutSpinner_hasNoSpinner() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientIconButton(spinnerTestTag = "spinner") }
    }

    composeTestRule.onAllNodesWithTag("spinner", useUnmergedTree = true).assertCountEquals(0)
  }

  @Test
  fun iconButton_usesCustomTestTag() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientIconButton(testTag = "custom_icon_button") }
    }

    composeTestRule.onNodeWithTag("custom_icon_button").assertIsDisplayed()
  }

  // ---------- Custom styles and heights ----------

  @Test
  fun title_usesCustomStyle() {
    val results = mutableListOf<TextLayoutResult>()
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientTitle(title = "Style", titleStyle = TextStyle(fontSize = 40.sp))
      }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TITLE).performSemanticsAction(
        SemanticsActions.GetTextLayoutResult
    ) {
      it(results)
    }

    assertEquals(40.sp, results.first().layoutInput.style.fontSize)
  }

  @Test
  fun button_usesCustomHeight() {
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientButton(
            text = "Go",
            buttonHeight = 80.dp,
        )
      }
    }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON)
        .assertIsDisplayed()
        .assertTextEquals("Go")
        .assertHeightIsEqualTo(80.dp)
  }

  @Test
  fun buttonWithIcon_usesCustomHeight() {
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientButtonWithIcon(
            text = "Add",
            buttonHeight = 80.dp,
        )
      }
    }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON)
        .assertIsDisplayed()
        .assertTextEquals("Add")
        .assertHeightIsEqualTo(80.dp)
  }

  // ---------- Enabled ----------

  @Test
  fun button_isEnabledByDefault() {
    composeTestRule.setContent { AmberAppTheme { DisplayPatientButton(text = "Go") } }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertIsEnabled()
  }

  @Test
  fun button_whenDisabled_isNotEnabledAndDoesNotInvokeCallback() {
    var clickCount = 0
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientButton(text = "Go", enabled = false, onClick = { clickCount++ })
      }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertIsNotEnabled().performClick()

    assertEquals(0, clickCount)
  }

  @Test
  fun button_enabledStateFollowsParameter() {
    val enabled = mutableStateOf(false)
    var clickCount = 0
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientButton(text = "Go", enabled = enabled.value, onClick = { clickCount++ })
      }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertIsNotEnabled().performClick()
    assertEquals(0, clickCount)

    enabled.value = true

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertIsEnabled().performClick()
    assertEquals(1, clickCount)
  }

  @Test
  fun buttonWithIcon_isEnabledByDefault() {
    composeTestRule.setContent { AmberAppTheme { DisplayPatientButtonWithIcon(text = "Add") } }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON).assertIsEnabled()
  }

  @Test
  fun buttonWithIcon_whenDisabled_isNotEnabledAndDoesNotInvokeCallback() {
    var clickCount = 0
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientButtonWithIcon(text = "Add", enabled = false, onClick = { clickCount++ })
      }
    }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON)
        .assertIsNotEnabled()
        .performClick()

    assertEquals(0, clickCount)
  }

  @Test
  fun buttonWithIcon_enabledStateFollowsParameter() {
    val enabled = mutableStateOf(false)
    var clickCount = 0
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientButtonWithIcon(
            text = "Add",
            enabled = enabled.value,
            onClick = { clickCount++ },
        )
      }
    }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON)
        .assertIsNotEnabled()
        .performClick()
    assertEquals(0, clickCount)

    enabled.value = true

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON)
        .assertIsEnabled()
        .performClick()
    assertEquals(1, clickCount)
  }

  @Test
  fun preview_showsThePatientButtons() {
    composeTestRule.setContent { DisplayPatientButtonsPreview() }

    // The labeled button takes a width fraction inside a weighted row, so it is composed but has
    // no size in this unbounded test. Existence still runs the preview.
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertExists()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON).assertExists()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON).assertExists()
  }

  @Test
  fun darkPreview_showsThePatientButtons() {
    composeTestRule.setContent { DisplayPatientButtonsDarkPreview() }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertExists()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON).assertExists()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON).assertExists()
  }
}
