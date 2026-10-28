// Written by Yuhan YANG, with assistance from Cursor.
package com.github.se.amber.ui.caregiver

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_HEIGHT_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_WIDTH
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaregiverButtonTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun button_isDisplayed_withGivenText() {
    composeTestRule.setContent { AmberAppTheme { CaregiverButton(text = "Create") } }

    composeTestRule
        .onNodeWithTag(CaregiverButtonTestTags.BUTTON)
        .assertIsDisplayed()
        .assertTextEquals("Create")
  }

  @Test
  fun button_click_invokesCallbackOnce() {
    var clickCount = 0
    composeTestRule.setContent {
      AmberAppTheme { CaregiverButton(text = "Create", onClick = { clickCount++ }) }
    }

    composeTestRule.onNodeWithTag(CaregiverButtonTestTags.BUTTON).performClick()

    assertEquals(1, clickCount)
  }

  @Test
  fun button_usesCustomTestTag() {
    composeTestRule.setContent {
      AmberAppTheme { CaregiverButton(text = "Create", testTag = "custom_button") }
    }

    composeTestRule.onNodeWithTag("custom_button").assertIsDisplayed()
  }

  @Test
  fun button_withAllDefaults_clickDoesNothing() {
    composeTestRule.setContent { AmberAppTheme { CaregiverButton() } }

    composeTestRule.onNodeWithTag(CaregiverButtonTestTags.BUTTON).performClick().assertIsDisplayed()
  }

  @Test
  fun button_usesDefaultCaregiverWidth() {
    composeTestRule.setContent {
      AmberAppTheme { CaregiverButton(text = "Create", modifier = Modifier.width(200.dp)) }
    }

    composeTestRule
        .onNodeWithTag(CaregiverButtonTestTags.BUTTON)
        .assertWidthIsEqualTo(200.dp * CAREGIVER_BUTTON_WIDTH)
  }

  @Test
  fun button_usesDefaultCaregiverHeight() {
    composeTestRule.setContent { AmberAppTheme { CaregiverButton(text = "Create") } }

    composeTestRule
        .onNodeWithTag(CaregiverButtonTestTags.BUTTON)
        .assertHeightIsEqualTo(CAREGIVER_BUTTON_HEIGHT_MEDIUM)
  }

  @Test
  fun button_isEnabledByDefault() {
    composeTestRule.setContent { AmberAppTheme { CaregiverButton(text = "Create") } }

    composeTestRule.onNodeWithTag(CaregiverButtonTestTags.BUTTON).assertIsEnabled()
  }

  @Test
  fun button_whenDisabled_isNotEnabledAndDoesNotInvokeCallback() {
    var clickCount = 0
    composeTestRule.setContent {
      AmberAppTheme {
        CaregiverButton(text = "Create", enabled = false, onClick = { clickCount++ })
      }
    }

    composeTestRule
        .onNodeWithTag(CaregiverButtonTestTags.BUTTON)
        .assertIsNotEnabled()
        .performClick()

    assertEquals(0, clickCount)
  }

  @Test
  fun twoButtons_withDifferentTags_areClickedIndependently() {
    var firstClicks = 0
    var secondClicks = 0
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          CaregiverButton(text = "First", testTag = "first", onClick = { firstClicks++ })
          CaregiverButton(text = "Second", testTag = "second", onClick = { secondClicks++ })
        }
      }
    }

    composeTestRule.onNodeWithTag("second").performClick()

    assertEquals(0, firstClicks)
    assertEquals(1, secondClicks)
  }

  @Test
  fun button_updatesWhenTextChanges() {
    val text = mutableStateOf("Create")
    composeTestRule.setContent { AmberAppTheme { CaregiverButton(text = text.value) } }

    composeTestRule.onNodeWithTag(CaregiverButtonTestTags.BUTTON).assertTextEquals("Create")

    text.value = "Creation..."
    composeTestRule.onNodeWithTag(CaregiverButtonTestTags.BUTTON).assertTextEquals("Creation...")
  }
}
