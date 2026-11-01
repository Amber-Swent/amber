// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.caregiver.upload

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.credentials.Credential
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.location.PlaceSearchRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthUser
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.caregiver.DisplayTextFieldTestTags
import com.github.se.amber.ui.caregiver.upload.picture.DisplayWhoIsInvolvedTestTags
import com.github.se.amber.ui.caregiver.upload.picture.SelectedPictureReader
import com.github.se.amber.ui.caregiver.upload.picture.UploadPictureScreen
import com.github.se.amber.ui.caregiver.upload.picture.UploadPictureUIState
import com.github.se.amber.ui.caregiver.upload.picture.UploadPictureViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Compose tests for the add-person flow on
 * [com.github.se.amber.ui.caregiver.upload.picture.UploadPictureScreen].
 */
class AddPersonDialogTest {
  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var viewModel: UploadPictureViewModel
  private lateinit var uiStateFlow: MutableStateFlow<UploadPictureUIState>
  private lateinit var mediaRepo: MediaRepository
  private lateinit var pictureReader: SelectedPictureReader

  @Before
  fun setUp() {
    uiStateFlow = MutableStateFlow(UploadPictureUIState())
    mediaRepo = mockk(relaxed = true)
    pictureReader = mockk(relaxed = true)
    viewModel = mockk(relaxed = true)
    every { viewModel.uiState } returns uiStateFlow
    every { viewModel.careCircle } returns MutableStateFlow(null)
    every { viewModel.involvedOptions } returns MutableStateFlow(emptyMap())
    every { viewModel.placeOptions } returns MutableStateFlow(emptyMap())
  }

  private fun launchScreen() {
    composeTestRule.setContent { UploadPictureScreen(viewModel = viewModel) }
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
            placeRepo = PlaceSearchRepository { Result.success(emptyList()) },
            pictureReader = pictureReader,
        )
    launchScreen()
    composeTestRule.waitUntil(10_000) { viewModel.careCircle.value?.id == circleId }

    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.ADD_PERSON)
        .performScrollTo()
        .performClick()
    // the screen has its own text fields: the first one in the dialog is First Name
    composeTestRule
        .onAllNodes(
            hasTestTag(DisplayTextFieldTestTags.TEXT) and
                hasAnyAncestor(hasTestTag(AddPersonDialogTestTags.DIALOG))
        )[0]
        .performTextInput("Anne")
    composeTestRule.onNode(hasText("Add person") and hasClickAction()).performClick()

    composeTestRule.waitUntil(10_000) { viewModel.uiState.value.shownIds.isNotEmpty() }
    val savedId = viewModel.uiState.value.shownIds.single()
    composeTestRule.onNode(hasText("Anne") and hasClickAction()).assertIsSelected()
    Assert.assertEquals("Anne", fakeCircleRepo.circle.people[savedId]?.firstName)
  }
}
