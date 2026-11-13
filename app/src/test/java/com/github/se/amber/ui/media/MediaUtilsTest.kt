// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.media

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
  ) {
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayRelatedPersonButton(person = person, onClick = onClick, testTag = tag)
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
}
