// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.caregiver.upload.picture

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.AnnotatedString
import com.github.se.amber.model.location.GeoLocation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric tests for the "Location - optional" helpers of the Upload Picture screen, rendered on
 * their own: the screen tests cover them through the ViewModel, these cover what the helpers show
 * for each search state and with any list of suggestions.
 */
@RunWith(RobolectricTestRunner::class)
class UploadPictureLocationTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val lausanne = GeoLocation(lat = 46.52, lng = 6.63, placeName = "Lausanne, Switzerland")
  private val unnamed = GeoLocation(lat = 45.0, lng = -73.0, placeName = null)

  private val searchField =
      hasSetTextAction() and hasAnyAncestor(hasTestTag(UploadPictureLocationTestTags.SEARCH_FIELD))

  private fun hasEditableText(text: String) =
      SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text))

  private fun hasOnlyText(text: String) =
      SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(AnnotatedString(text)))

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
  fun withDefaultCallbacksTappingAndTypingChangeNothing() {
    composeTestRule.setContent {
      DisplayLocation(
          savedPlaceNamesById = mapOf("home" to "Home"),
          state = PictureLocationUIState(query = "Lau", suggestions = listOf(lausanne)),
      )
    }

    composeTestRule.onNodeWithTag(UploadPictureLocationTestTags.SAVED_PLACE).performClick()
    composeTestRule.onNodeWithTag(UploadPictureLocationTestTags.SUGGESTION).performClick()
    composeTestRule.onNode(searchField).performTextInput("x")

    // the state is hoisted: without callbacks, nothing gets picked or typed
    composeTestRule.onNodeWithTag(UploadPictureLocationTestTags.SAVED_PLACE).assertIsNotSelected()
    composeTestRule.onNode(searchField).assert(hasEditableText("Lau"))
    composeTestRule.onAllNodesWithTag(UploadPictureLocationTestTags.SUGGESTION).assertCountEquals(1)
  }

  @Test
  fun eachSuggestionIsShownAndPickedWithItsOwnPlace() {
    val picked = mutableListOf<GeoLocation>()
    composeTestRule.setContent {
      DisplayLocation(
          state = PictureLocationUIState(query = "La", suggestions = listOf(lausanne, unnamed)),
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
  fun aPlaceTypedButNotFoundSaysHowToUnblockTheUpload() {
    composeTestRule.setContent {
      DisplayLocation(state = PictureLocationUIState(query = "Grandma's house"))
    }

    composeTestRule
        .onNodeWithTag(UploadPictureLocationTestTags.NO_RESULT)
        .assertIsDisplayed()
        .assert(hasOnlyText(NO_PLACE_FOUND_HINT))
    composeTestRule.onNodeWithTag(UploadPictureLocationTestTags.SEARCH_FAILED).assertDoesNotExist()
  }

  @Test
  fun aFailedSearchIsNotShownAsNoPlacesFound() {
    composeTestRule.setContent {
      DisplayLocation(state = PictureLocationUIState(query = "Lausanne", searchFailed = true))
    }

    composeTestRule
        .onNodeWithTag(UploadPictureLocationTestTags.SEARCH_FAILED)
        .assertIsDisplayed()
        .assert(hasOnlyText(PLACE_SEARCH_FAILED_HINT))
    composeTestRule.onNodeWithTag(UploadPictureLocationTestTags.NO_RESULT).assertDoesNotExist()
  }

  @Test
  fun aSearchStillRunningOnlyShowsSearching() {
    composeTestRule.setContent {
      DisplayLocation(
          state = PictureLocationUIState(query = "Lau", isSearching = true, searchFailed = true)
      )
    }

    composeTestRule.onNodeWithText("Searching…").assertIsDisplayed()
    composeTestRule.onNodeWithTag(UploadPictureLocationTestTags.SEARCH_FAILED).assertDoesNotExist()
    composeTestRule.onNodeWithTag(UploadPictureLocationTestTags.NO_RESULT).assertDoesNotExist()
  }

  @Test
  fun pressingEnterInTheSearchFieldDoesNotAddANewLine() {
    var query by mutableStateOf("Lausanne")
    composeTestRule.setContent {
      DisplayLocation(state = PictureLocationUIState(query = query), onQueryChange = { query = it })
    }

    composeTestRule.onNode(searchField).performClick().performKeyInput { pressKey(Key.Enter) }

    composeTestRule.runOnIdle { assertEquals("Lausanne", query) }
  }

  @Test
  fun aPlaceWithoutANameIsShownWithEmptyText() {
    composeTestRule.setContent { DisplayPlaceSuggestion() } // default place: no name

    composeTestRule
        .onNodeWithTag(UploadPictureLocationTestTags.SUGGESTION)
        .assertIsDisplayed()
        .assert(hasOnlyText(""))
        .performClick() // the default callback does nothing
        .assert(hasOnlyText(""))
  }

  @Test
  fun aHintShowsItsTextUnderItsTag() {
    composeTestRule.setContent { DisplayLocationHint(text = "Searching…", tag = HINT_TAG) }

    composeTestRule.onNodeWithTag(HINT_TAG).assertIsDisplayed().assert(hasOnlyText("Searching…"))
  }

  @Test
  fun aHintWithDefaultsShowsAnEmptyText() {
    composeTestRule.setContent { DisplayLocationHint() }

    composeTestRule.onNodeWithTag("").assert(hasOnlyText(""))
  }

  @Test
  fun suggestionsGoAwayOnceAPlaceIsPicked() {
    var state by
        mutableStateOf(PictureLocationUIState(query = "La", suggestions = listOf(lausanne)))
    composeTestRule.setContent { DisplayLocation(state = state) }
    composeTestRule.onAllNodesWithTag(UploadPictureLocationTestTags.SUGGESTION).assertCountEquals(1)

    composeTestRule.runOnIdle { state = state.copy(location = lausanne) }

    composeTestRule.onAllNodesWithTag(UploadPictureLocationTestTags.SUGGESTION).assertCountEquals(0)
    composeTestRule.onNodeWithTag(UploadPictureLocationTestTags.NO_RESULT).assertDoesNotExist()
  }

  private companion object {
    const val HINT_TAG = "UploadPictureLocationTest_hint"
  }
}
