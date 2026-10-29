/* Written by LaMaier, with assistance from
 * Claude (Anthropic) via Claude Code.
 * Modified with assistance from OpenAI Codex.
 * Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload.pictures

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.ui.caregiver.upload.picture.DisplayWhoIsInvolvedTestTags
import com.github.se.amber.ui.caregiver.upload.picture.PictureLocationUIState
import com.github.se.amber.ui.caregiver.upload.picture.UploadPictureLocationTestTags
import com.github.se.amber.ui.caregiver.upload.picture.UploadPictureScreen
import com.github.se.amber.ui.caregiver.upload.picture.UploadPictureScreenTestTags
import com.github.se.amber.ui.caregiver.upload.picture.UploadPictureUIState
import com.github.se.amber.ui.caregiver.upload.picture.UploadPictureViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlin.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Compose tests for [UploadPictureScreen]. */
class UploadPicturesTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var viewModel: UploadPictureViewModel
  private var uploadCallbackCount = 0
  private var goBackCallbackCount = 0

  private lateinit var uiStateFlow: MutableStateFlow<UploadPictureUIState>
  private lateinit var careCircleFlow: MutableStateFlow<CareCircle?>
  private lateinit var involvedOptionsFlow: MutableStateFlow<Map<String, String>>
  private lateinit var placeOptionsFlow: MutableStateFlow<Map<String, String>>

  @Before
  fun setUp() {
    uiStateFlow = MutableStateFlow(UploadPictureUIState())
    careCircleFlow = MutableStateFlow(null)
    involvedOptionsFlow = MutableStateFlow(emptyMap())
    placeOptionsFlow = MutableStateFlow(emptyMap())

    viewModel = mockk(relaxed = true)
    every { viewModel.uiState } returns uiStateFlow
    every { viewModel.careCircle } returns careCircleFlow
    every { viewModel.involvedOptions } returns involvedOptionsFlow
    every { viewModel.placeOptions } returns placeOptionsFlow

    uploadCallbackCount = 0
    goBackCallbackCount = 0
  }

  private fun launchScreen() {
    composeTestRule.setContent {
      UploadPictureScreen(
          viewModel = viewModel,
          onUpload = { uploadCallbackCount++ },
          goBack = { goBackCallbackCount++ },
      )
    }
  }

  private fun fakeCircle(members: List<String>): CareCircle = mockk {
    every { id } returns "circle1"
    every { people } returns emptyMap()
    every { memberIds } returns members
  }

  private fun fillValidForm() {
    composeTestRule.runOnIdle {
      uiStateFlow.value =
          UploadPictureUIState(
              pictureFile = File("picture.jpg"),
              pictureWidth = 640,
              pictureHeight = 480,
              description = "Beach day",
              date = MemoryDate(2000, 10, 10),
          )
    }
  }

  // ---------- initial screen ----------

  @Test
  fun initialScreenShowsEveryOptionWithNothingPicked() {
    launchScreen()
    composeTestRule.onNodeWithTag(UploadPictureScreenTestTags.TITLE).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(UploadPictureScreenTestTags.TAKE_PICTURE)
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(UploadPictureScreenTestTags.SELECT_PICTURE)
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(UploadPictureScreenTestTags.PICTURE_SELECTED).assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.SELECT_PERSON_INVOLVED)
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.NO_PERSON_INVOLVED)
        .performScrollTo()
        .assertIsDisplayed()
    chips.assertCountEquals(0)
    composeTestRule
        .onNodeWithTag(UploadPictureLocationTestTags.SECTION)
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule
        .onAllNodesWithTag(UploadPictureLocationTestTags.SAVED_PLACE)
        .assertCountEquals(0)
    composeTestRule
        .onNodeWithTag(UploadPictureScreenTestTags.UPLOAD_BUTTON)
        .assertIsDisplayed()
        .assertIsNotEnabled()
  }

  // ---------- picture and upload button ----------

  @Test
  fun uploadIsEnabledOnlyOnceThePictureAndDescriptionAreSet() {
    launchScreen()
    val uploadButton = composeTestRule.onNodeWithTag(UploadPictureScreenTestTags.UPLOAD_BUTTON)

    composeTestRule.runOnIdle {
      uiStateFlow.value = UploadPictureUIState(description = "Beach day")
    }
    uploadButton.assertIsNotEnabled()

    composeTestRule.runOnIdle {
      uiStateFlow.value =
          UploadPictureUIState(
              pictureFile = File("picture.jpg"),
              pictureWidth = 640,
              pictureHeight = 480,
          )
    }
    composeTestRule
        .onNodeWithTag(UploadPictureScreenTestTags.PICTURE_SELECTED)
        .performScrollTo()
        .assertIsDisplayed()
    uploadButton.assertIsNotEnabled()

    fillValidForm()
    uploadButton.assertIsEnabled()
  }

  // ---------- who is involved ----------

  private val twoMembersCircle by lazy { fakeCircle(members = listOf("member1", "member2")) }

  private fun setCircleWithOptions(circle: CareCircle) {
    composeTestRule.runOnIdle {
      careCircleFlow.value = circle
      // The mocked ViewModel doesn't derive this itself. It mirrors the real mapping
      // for a circle with no people: memberIds.associateWith { it }.
      involvedOptionsFlow.value = circle.memberIds.associateWith { it }
    }
  }

  private val chips
    get() =
        composeTestRule.onAllNodes(
            SemanticsMatcher("person chip") { node ->
              node.config
                  .getOrNull(SemanticsProperties.TestTag)
                  ?.startsWith(DisplayWhoIsInvolvedTestTags.personInvolved("")) == true
            }
        )

  /** Makes the mocked ViewModel tag or untag a person like the real one does. */
  private fun toggleShownIdsForReal() {
    every { viewModel.toggleShownId(any()) } answers
        {
          val id = firstArg<String>()
          uiStateFlow.value =
              uiStateFlow.value.let { state ->
                val shownIds =
                    if (id in state.shownIds) state.shownIds - id else state.shownIds + id
                state.copy(shownIds = shownIds)
              }
        }
  }

  @Test
  fun chipsShowEachPersonByNameAndToggleOnlyThatPerson() {
    toggleShownIdsForReal()
    launchScreen()
    composeTestRule.runOnIdle {
      involvedOptionsFlow.value = mapOf("uid123" to "Marie", "p1" to "Anne")
    }
    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.NO_PERSON_INVOLVED)
        .assertDoesNotExist()
    chips.assertCountEquals(2)
    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.personInvolved("uid123"))
        .assertIsNotSelected()
    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.personInvolved("p1"))
        .assertIsNotSelected()
    composeTestRule.onNodeWithText("Marie").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("uid123").assertDoesNotExist()

    composeTestRule.onNodeWithText("Anne").performScrollTo().assertIsDisplayed().performClick()
    composeTestRule.onNodeWithText("Anne").assertIsSelected()
    composeTestRule.onNodeWithText("Marie").assertIsNotSelected()
    composeTestRule.runOnIdle { assertTrue("p1" in viewModel.uiState.value.shownIds) }
    verify(exactly = 0) { viewModel.toggleShownId("uid123") }

    composeTestRule.onNodeWithText("Anne").performClick()
    composeTestRule.onNodeWithText("Anne").assertIsNotSelected()
    composeTestRule.runOnIdle { assertTrue(viewModel.uiState.value.shownIds.isEmpty()) }
  }

  // ---------- location ----------

  private val lausanne = GeoLocation(lat = 46.52, lng = 6.63, placeName = "Lausanne, Switzerland")

  private fun setLocation(location: PictureLocationUIState) {
    composeTestRule.runOnIdle { uiStateFlow.value = UploadPictureUIState(location = location) }
  }

  @Test
  fun savedPlacesAreShownAsChipsThatCanBePicked() {
    launchScreen()
    every { viewModel.selectPlace(any<String>()) } answers
        {
          uiStateFlow.value =
              uiStateFlow.value.copy(
                  location = uiStateFlow.value.location.copy(selectedPlaceId = firstArg())
              )
        }
    composeTestRule.runOnIdle {
      placeOptionsFlow.value = mapOf("home" to "Home", "doctor" to "Dr. Martin")
    }
    val chips = composeTestRule.onAllNodesWithTag(UploadPictureLocationTestTags.SAVED_PLACE)
    chips.assertCountEquals(2)

    chips[0].performScrollTo().assertIsNotSelected().performClick()

    verify { viewModel.selectPlace("home") }
    chips[0].assertIsSelected()
    chips[1].assertIsNotSelected()
  }

  @Test
  fun searchingAPlaceShowsItsProgressAndSuggestionsToPick() {
    launchScreen()
    composeTestRule
        .onNode(
            hasSetTextAction() and
                hasAnyAncestor(hasTestTag(UploadPictureLocationTestTags.SEARCH_FIELD))
        )
        .performScrollTo()
        .performTextInput("Lausanne")
    verify { viewModel.setPlaceQuery("Lausanne") }

    setLocation(PictureLocationUIState(query = "Lau", isSearching = true))
    composeTestRule
        .onNodeWithTag(UploadPictureLocationTestTags.SEARCHING)
        .performScrollTo()
        .assertIsDisplayed()

    setLocation(PictureLocationUIState(query = "Grandma's house"))
    composeTestRule
        .onNodeWithTag(UploadPictureLocationTestTags.NO_RESULT)
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(UploadPictureLocationTestTags.SEARCHING).assertDoesNotExist()

    setLocation(PictureLocationUIState(query = "Lau", suggestions = listOf(lausanne)))
    composeTestRule
        .onNodeWithTag(UploadPictureLocationTestTags.SUGGESTION)
        .performScrollTo()
        .performClick()
    verify { viewModel.selectPlace(lausanne) }

    setLocation(
        PictureLocationUIState(
            query = lausanne.placeName!!,
            suggestions = listOf(lausanne),
            pickedPlace = lausanne,
        )
    )
    composeTestRule.onNodeWithTag(UploadPictureLocationTestTags.SUGGESTION).assertDoesNotExist()
    composeTestRule.onNodeWithTag(UploadPictureLocationTestTags.NO_RESULT).assertDoesNotExist()
  }
}
