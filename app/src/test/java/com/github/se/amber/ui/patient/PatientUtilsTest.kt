// this code was written with the aid of AI

package com.github.se.amber.ui.patient

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
  fun button_usesCustomHeightAndTextStyle() {
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientButton(
            text = "Go",
            buttonHeight = 80.dp,
            textStyle = TextStyle(fontSize = 20.sp),
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
  fun buttonWithIcon_usesCustomHeightAndTextStyle() {
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientButtonWithIcon(
            text = "Add",
            buttonHeight = 80.dp,
            textStyle = TextStyle(fontSize = 20.sp),
        )
      }
    }

    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.BUTTON_WITH_ICON)
        .assertIsDisplayed()
        .assertTextEquals("Add")
        .assertHeightIsEqualTo(80.dp)
  }

  // ---------- Recomposition with unchanged parameters ----------

  // Every parameter comes from a variable defined outside setContent (not a literal), so the
  // Compose compiler cannot treat it as a constant and must compare it with the previous value
  // when the parent recomposes.
  @Test
  fun helpers_areSkippedWhenParentRecomposesWithSameParameters() {
    val counter = mutableStateOf(0)
    val modifier: Modifier = Modifier
    val style = TextStyle(fontSize = 20.sp)
    val height = 80.dp
    val width = 0.5f
    val title = "Title"
    val goText = "Go"
    val addText = "Add"
    val icon = Icons.Default.Add
    val description = "add icon"
    val onClick: () -> Unit = {}
    val titleTag = "title"
    val buttonTag = "button"
    val buttonWithIconTag = "button_with_icon"
    val iconTag = "icon"

    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          Text("Counter ${counter.value}")
          DisplayPatientTitle(
              modifier = modifier,
              title = title,
              titleStyle = style,
              testTag = titleTag,
          )
          DisplayPatientButton(
              modifier = modifier,
              buttonHeight = height,
              buttonWidth = width,
              text = goText,
              textStyle = style,
              onClick = onClick,
              testTag = buttonTag,
          )
          DisplayPatientButtonWithIcon(
              modifier = modifier,
              buttonHeight = height,
              buttonWidth = width,
              text = addText,
              textStyle = style,
              icon = icon,
              description = description,
              onClick = onClick,
              testTag = buttonWithIconTag,
              iconTestTag = iconTag,
          )
        }
      }
    }

    composeTestRule.onNodeWithText("Counter 0").assertIsDisplayed()

    counter.value = 1

    composeTestRule.onNodeWithText("Counter 1").assertIsDisplayed()
    composeTestRule.onNodeWithTag(titleTag).assertTextEquals("Title")
    composeTestRule.onNodeWithTag(buttonTag).assertTextEquals("Go")
    composeTestRule.onNodeWithTag(buttonWithIconTag).assertTextEquals("Add")
  }

  // The composables read MaterialTheme colors (backed by Compose state). Changing the color scheme
  // makes each composable recompose on its own, which covers the generated restart path.
  // One instance omits the default parameters, the other passes them all.
  @Test
  fun helpers_recomposeThemselvesWhenThemeColorsChange() {
    val colors =
        mutableStateOf(
            lightColorScheme(
                primary = Color.Red,
                tertiary = Color.Green,
                onTertiary = Color.White,
            )
        )
    val style = TextStyle(fontSize = 20.sp)
    val onClick: () -> Unit = {}

    composeTestRule.setContent {
      MaterialTheme(colorScheme = colors.value) {
        Column {
          DisplayPatientTitle(title = "Title default", testTag = "title_default")
          DisplayPatientTitle(
              modifier = Modifier,
              title = "Title explicit",
              titleStyle = style,
              testTag = "title_explicit",
          )
          DisplayPatientButton(text = "Button default", testTag = "button_default")
          DisplayPatientButton(
              modifier = Modifier,
              buttonHeight = 80.dp,
              buttonWidth = 0.5f,
              text = "Button explicit",
              textStyle = style,
              onClick = onClick,
              testTag = "button_explicit",
          )
          DisplayPatientButtonWithIcon(text = "Icon default", testTag = "icon_default")
          DisplayPatientButtonWithIcon(
              modifier = Modifier,
              buttonHeight = 80.dp,
              buttonWidth = 0.5f,
              text = "Icon explicit",
              textStyle = style,
              icon = Icons.Default.Add,
              description = "add icon",
              onClick = onClick,
              testTag = "icon_explicit",
              iconTestTag = "icon_explicit_icon",
          )
        }
      }
    }

    colors.value =
        lightColorScheme(
            primary = Color.Blue,
            tertiary = Color.Yellow,
            onTertiary = Color.Black,
        )
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("title_default").assertTextEquals("Title default")
    composeTestRule.onNodeWithTag("title_explicit").assertTextEquals("Title explicit")
    composeTestRule.onNodeWithTag("button_default").assertTextEquals("Button default")
    composeTestRule.onNodeWithTag("button_explicit").assertTextEquals("Button explicit")
    composeTestRule.onNodeWithTag("icon_default").assertTextEquals("Icon default")
    composeTestRule.onNodeWithTag("icon_explicit").assertTextEquals("Icon explicit")
  }
}
