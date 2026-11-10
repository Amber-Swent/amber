// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by camomille299, with assistance from Claude (Anthropic) via claude.ai.

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
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PATIENT_BUTTON_HEIGHT_SMALL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

  // ---------- DisplayPatientButton (with and without icon) ----------

  @Test
  fun buttons_areDisplayedWithGivenText_andUseTheirDefaultTestTag() {
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          DisplayPatientButton(text = "Go")
          DisplayPatientButton(text = "Add", icon = Icons.Default.Add)
        }
      }
    }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON)
        .assertIsDisplayed()
        .assertTextEquals("Go")
    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON)
        .assertIsDisplayed()
        .assertTextEquals("Add")
    composeTestRule.onAllNodesWithTag(PatientUtilsTestTags.BUTTON).assertCountEquals(1)
    composeTestRule.onAllNodesWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON).assertCountEquals(1)
  }

  @Test
  fun buttons_useCustomTestTags_andAreClickedIndependently() {
    var plainClicks = 0
    var iconClicks = 0
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          DisplayPatientButton(text = "Go", testTag = "plain", onClick = { plainClicks++ })
          DisplayPatientButton(
              text = "Add",
              icon = Icons.Default.Add,
              testTag = "with_icon",
              iconTestTag = "custom_icon",
              onClick = { iconClicks++ },
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("plain").assertIsDisplayed()
    composeTestRule.onNodeWithTag("with_icon").assertIsDisplayed()
    composeTestRule.onNodeWithTag("custom_icon", useUnmergedTree = true).assertIsDisplayed()
    // The custom tags replace the default ones.
    composeTestRule.onAllNodesWithTag(PatientUtilsTestTags.BUTTON).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON).assertCountEquals(0)

    composeTestRule.onNodeWithTag("with_icon").performClick()
    assertEquals(0, plainClicks)
    assertEquals(1, iconClicks)

    composeTestRule.onNodeWithTag("plain").performClick()
    assertEquals(1, plainClicks)
    assertEquals(1, iconClicks)
  }

  @Test
  fun button_showsIconWithDescription_onlyWhenAnIconIsGiven() {
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          DisplayPatientButton(text = "Go", iconTestTag = "plain_icon")
          DisplayPatientButton(
              text = "Add",
              icon = Icons.Default.Add,
              description = "add icon",
          )
        }
      }
    }

    composeTestRule.onAllNodesWithTag("plain_icon", useUnmergedTree = true).assertCountEquals(0)
    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_ICON, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertContentDescriptionEquals("add icon")
  }

  @Test
  fun buttons_withAllDefaults_clickDoesNothing() {
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          DisplayPatientButton()
          DisplayPatientButton(icon = Icons.Default.Add)
        }
      }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).performClick().assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON)
        .performClick()
        .assertIsDisplayed()
  }

  // The custom modifier defines the space available to the button:
  // the button then takes PATIENT_BUTTON_WIDTH (0.7f) of it, i.e. 140.dp out of 200.dp.
  @Test
  fun buttons_applyCustomModifier() {
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          DisplayPatientButton(text = "Go", testTag = "plain", modifier = Modifier.width(200.dp))
          DisplayPatientButton(
              text = "Add",
              icon = Icons.Default.Add,
              testTag = "with_icon",
              modifier = Modifier.width(200.dp),
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("plain").assertWidthIsEqualTo(140.dp)
    composeTestRule.onNodeWithTag("with_icon").assertWidthIsEqualTo(140.dp)
  }

  @Test
  fun buttons_updateWhenTextChanges() {
    val text = mutableStateOf("First")
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          DisplayPatientButton(text = text.value)
          DisplayPatientButton(text = text.value, icon = Icons.Default.Add)
        }
      }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertTextEquals("First")
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON).assertTextEquals("First")

    text.value = "Second"
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertTextEquals("Second")
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON).assertTextEquals("Second")
  }

  // ---------- Button width and centering (fillWidth = true) ----------

  @Test
  fun buttons_takeGivenFractionOfParentWidth_andAreCentered() {
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          Box(Modifier.width(300.dp)) {
            DisplayPatientButton(text = "Go", buttonWidth = 0.5f, testTag = "half")
          }
          Box(Modifier.width(300.dp)) {
            DisplayPatientButton(
                text = "Add",
                icon = Icons.Default.Add,
                buttonWidth = 0.5f,
                testTag = "half_icon",
            )
          }
          Box(Modifier.width(300.dp)) {
            DisplayPatientButton(text = "Go", buttonWidth = 1f, testTag = "full")
          }
          Box(Modifier.width(300.dp)) {
            DisplayPatientButton(
                text = "Add",
                icon = Icons.Default.Add,
                buttonWidth = 1f,
                testTag = "full_icon",
            )
          }
        }
      }
    }

    composeTestRule
        .onNodeWithTag("half")
        .assertWidthIsEqualTo(150.dp)
        .assertLeftPositionInRootIsEqualTo(75.dp)
    composeTestRule
        .onNodeWithTag("half_icon")
        .assertWidthIsEqualTo(150.dp)
        .assertLeftPositionInRootIsEqualTo(75.dp)
    composeTestRule
        .onNodeWithTag("full")
        .assertWidthIsEqualTo(300.dp)
        .assertLeftPositionInRootIsEqualTo(0.dp)
    composeTestRule
        .onNodeWithTag("full_icon")
        .assertWidthIsEqualTo(300.dp)
        .assertLeftPositionInRootIsEqualTo(0.dp)
  }

  // ---------- Button wrapping its content (fillWidth = false) ----------

  @Test
  fun buttons_withoutFillWidth_wrapTheirContent_ignoreButtonWidth_andRespectExplicitWidth() {
    var clickCount = 0
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          Box(Modifier.width(300.dp)) {
            DisplayPatientButton(text = "Go", fillWidth = false, testTag = "plain")
          }
          Box(Modifier.width(300.dp)) {
            DisplayPatientButton(
                text = "Add",
                icon = Icons.Default.Add,
                description = "add icon",
                fillWidth = false,
                testTag = "with_icon",
            )
          }
          Box(Modifier.width(300.dp)) {
            DisplayPatientButton(
                text = "Go",
                buttonWidth = 1f,
                fillWidth = false,
                testTag = "ignored_width",
            )
          }
          DisplayPatientButton(
              text = "Go",
              fillWidth = false,
              modifier = Modifier.width(200.dp),
              testTag = "explicit",
              onClick = { clickCount++ },
          )
        }
      }
    }

    val plain = composeTestRule.onNodeWithTag("plain")
    assertTrue(plain.getUnclippedBoundsInRoot().width < 300.dp)
    plain.assertLeftPositionInRootIsEqualTo(0.dp)

    val withIcon = composeTestRule.onNodeWithTag("with_icon")
    assertTrue(withIcon.getUnclippedBoundsInRoot().width < 300.dp)
    withIcon.assertLeftPositionInRootIsEqualTo(0.dp).assertTextEquals("Add")
    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_ICON, useUnmergedTree = true)
        .assertContentDescriptionEquals("add icon")

    val ignored = composeTestRule.onNodeWithTag("ignored_width")
    assertTrue(ignored.getUnclippedBoundsInRoot().width < 300.dp)

    composeTestRule.onNodeWithTag("explicit").assertWidthIsEqualTo(200.dp).performClick()
    assertEquals(1, clickCount)
  }

  // ---------- Custom height ----------

  @Test
  fun buttons_useCustomHeight() {
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          DisplayPatientButton(text = "Go", buttonHeight = 80.dp)
          DisplayPatientButton(text = "Add", icon = Icons.Default.Add, buttonHeight = 80.dp)
        }
      }
    }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON)
        .assertIsDisplayed()
        .assertTextEquals("Go")
        .assertHeightIsEqualTo(80.dp)
    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON)
        .assertIsDisplayed()
        .assertTextEquals("Add")
        .assertHeightIsEqualTo(80.dp)
  }

  // ---------- Enabled ----------

  @Test
  fun buttons_areEnabledByDefault() {
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          DisplayPatientButton(text = "Go")
          DisplayPatientButton(text = "Add", icon = Icons.Default.Add)
        }
      }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertIsEnabled()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON).assertIsEnabled()
  }

  @Test
  fun buttons_enabledStateFollowsParameter() {
    val enabled = mutableStateOf(false)
    var plainClicks = 0
    var iconClicks = 0
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          DisplayPatientButton(
              text = "Go",
              enabled = enabled.value,
              onClick = { plainClicks++ },
          )
          DisplayPatientButton(
              text = "Add",
              icon = Icons.Default.Add,
              enabled = enabled.value,
              onClick = { iconClicks++ },
          )
        }
      }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertIsNotEnabled().performClick()
    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON)
        .assertIsNotEnabled()
        .performClick()
    assertEquals(0, plainClicks)
    assertEquals(0, iconClicks)

    enabled.value = true

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertIsEnabled().performClick()
    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON)
        .assertIsEnabled()
        .performClick()
    assertEquals(1, plainClicks)
    assertEquals(1, iconClicks)
  }

  // ---------- DisplayPatientCircularIconButton ----------

  @Test
  fun iconButton_defaults_areEnabledSquareAndHaveNoSpinner() {
    composeTestRule.setContent { AmberAppTheme { DisplayPatientCircularIconButton() } }

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
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientCircularIconButton(description = "Play") }
    }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON)
        .assertContentDescriptionEquals("Play")
  }

  @Test
  fun iconButton_click_invokesCallbackOnce() {
    var clickCount = 0
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientCircularIconButton(onClick = { clickCount++ }) }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON).performClick()

    assertEquals(1, clickCount)
  }

  @Test
  fun iconButton_disabled_isNotEnabledAndIgnoresClicks() {
    var clickCount = 0
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientCircularIconButton(enabled = false, onClick = { clickCount++ })
      }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON).performClick()

    assertEquals(0, clickCount)
  }

  @Test
  fun iconButton_withSpinner_showsTheTaggedSpinner() {
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientCircularIconButton(showSpinner = true, spinnerTestTag = "spinner")
      }
    }

    composeTestRule.onNodeWithTag("spinner", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun iconButton_withSpinnerAndNoSpinnerTag_stillShowsTheButton() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientCircularIconButton(showSpinner = true) }
    }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.ICON_BUTTON).assertIsDisplayed()
  }

  @Test
  fun iconButton_withoutSpinner_hasNoSpinner() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientCircularIconButton(spinnerTestTag = "spinner") }
    }

    composeTestRule.onAllNodesWithTag("spinner", useUnmergedTree = true).assertCountEquals(0)
  }

  @Test
  fun iconButton_usesCustomTestTag() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientCircularIconButton(testTag = "custom_icon_button") }
    }

    composeTestRule.onNodeWithTag("custom_icon_button").assertIsDisplayed()
  }

  // ---------- Previews ----------

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
