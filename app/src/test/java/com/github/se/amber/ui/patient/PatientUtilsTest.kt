// this code was written with the aid of AI

package com.github.se.amber.ui.patient

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.ui.theme.AmberAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DisplayPatientTitleTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val titleTag = "patient_title"

  @Test
  fun title_isDisplayedWithGivenText() {
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientTitle(title = "My Stories", modifier = Modifier.testTag(titleTag))
      }
    }

    composeTestRule.onNodeWithTag(titleTag).assertIsDisplayed().assertTextEquals("My Stories")
  }

  @Test
  fun title_isFoundByText() {
    composeTestRule.setContent {
      AmberAppTheme { DisplayPatientTitle(title = "Pictures", modifier = Modifier) }
    }

    composeTestRule.onNodeWithText("Pictures").assertIsDisplayed()
  }

  @Test
  fun title_fillsParentWidth() {
    composeTestRule.setContent {
      AmberAppTheme {
        Box(Modifier.width(300.dp)) {
          DisplayPatientTitle(title = "Width", modifier = Modifier.testTag(titleTag))
        }
      }
    }

    composeTestRule.onNodeWithTag(titleTag).assertWidthIsEqualTo(300.dp)
  }

  @Test
  fun title_updatesWhenTextChanges() {
    val title = mutableStateOf("First")

    composeTestRule.setContent {
      AmberAppTheme {
        DisplayPatientTitle(title = title.value, modifier = Modifier.testTag(titleTag))
      }
    }

    composeTestRule.onNodeWithTag(titleTag).assertTextEquals("First")

    title.value = "Second"
    composeTestRule.onNodeWithTag(titleTag).assertTextEquals("Second")
  }
}
