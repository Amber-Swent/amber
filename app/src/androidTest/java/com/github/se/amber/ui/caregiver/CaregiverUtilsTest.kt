package com.github.se.amber.ui.caregiver

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.se.amber.ui.caregiver.DisplayBoxWithIconTextTestTags.ICON_BUTTON
import com.github.se.amber.ui.caregiver.DisplayTextFieldTestTags.ERROR_MESSAGE
import com.github.se.amber.ui.caregiver.DisplayTextFieldTestTags.MAX_CHAR_MESSAGE
import com.github.se.amber.ui.caregiver.DisplayTextFieldTestTags.MAX_LINES_MESSAGE
import com.github.se.amber.ui.caregiver.DisplayTextFieldTestTags.TEXT
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_HEIGHT_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_WIDTH
import com.github.se.amber.ui.theme.MAX_LINE_DEFAULT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// --------------------- TEST DisplayTextField -------------------

class DisplayTextFieldTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun setField(
      value: String = "",
      onValueChange: (String) -> Unit = {},
      placeholder: String? = null,
      emptyErrorMessage: String? = null,
      maxLines: Int = 1,
      maxChar: Int = MAX_LINE_DEFAULT,
  ) {
    composeTestRule.setContent {
      DisplayTextField(
          value = value,
          onValueChange = onValueChange,
          label = "Label",
          placeholder = placeholder,
          emptyErrorMessage = emptyErrorMessage,
          maxLines = maxLines,
          maxChar = maxChar,
      )
    }
  }

  private fun focusField() {
    composeTestRule.onNodeWithTag(TEXT).performClick()
  }

  @Test
  fun textField_isDisplayed() {
    setField()
    composeTestRule.onNodeWithTag(TEXT).assertIsDisplayed()
  }

  @Test
  fun label_isDisplayed() {
    setField()
    composeTestRule.onNodeWithText("Label").assertIsDisplayed()
  }

  @Test
  fun maxCharMessage_isDisplayed_whenValueReachesMaxChar() {
    setField(value = "abc", maxChar = 3)
    focusField()
    composeTestRule.onNodeWithTag(MAX_CHAR_MESSAGE).assertIsDisplayed()
  }

  @Test
  fun maxCharMessage_isNotDisplayed_whenValueIsBelowMaxChar() {
    setField(value = "ab", maxChar = 3)
    composeTestRule.onNodeWithTag(MAX_CHAR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun maxLinesMessage_isDisplayed_whenLineCountReachesMaxLines() {
    setField(value = "a\nb", maxLines = 2)
    focusField()
    composeTestRule.onNodeWithTag(MAX_LINES_MESSAGE).assertIsDisplayed()
  }

  @Test
  fun maxLinesMessage_isNotDisplayed_whenLineCountIsBelowMaxLines() {
    setField(value = "a", maxLines = 2)
    composeTestRule.onNodeWithTag(MAX_LINES_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun errorMessage_isDisplayed_whenValueIsBlankAndMessageProvided() {
    setField(value = "", emptyErrorMessage = "Required")
    focusField()
    composeTestRule.onNodeWithTag(ERROR_MESSAGE).assertIsDisplayed()
  }

  @Test
  fun errorMessage_isNotDisplayed_whenNoMessageProvided() {
    setField(value = "", emptyErrorMessage = null)
    composeTestRule.onNodeWithTag(ERROR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun errorMessage_isNotDisplayed_whenValueIsNotBlank() {
    setField(value = "abc", emptyErrorMessage = "Required")
    composeTestRule.onNodeWithTag(ERROR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun errorMessage_isNotDisplayed_beforeFieldIsFocused() {
    setField(value = "", emptyErrorMessage = "Required")
    composeTestRule.onNodeWithTag(ERROR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun maxCharMessage_isNotDisplayed_beforeFieldIsFocused() {
    setField(value = "abc", maxChar = 3)
    composeTestRule.onNodeWithTag(MAX_CHAR_MESSAGE).assertDoesNotExist()
  }
}

// --------------------- TEST DisplayCaregiverButton -------------------

class DisplayCaregiverButtonTest {

  @get:Rule val composeTestRule = createComposeRule()
  private val buttonTagFromAnotherFunction = "caregiverButtonTagFromAnotherFunction"
  private val iconDescription = "icon description"
  private val icon = Icons.Default.Add

  private fun setButton(
      onClick: () -> Unit = {},
      enabled: Boolean = true,
      withIcon: Boolean = false,
      height: Dp = CAREGIVER_BUTTON_HEIGHT_MEDIUM,
      width: Float = CAREGIVER_BUTTON_WIDTH,
  ) {
    composeTestRule.setContent {
      DisplayCaregiverButton(
          modifier = Modifier.testTag(buttonTagFromAnotherFunction),
          onClick = onClick,
          enabled = enabled,
          icon = if (withIcon) icon else null,
          description = if (withIcon) iconDescription else "",
          height = height,
          width = width,
      )
    }
  }

  // The button wraps its content in a merged semantics node, so children are looked up in the
  // unmerged tree.
  private fun textNode() =
      composeTestRule.onNodeWithTag(DisplayCaregiverButtonTestTags.TEXT, useUnmergedTree = true)

  private fun iconNode() =
      composeTestRule.onNodeWithTag(DisplayCaregiverButtonTestTags.ICON, useUnmergedTree = true)

  @Test
  fun button_isDisplayed() {
    setButton()
    composeTestRule.onNodeWithTag(buttonTagFromAnotherFunction).assertIsDisplayed()
  }

  @Test
  fun text_isDisplayed() {
    setButton()
    textNode().assertIsDisplayed()
  }

  @Test
  fun icon_isDisplayed_whenIconProvided() {
    setButton(withIcon = true)
    iconNode().assertIsDisplayed()
  }

  @Test
  fun icon_doesNotExist_whenNoIconProvided() {
    setButton(withIcon = false)
    iconNode().assertDoesNotExist()
  }

  @Test
  fun text_isDisplayed_whenIconProvided() {
    setButton(withIcon = true)
    textNode().assertIsDisplayed()
  }

  @Test
  fun button_isEnabled_byDefault() {
    setButton()
    composeTestRule.onNodeWithTag(buttonTagFromAnotherFunction).assertIsEnabled()
  }

  @Test
  fun button_isNotEnabled_whenDisabled() {
    setButton(enabled = false)
    composeTestRule.onNodeWithTag(buttonTagFromAnotherFunction).assertIsNotEnabled()
  }

  @Test
  fun click_callsOnClickOnce() {
    var clickCount = 0
    setButton(onClick = { clickCount++ })
    composeTestRule.onNodeWithTag(buttonTagFromAnotherFunction).performClick()
    assertEquals(1, clickCount)
  }

  @Test
  fun click_doesNotCallOnClick_whenDisabled() {
    var clickCount = 0
    setButton(onClick = { clickCount++ }, enabled = false)
    composeTestRule.onNodeWithTag(buttonTagFromAnotherFunction).performClick()
    assertEquals(0, clickCount)
  }

  @Test
  fun button_usesGivenHeight() {
    val customHeight = 77.dp
    setButton(height = customHeight)
    composeTestRule.onNodeWithTag(buttonTagFromAnotherFunction).assertHeightIsEqualTo(customHeight)
  }

  @Test
  fun iconWithoutDescription_isRejected() {
    assertThrows(IllegalArgumentException::class.java) {
      composeTestRule.setContent { DisplayCaregiverButton(icon = icon, description = "") }
    }
  }

  @Test
  fun descriptionWithoutIcon_isRejected() {
    assertThrows(IllegalArgumentException::class.java) {
      composeTestRule.setContent {
        DisplayCaregiverButton(
            icon = null,
            description = iconDescription,
        )
      }
    }
  }
}

// --------------------- TEST DisplayBoxWithIcon -------------------
class DisplayBoxWithIconTextTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val containerTag = "box_container"
  private val iconDescription = "Add icon"

  private fun setBox(onClick: () -> Unit = {}) {
    composeTestRule.setContent {
      DisplayBoxWithIconText(
          modifier = Modifier.testTag(containerTag),
          icon = Icons.Default.Add,
          contentDescription = iconDescription,
          label = "Box label",
          onClick = onClick,
      )
    }
  }

  @Test
  fun icon_isDisplayed() {
    setBox()
    composeTestRule.onNodeWithContentDescription(iconDescription).assertIsDisplayed()
  }

  @Test
  fun label_isDisplayed() {
    setBox()
    composeTestRule.onNodeWithText("Box label").assertIsDisplayed()
  }

  @Test
  fun button_isDisplayed() {
    setBox()
    composeTestRule.onNodeWithTag(ICON_BUTTON).assertIsDisplayed()
  }

  @Test
  fun onClick_isInvoked_whenIconButtonIsPressed() {
    var clicked = false
    setBox(onClick = { clicked = true })
    composeTestRule.onNodeWithTag(ICON_BUTTON).performClick()
    assertTrue(clicked)
  }

  @Test
  fun onClick_isInvokedOnce_whenIconButtonIsPressedOnce() {
    var callCount = 0
    setBox(onClick = { callCount++ })
    composeTestRule.onNodeWithTag(ICON_BUTTON).performClick()
    assertEquals(1, callCount)
  }
}
