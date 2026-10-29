/* Written by LaMaier, with assistance from
 * Claude (Anthropic) via Claude Code.
 * Modified with assistance from OpenAI Codex.
 * Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload.pictures

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.credentials.Credential
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthUser
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.caregiver.DisplayTextFieldTestTags
import com.github.se.amber.ui.caregiver.upload.AddPersonDialogTestTags
import com.github.se.amber.ui.caregiver.upload.picture.DisplayWhoIsInvolvedTestTags
import com.github.se.amber.ui.caregiver.upload.picture.SelectedPictureReader
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
import org.junit.Assert.assertEquals
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

  @Before
  fun setUp() {
    uiStateFlow = MutableStateFlow(UploadPictureUIState())
    careCircleFlow = MutableStateFlow(null)
    involvedOptionsFlow = MutableStateFlow(emptyMap())

    mediaRepo = mockk(relaxed = true)
    authRepo = mockk(relaxed = true)
    profileRepo = mockk(relaxed = true)
    circleRepo = mockk(relaxed = true)
    pictureReader = mockk(relaxed = true)

    viewModel = mockk(relaxed = true)
    every { viewModel.uiState } returns uiStateFlow
    every { viewModel.careCircle } returns careCircleFlow
    every { viewModel.involvedOptions } returns involvedOptionsFlow

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
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.SELECT_PERSON_INVOLVED)
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

  private val chips
    get() = composeTestRule.onAllNodesWithTag(UploadPictureScreenTestTags.PERSON_INVOLVED)

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
  fun withoutOptionsShowsTheEmptyMessageAndNoChip() {
    launchScreen()
    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.NO_PERSON_INVOLVED)
        .performScrollTo()
        .assertIsDisplayed()
    chips.assertCountEquals(0)
  }

  @Test
  fun optionsShowOneUnselectedChipPerPersonByName() {
    launchScreen()
    composeTestRule.runOnIdle {
      involvedOptionsFlow.value = mapOf("uid123" to "Marie", "p1" to "Anne")
    }
    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.NO_PERSON_INVOLVED)
        .assertDoesNotExist()
    chips.assertCountEquals(2)
    chips[0].assertIsNotSelected()
    chips[1].assertIsNotSelected()
    composeTestRule.onNodeWithText("Marie").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Anne").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("uid123").assertDoesNotExist()
  }
  
  @Test
  fun emptyCircleMessageIsHiddenWhenCircleHasMembers() {
    launchScreen()
    composeTestRule.runOnIdle {
      involvedOptionsFlow.value = mapOf("member1" to "Member 1", "member2" to "Member 2")
    }
    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.NO_PERSON_INVOLVED)
        .assertDoesNotExist()
  }
  
  @Test
  fun clickingAChipTogglesOnlyThatPerson() {
    toggleShownIdsForReal()
    launchScreen()
    composeTestRule.runOnIdle {
      involvedOptionsFlow.value = mapOf("uid123" to "Marie", "p1" to "Anne")
    }

    composeTestRule.onNodeWithText("Anne").performScrollTo().performClick()
    composeTestRule.onNodeWithText("Anne").assertIsSelected()
    composeTestRule.onNodeWithText("Marie").assertIsNotSelected()
    composeTestRule.runOnIdle { assertTrue("p1" in viewModel.uiState.value.shownIds) }
    verify(exactly = 0) { viewModel.toggleShownId("uid123") }

    composeTestRule.onNodeWithText("Anne").performClick()
    composeTestRule.onNodeWithText("Anne").assertIsNotSelected()
    composeTestRule.runOnIdle { assertTrue(viewModel.uiState.value.shownIds.isEmpty()) }
  }

    @Test
  fun chipIsDisplayedForEachCircleMember() {
    launchScreen()
    composeTestRule.runOnIdle {
      involvedOptionsFlow.value = mapOf("member1" to "Member 1", "member2" to "Member 2")
    }
    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.personInvolved("member1"))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.personInvolved("member2"))
        .assertIsDisplayed()
  }

  @Test
  fun chipIsNotSelectedByDefault() {
    launchScreen()
    setCircleWithOptions(twoMembersCircle)
    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.personInvolved("member1"))
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

    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.personInvolved("member1"))
        .performClick()
    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.personInvolved("member1"))
        .assertIsSelected()
  }

  @Test
  fun clickingASelectedChipDeselectsIt() {
    launchScreen()
    setCircleWithOptions(twoMembersCircle)
    val chip = composeTestRule.onNodeWithTag(DisplayWhoIsInvolvedTestTags.personInvolved("member1"))
    chip.performClick()
    chip.performClick()
    chip.assertIsNotSelected()
  }

  @Test
  fun clickingAChipDoesNotSelectTheOthers() {
    launchScreen()
    setCircleWithOptions(twoMembersCircle)
    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.personInvolved("member1"))
        .performClick()
    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.personInvolved("member2"))
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

    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.personInvolved("member1"))
        .performClick()
    composeTestRule.runOnIdle { assertTrue("member1" in viewModel.uiState.value.shownIds) }
  }
  
  // ---------- add person dialog ----------

  @Test
  fun addPersonChipOpensDialog() {
    every { viewModel.onAddPerson() } answers
        {
          uiStateFlow.value = uiStateFlow.value.copy(isAddPersonOpen = true)
        }
    launchScreen()

    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.ADD_PERSON)
        .performScrollTo()
        .performClick()

    composeTestRule.onNodeWithTag(AddPersonDialogTestTags.DIALOG).assertIsDisplayed()
    verify(exactly = 1) { viewModel.onAddPerson() }
  }

  @Test
  fun closeButtonDismissesAddPersonDialog() {
    uiStateFlow.value = uiStateFlow.value.copy(isAddPersonOpen = true)
    every { viewModel.dismissAddPerson() } answers
        {
          uiStateFlow.value = uiStateFlow.value.copy(isAddPersonOpen = false)
        }
    launchScreen()

    composeTestRule.onNodeWithTag(AddPersonDialogTestTags.CLOSE).performClick()

    composeTestRule.onNodeWithTag(AddPersonDialogTestTags.DIALOG).assertDoesNotExist()
    verify(exactly = 1) { viewModel.dismissAddPerson() }
  }

  @Test
  fun addPersonSaveButtonCallsViewModelForValidName() {
    uiStateFlow.value = uiStateFlow.value.copy(isAddPersonOpen = true, personFirstName = "Anne")
    launchScreen()

    composeTestRule.onNode(hasText("Add person") and hasClickAction()).performClick()

    verify(exactly = 1) { viewModel.addPerson() }
  }

  @Test
  fun addPersonSaveTrimsNames() {
    uiStateFlow.value =
        uiStateFlow.value.copy(
            isAddPersonOpen = true,
            personFirstName = "  Anne  ",
            personLastName = "  Dupont  ",
            personNickname = "  Nana  ",
        )
    launchScreen()

    composeTestRule.onNode(hasText("Add person") and hasClickAction()).performClick()

    verify(exactly = 1) { viewModel.setPersonFirstName("Anne") }
    verify(exactly = 1) { viewModel.setPersonLastName("Dupont") }
    verify(exactly = 1) { viewModel.setPersonNickname("Nana") }
    verify(exactly = 1) { viewModel.addPerson() }
  }

  @Test
  fun addPersonSaveButtonIsDisabledForBlankName() {
    uiStateFlow.value = uiStateFlow.value.copy(isAddPersonOpen = true, personFirstName = "  ")
    launchScreen()

    composeTestRule.onNode(hasText("Add person") and hasClickAction()).assertIsNotEnabled()
  }

  @Test
  fun addPersonPopupSavesWithInMemoryRepositoriesAndShowsSelectedChip() {
    val userId = "caregiver1"
    val circleId = "circle1"
    val fakeAuthRepo =
        object : AuthRepository {
          override val currentUser = AuthUser(userId)

          override suspend fun signInWithGoogle(credential: Credential): Result<AuthUser> =
              error("Not used by this test")

          override suspend fun signOut(): Result<Unit> = error("Not used by this test")
        }
    val fakeProfileRepo =
        object : ProfileRepository {
          override suspend fun createProfile(person: Person, role: Role): Result<Unit> =
              error("Not used by this test")

          override suspend fun getCurrentProfile(): Result<UserProfile?> =
              error("Not used by this test")

          override suspend fun getProfile(uid: String): Result<UserProfile?> =
              Result.success(UserProfile(uid = uid, circleIds = listOf(circleId)))
        }
    val fakeCircleRepo =
        object : CareCircleRepository {
          var circle = CareCircle(id = circleId, memberIds = listOf(userId))

          override fun newId(): String = "person1"

          override suspend fun getCircle(circleId: String): Result<CareCircle> =
              Result.success(circle)

          override suspend fun addPerson(circleId: String, person: Person): Result<Person> {
            val saved = person.copy(id = newId())
            circle = circle.copy(people = circle.people + (saved.id to saved))
            return Result.success(saved)
          }
        }
    viewModel =
        UploadPictureViewModel(
            mediaRepo = mediaRepo,
            authRepo = fakeAuthRepo,
            profileRepo = fakeProfileRepo,
            circleRepo = fakeCircleRepo,
            pictureReader = pictureReader,
        )
    launchScreen()
    composeTestRule.waitUntil(10_000) { viewModel.careCircle.value?.id == circleId }

    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.ADD_PERSON)
        .performScrollTo()
        .performClick()
    composeTestRule.onAllNodesWithTag(DisplayTextFieldTestTags.TEXT)[1].performTextInput("Anne")
    composeTestRule.onNode(hasText("Add person") and hasClickAction()).performClick()

    composeTestRule.waitUntil(10_000) { viewModel.uiState.value.shownIds.isNotEmpty() }
    val savedId = viewModel.uiState.value.shownIds.single()
    composeTestRule.onNode(hasText("Anne") and hasClickAction()).assertIsSelected()
    assertEquals("Anne", fakeCircleRepo.circle.people[savedId]?.firstName)
  }
}
