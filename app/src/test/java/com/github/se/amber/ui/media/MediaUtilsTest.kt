// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.media

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.se.amber.model.user.Person
import com.github.se.amber.ui.patient.PatientUtilsTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaUtilsTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val testTag = "related_person_test_tag"

  private fun setContent(
      person: Person,
      onClick: (String) -> Unit = {},
      tag: String? = testTag,
      modifier: Modifier = Modifier,
  ) {
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayRelatedPersonButton(
            modifier = modifier,
            person = person,
            onClick = onClick,
            testTag = tag,
        )
      }
    }
  }

  // ---------- DisplayRelatedPersonButton ----------

  @Test
  fun displaysNickname_whenNicknameIsSet() {
    setContent(Person(id = "1", firstName = "Alice", nickname = "Mamie"))

    composeTestRule.onNodeWithTag(testTag).assertTextEquals("Mamie")
  }

  @Test
  fun displaysFirstName_whenNicknameIsNull() {
    setContent(Person(id = "1", firstName = "Alice", nickname = null))

    composeTestRule.onNodeWithTag(testTag).assertTextEquals("Alice")
  }

  @Test
  fun displaysFirstName_whenNicknameIsBlank() {
    setContent(Person(id = "1", firstName = "Alice", nickname = "   "))

    composeTestRule.onNodeWithTag(testTag).assertTextEquals("Alice")
  }

  @Test
  fun click_callsOnClickWithPersonId() {
    var clickedId: String? = null
    setContent(Person(id = "person-42", firstName = "Alice"), onClick = { clickedId = it })

    composeTestRule.onNodeWithTag(testTag).performClick()

    assertEquals("person-42", clickedId)
  }

  @Test
  fun click_isNotTriggeredWithoutInteraction() {
    var clickedId: String? = null
    setContent(Person(id = "person-42", firstName = "Alice"), onClick = { clickedId = it })

    composeTestRule.waitForIdle()

    assertNull(clickedId)
  }

  @Test
  fun usesDefaultButtonTestTag_whenNoTestTagIsGiven() {
    setContent(Person(id = "1", firstName = "Alice"), tag = null)

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertTextEquals("Alice")
  }

  @Test
  fun button_withCustomModifier_isDisplayed() {
    setContent(Person(id = "1", firstName = "Alice"), modifier = Modifier.width(150.dp))

    composeTestRule.onNodeWithTag(testTag).assertIsDisplayed()
  }

  @Test
  fun button_withAllDefaults_usesDefaultTagAndClickDoesNothing() {
    composeTestRule.setContent { AmberAppTheme { DisplayRelatedPersonButton() } }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertExists().performClick()
  }

  @Test
  fun preview_showsTheButton() {
    composeTestRule.setContent { DisplayRelatedPersonButtonPreview() }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertExists()
  }

  // ---------- DisplayBox ----------

  @Test
  fun displayBox_displaysContent_andAppliesCustomModifier() {
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayBox(modifier = Modifier.testTag("box")) {
          Text("Content", modifier = Modifier.testTag("content"))
        }
      }
    }

    composeTestRule.onNodeWithTag("box").assertIsDisplayed()
    composeTestRule.onNodeWithTag("content").assertTextEquals("Content")
  }
}
