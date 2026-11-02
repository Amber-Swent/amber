/* Written by LaMaier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload.media

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.se.amber.ui.theme.AmberAppTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Compose tests for [DisplayWhoIsInvolved], which is state-hoisted: no ViewModel is needed. */
class PersonInvolvedTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val options = mapOf("p1" to "Marie", "p2" to "Anne")
  private val noOptions = emptyMap<String, String>()

  private val toggledIds = mutableListOf<String>()
  private var addPersonCount = 0

  private val personChips
    get() =
        composeTestRule.onAllNodes(
            SemanticsMatcher("person chip") { node ->
              node.config
                  .getOrNull(SemanticsProperties.TestTag)
                  ?.startsWith(DisplayWhoIsInvolvedTestTags.personInvolved("")) == true
            }
        )

  @Before
  fun setUp() {
    toggledIds.clear()
    addPersonCount = 0
  }

  private fun launch(
      options: Map<String, String> = emptyMap(),
      shownIds: Set<String> = emptySet(),
  ) {
    composeTestRule.setContent {
      AmberAppTheme(dynamicColor = false) {
        DisplayWhoIsInvolved(
            options = options,
            shownIds = shownIds,
            toggleShownId = { toggledIds.add(it) },
            onAddPerson = { addPersonCount++ },
        )
      }
    }
  }

  // ---------- no one in the care circle ----------

  @Test
  fun withoutOptionsTheSelectorContainer_andNoPersonMessage_IsDisplayed() {
    launch(noOptions)
    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.SELECT_PERSON_INVOLVED)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.NO_PERSON_INVOLVED)
        .assertIsDisplayed()
  }

  @Test
  fun withoutOptionsNoPersonChipIsDisplayed() {
    launch(noOptions)
    personChips.assertCountEquals(0)
  }

  @Test
  fun withoutOptionsTheAddPersonButtonIsDisplayed_andNotifiesOnce() {
    launch(noOptions)
    composeTestRule.onNodeWithTag(DisplayWhoIsInvolvedTestTags.ADD_PERSON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(DisplayWhoIsInvolvedTestTags.ADD_PERSON).performClick()
    assertEquals(1, addPersonCount)
  }

  // ---------- people in the care circle ----------

  @Test
  fun withOptionsTheSelectorContainerIsDisplayed() {
    launch(options)
    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.SELECT_PERSON_INVOLVED)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.NO_PERSON_INVOLVED)
        .assertDoesNotExist()
  }

  @Test
  fun withOptionsOneChipIsDisplayedPerPerson() {
    launch(options)
    personChips.assertCountEquals(options.size)
  }

  @Test
  fun withOptionsTheAddPersonButtonIsDisplayed() {
    launch(options)
    composeTestRule.onNodeWithTag(DisplayWhoIsInvolvedTestTags.ADD_PERSON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(DisplayWhoIsInvolvedTestTags.ADD_PERSON).performClick()
    assertEquals(1, addPersonCount)
  }

  // ---------- selection ----------

  @Test
  fun aShownPersonChipIsSelected() {
    launch(options, shownIds = setOf("p1"))
    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.personInvolved("p1"))
        .assertIsSelected()
  }

  @Test
  fun aPersonChipNotShownIsNotSelected() {
    launch(options, shownIds = setOf("p1"))
    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.personInvolved("p2"))
        .assertIsNotSelected()
  }

  @Test
  fun clickingAChipTogglesOnlyThatPerson() {
    launch(options)
    composeTestRule.onNodeWithTag(DisplayWhoIsInvolvedTestTags.personInvolved("p2")).performClick()
    assertEquals(listOf("p2"), toggledIds)
  }

  @Test
  fun clickingAChipDoesNotOpenTheAddPersonFlow() {
    launch(options)
    composeTestRule.onNodeWithTag(DisplayWhoIsInvolvedTestTags.personInvolved("p1")).performClick()
    assertEquals(0, addPersonCount)
  }
}
