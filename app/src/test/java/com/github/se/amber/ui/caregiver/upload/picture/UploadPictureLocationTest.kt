// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.caregiver.upload.picture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.se.amber.model.location.GeoLocation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric tests for the "Location - optional" helpers of the Upload Picture screen, rendered on
 * their own: the screen tests cover them through the ViewModel, these cover what the helpers do
 * with their default values and with any list of suggestions.
 */
@RunWith(RobolectricTestRunner::class)
class UploadPictureLocationTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val lausanne = GeoLocation(lat = 46.52, lng = 6.63, placeName = "Lausanne, Switzerland")
  private val unnamed = GeoLocation(lat = 45.0, lng = -73.0, placeName = null)

  private val searchField =
      hasSetTextAction() and hasAnyAncestor(hasTestTag(UploadPictureLocationTestTags.SEARCH_FIELD))

  @Test
  fun withDefaultsOnlyTheTitleAndTheSearchFieldAreShown() {
    composeTestRule.setContent { DisplayLocation() }

    composeTestRule.onNodeWithText("Location - optional").assertIsDisplayed()
    composeTestRule.onNode(searchField).assertIsDisplayed()
    composeTestRule
        .onAllNodesWithTag(UploadPictureLocationTestTags.SAVED_PLACE)
        .assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(UploadPictureLocationTestTags.SUGGESTION).assertCountEquals(0)
  }

  @Test
  fun withDefaultCallbacksTappingAndTypingDoNothing() {
    composeTestRule.setContent {
      DisplayLocation(
          placeOptions = mapOf("home" to "Home"),
          uiState = UploadPictureUIState(placeQuery = "Lau", placeSuggestions = listOf(lausanne)),
      )
    }

    composeTestRule.onNodeWithTag(UploadPictureLocationTestTags.SAVED_PLACE).performClick()
    composeTestRule.onNodeWithTag(UploadPictureLocationTestTags.SUGGESTION).performClick()
    composeTestRule.onNode(searchField).performTextInput("x")

    composeTestRule.onNodeWithText("Home").assertIsDisplayed()
  }

  @Test
  fun eachSuggestionIsShownAndPickedWithItsOwnPlace() {
    val picked = mutableListOf<GeoLocation>()
    composeTestRule.setContent {
      DisplayLocation(
          uiState =
              UploadPictureUIState(placeQuery = "La", placeSuggestions = listOf(lausanne, unnamed)),
          onSuggestionClick = { picked += it },
      )
    }

    val suggestions = composeTestRule.onAllNodesWithTag(UploadPictureLocationTestTags.SUGGESTION)
    suggestions.assertCountEquals(2)
    suggestions[1].performClick()
    suggestions[0].performClick()

    assertEquals(listOf(unnamed, lausanne), picked)
  }

  @Test
  fun aPlaceWithoutANameIsShownWithEmptyText() {
    composeTestRule.setContent { DisplayPlaceSuggestion(place = unnamed) }

    composeTestRule
        .onNodeWithTag(UploadPictureLocationTestTags.SUGGESTION)
        .assertIsDisplayed()
        .performClick() // the default callback does nothing
  }

  @Test
  fun aSuggestionWithDefaultsIsStillShown() {
    composeTestRule.setContent { DisplayPlaceSuggestion() }

    composeTestRule.onNodeWithTag(UploadPictureLocationTestTags.SUGGESTION).assertIsDisplayed()
  }

  @Test
  fun aHintWithDefaultsShowsNothingVisible() {
    composeTestRule.setContent { Box(Modifier.testTag(HINT_HOLDER)) { DisplayLocationHint() } }

    composeTestRule.onNodeWithTag(HINT_HOLDER).assertExists()
  }

  @Test
  fun aHintShowsItsTextUnderItsTag() {
    composeTestRule.setContent { DisplayLocationHint(text = "Searching…", tag = HINT_HOLDER) }

    composeTestRule.onNodeWithTag(HINT_HOLDER).assertIsDisplayed()
    composeTestRule.onNodeWithText("Searching…").assertIsDisplayed()
  }

  @Test
  fun redrawingTheScreenKeepsTheSectionAndFollowsANewState() {
    var unrelated by mutableIntStateOf(0)
    var state by
        mutableStateOf(UploadPictureUIState(placeQuery = "La", placeSuggestions = listOf(lausanne)))
    composeTestRule.setContent {
      Column {
        Text("redrawn $unrelated times")
        DisplayLocation(placeOptions = mapOf("home" to "Home"), uiState = state)
        DisplayPlaceSuggestion(place = lausanne)
        DisplayLocationHint(text = "hint", tag = HINT_HOLDER)
      }
    }

    // something else on the screen changes: the location section is left as it is
    composeTestRule.runOnIdle { unrelated++ }
    composeTestRule.onNodeWithText("redrawn 1 times").assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(UploadPictureLocationTestTags.SUGGESTION).assertCountEquals(2)

    // the place is picked: its suggestions go away
    composeTestRule.runOnIdle { state = state.copy(location = lausanne) }
    composeTestRule
        .onAllNodesWithTag(UploadPictureLocationTestTags.SUGGESTION)
        .assertCountEquals(1) // only the standalone suggestion is left
  }

  private companion object {
    const val HINT_HOLDER = "UploadPictureLocationTest_hint_holder"
  }
}
