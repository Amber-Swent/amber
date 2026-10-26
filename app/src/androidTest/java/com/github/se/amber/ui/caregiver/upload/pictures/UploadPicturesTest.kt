/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 * Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload

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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.ui.caregiver.upload.picture.SelectedPictureReader
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

  private lateinit var mediaRepo: MediaRepository
  private lateinit var authRepo: AuthRepository
  private lateinit var profileRepo: ProfileRepository
  private lateinit var circleRepo: CareCircleRepository
  private lateinit var pictureReader: SelectedPictureReader

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

    mediaRepo = mockk(relaxed = true)
    authRepo = mockk(relaxed = true)
    profileRepo = mockk(relaxed = true)
    circleRepo = mockk(relaxed = true)
    pictureReader = mockk(relaxed = true)

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

  // ---------- static content ----------

  @Test
  fun titleIsDisplayed() {
    launchScreen()
    composeTestRule.onNodeWithTag(UploadPictureScreenTestTags.TITLE).assertIsDisplayed()
  }

  @Test
  fun takePictureOptionIsDisplayed() {
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadPictureScreenTestTags.TAKE_PICTURE)
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun selectPictureOptionIsDisplayed() {
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadPictureScreenTestTags.SELECT_PICTURE)
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun uploadButtonIsDisplayed() {
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadPictureScreenTestTags.UPLOAD_BUTTON)
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun involvedSectionIsDisplayed() {
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadPictureScreenTestTags.SELECT_PERSON_INVOLVED)
        .performScrollTo()
        .assertIsDisplayed()
  }

  // ---------- picture selected indicator ----------

  @Test
  fun pictureSelectedIndicatorIsHiddenInitially() {
    launchScreen()
    composeTestRule.onNodeWithTag(UploadPictureScreenTestTags.PICTURE_SELECTED).assertDoesNotExist()
  }

  @Test
  fun pictureSelectedIndicatorIsShownOnceAPictureIsSet() {
    launchScreen()

    every { viewModel.setPicture(any(), any(), any()) } answers
        {
          uiStateFlow.value =
              uiStateFlow.value.copy(
                  pictureFile = firstArg(),
                  pictureWidth = secondArg(),
                  pictureHeight = thirdArg(),
              )
        }

    composeTestRule.runOnIdle { viewModel.setPicture(File("picture.jpg"), 640, 480) }
    composeTestRule
        .onNodeWithTag(UploadPictureScreenTestTags.PICTURE_SELECTED)
        .performScrollTo()
        .assertIsDisplayed()
  }

  // ---------- upload button enabled state ----------

  @Test
  fun uploadButtonIsDisabledInitially() {
    launchScreen()
    composeTestRule.onNodeWithTag(UploadPictureScreenTestTags.UPLOAD_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun uploadButtonIsDisabledWithoutPicture() {
    launchScreen()
    composeTestRule.runOnIdle { viewModel.setDescription("Beach day") }
    composeTestRule.onNodeWithTag(UploadPictureScreenTestTags.UPLOAD_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun uploadButtonIsDisabledWithoutDescription() {
    launchScreen()
    composeTestRule.runOnIdle { viewModel.setPicture(File("picture.jpg"), 640, 480) }
    composeTestRule.onNodeWithTag(UploadPictureScreenTestTags.UPLOAD_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun uploadButtonIsEnabledWhenFormIsValid() {
    launchScreen()
    fillValidForm()
    composeTestRule.onNodeWithTag(UploadPictureScreenTestTags.UPLOAD_BUTTON).assertIsEnabled()
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

  @Test
  fun emptyCircleMessageIsDisplayedWithoutCircle() {
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadPictureScreenTestTags.NO_PERSON_INVOLVED)
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun noChipIsDisplayedWithoutCircle() {
    launchScreen()
    composeTestRule
        .onAllNodesWithTag(UploadPictureScreenTestTags.PERSON_INVOLVED)
        .assertCountEquals(0)
  }

  @Test
  fun emptyCircleMessageIsHiddenWhenCircleHasMembers() {
    launchScreen()
    composeTestRule.runOnIdle {
      involvedOptionsFlow.value = mapOf("member1" to "Member 1", "member2" to "Member 2")
    }
    composeTestRule
        .onNodeWithTag(UploadPictureScreenTestTags.NO_PERSON_INVOLVED)
        .assertDoesNotExist()
  }

  @Test
  fun chipIsDisplayedForEachCircleMember() {
    launchScreen()
    composeTestRule.runOnIdle {
      involvedOptionsFlow.value = mapOf("member1" to "Member 1", "member2" to "Member 2")
    }
    composeTestRule
        .onAllNodesWithTag(UploadPictureScreenTestTags.PERSON_INVOLVED)
        .assertCountEquals(2)
  }

  @Test
  fun chipIsNotSelectedByDefault() {
    launchScreen()
    setCircleWithOptions(twoMembersCircle)
    composeTestRule
        .onAllNodesWithTag(UploadPictureScreenTestTags.PERSON_INVOLVED)[0]
        .assertIsNotSelected()
  }

  @Test
  fun clickingAChipSelectsIt() {
    launchScreen()
    composeTestRule.runOnIdle { involvedOptionsFlow.value = mapOf("member1" to "Member 1") }

    every { viewModel.toggleShownId(any()) } answers
        {
          uiStateFlow.value =
              uiStateFlow.value.copy(
                  shownIds =
                      uiStateFlow.value.shownIds.toMutableSet().apply { add(firstArg<String>()) }
              )
        }

    composeTestRule.onAllNodesWithTag(UploadPictureScreenTestTags.PERSON_INVOLVED)[0].performClick()
    composeTestRule
        .onAllNodesWithTag(UploadPictureScreenTestTags.PERSON_INVOLVED)[0]
        .assertIsSelected()
  }

  @Test
  fun clickingASelectedChipDeselectsIt() {
    launchScreen()
    setCircleWithOptions(twoMembersCircle)
    val chip = composeTestRule.onAllNodesWithTag(UploadPictureScreenTestTags.PERSON_INVOLVED)[0]
    chip.performClick()
    chip.performClick()
    chip.assertIsNotSelected()
  }

  @Test
  fun clickingAChipDoesNotSelectTheOthers() {
    launchScreen()
    setCircleWithOptions(twoMembersCircle)
    composeTestRule.onAllNodesWithTag(UploadPictureScreenTestTags.PERSON_INVOLVED)[0].performClick()
    composeTestRule
        .onAllNodesWithTag(UploadPictureScreenTestTags.PERSON_INVOLVED)[1]
        .assertIsNotSelected()
  }

  @Test
  fun clickingAChipTagsThePersonInTheViewModel() {
    launchScreen()
    composeTestRule.runOnIdle {
      involvedOptionsFlow.value = mapOf("member1" to "Member 1", "member2" to "Member 2")
    }

    every { viewModel.toggleShownId(any()) } answers
        {
          uiStateFlow.value =
              uiStateFlow.value.copy(
                  shownIds =
                      uiStateFlow.value.shownIds.toMutableSet().apply { add(firstArg<String>()) }
              )
        }

    composeTestRule.onAllNodesWithTag(UploadPictureScreenTestTags.PERSON_INVOLVED)[0].performClick()
    composeTestRule.runOnIdle { assertTrue("member1" in viewModel.uiState.value.shownIds) }
  }

  // ---------- location ----------

  private val lausanne = GeoLocation(lat = 46.52, lng = 6.63, placeName = "Lausanne, Switzerland")

  private fun setState(state: UploadPictureUIState) {
    composeTestRule.runOnIdle { uiStateFlow.value = state }
  }

  @Test
  fun locationSectionIsDisplayed() {
    launchScreen()
    composeTestRule
        .onNodeWithTag(UploadPictureLocationTestTags.SECTION)
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun noSavedPlaceChipWithoutSavedPlaces() {
    launchScreen()
    composeTestRule
        .onAllNodesWithTag(UploadPictureLocationTestTags.SAVED_PLACE)
        .assertCountEquals(0)
  }

  @Test
  fun aChipIsDisplayedForEachSavedPlace() {
    launchScreen()
    composeTestRule.runOnIdle {
      placeOptionsFlow.value = mapOf("home" to "Home", "doctor" to "Dr. Martin")
    }
    composeTestRule
        .onAllNodesWithTag(UploadPictureLocationTestTags.SAVED_PLACE)
        .assertCountEquals(2)
  }

  @Test
  fun clickingASavedPlaceChipPicksItAndShowsItSelected() {
    launchScreen()
    composeTestRule.runOnIdle { placeOptionsFlow.value = mapOf("home" to "Home") }
    every { viewModel.selectPlace(any<String>()) } answers
        {
          uiStateFlow.value = uiStateFlow.value.copy(selectedPlaceId = firstArg())
        }

    val chip = composeTestRule.onAllNodesWithTag(UploadPictureLocationTestTags.SAVED_PLACE)[0]
    chip.performScrollTo().assertIsNotSelected()
    chip.performClick()

    verify { viewModel.selectPlace("home") }
    chip.assertIsSelected()
  }

  @Test
  fun typingInTheSearchFieldSendsTheQuery() {
    launchScreen()
    composeTestRule
        .onNode(
            hasSetTextAction() and
                hasAnyAncestor(hasTestTag(UploadPictureLocationTestTags.SEARCH_FIELD))
        )
        .performScrollTo()
        .performTextInput("Lausanne")
    verify { viewModel.setPlaceQuery("Lausanne") }
  }

  @Test
  fun searchingIsShownWhileTheSearchRuns() {
    launchScreen()
    setState(UploadPictureUIState(placeQuery = "Lau", isSearchingPlaces = true))
    composeTestRule
        .onNodeWithTag(UploadPictureLocationTestTags.SEARCHING)
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun noResultIsShownWhenTheSearchFindsNothing() {
    launchScreen()
    setState(UploadPictureUIState(placeQuery = "Grandma's house"))
    composeTestRule
        .onNodeWithTag(UploadPictureLocationTestTags.NO_RESULT)
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun clickingASuggestionPicksIt() {
    launchScreen()
    setState(UploadPictureUIState(placeQuery = "Lau", placeSuggestions = listOf(lausanne)))
    composeTestRule
        .onNodeWithTag(UploadPictureLocationTestTags.SUGGESTION)
        .performScrollTo()
        .performClick()
    verify { viewModel.selectPlace(lausanne) }
  }

  @Test
  fun suggestionsAreHiddenOnceAPlaceIsPicked() {
    launchScreen()
    setState(
        UploadPictureUIState(
            placeQuery = lausanne.placeName!!,
            placeSuggestions = listOf(lausanne),
            location = lausanne,
        )
    )
    composeTestRule.onNodeWithTag(UploadPictureLocationTestTags.SUGGESTION).assertDoesNotExist()
    composeTestRule.onNodeWithTag(UploadPictureLocationTestTags.NO_RESULT).assertDoesNotExist()
  }
}
