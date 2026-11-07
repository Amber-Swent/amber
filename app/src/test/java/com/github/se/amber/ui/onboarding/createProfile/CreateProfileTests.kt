// this code was written by Alaabrine
// with the aid of AI coding agent
// Modified by Yuhan YANG, with assistance from Cursor.
package com.github.se.amber.ui.createProfile

import android.os.Bundle
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.credentials.Credential
import androidx.credentials.CustomCredential
import com.github.se.amber.data.profile.ProfileCreationError
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthUser
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.ui.authentication.signOut.SignOutButtonTestTags
import com.github.se.amber.ui.authentication.signOut.SignOutViewModel
import com.github.se.amber.ui.onboarding.createProfile.CreateProfilePreview
import com.github.se.amber.ui.onboarding.createProfile.CreateProfileScreen
import com.github.se.amber.ui.onboarding.createProfile.CreateProfileTestTags
import com.github.se.amber.ui.onboarding.createProfile.CreateProfileViewModel
import com.github.se.amber.ui.onboarding.createProfile.FILL_OUT_MESSAGE
import com.github.se.amber.ui.onboarding.createProfile.PreviewProfileRepository
import com.github.se.amber.ui.onboarding.createProfile.PreviewSignOutAuthRepository
import com.github.se.amber.ui.patient.PatientUtilsTestTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CreateProfileUITests {
  @get:Rule val composeTestRule = createComposeRule()

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun setCreateProfile(
      viewModel: CreateProfileViewModel =
          CreateProfileViewModel(
              profileRepository =
                  object : ProfileRepository {
                    override suspend fun createProfile(person: Person, role: Role) =
                        Result.success(Unit)

                    override suspend fun getCurrentProfile() = Result.success(null)

                    override suspend fun getProfile(uid: String) = Result.success(null)
                  },
          ),
      signOutViewModel: SignOutViewModel = SignOutViewModel(idleAuthRepository()),
      onSignedOut: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      CreateProfileScreen(
          viewModel = viewModel,
          signOutViewModel = signOutViewModel,
          onSignedOut = onSignedOut,
      )
    }
  }

  private fun hasErrorSemantics(): SemanticsMatcher =
      SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)

  // ---------- Valid submit ----------

  @Test
  fun enteringDetailsAndChoosingPatient_sendsPatientProfile() {
    var savedPerson: Person? = null
    var savedRole: Role? = null
    var callCount = 0

    val repository =
        object : ProfileRepository {
          override suspend fun createProfile(person: Person, role: Role): Result<Unit> {
            callCount++
            savedPerson = person
            savedRole = role
            return Result.success(Unit)
          }

          override suspend fun getCurrentProfile() = Result.success(null)

          override suspend fun getProfile(uid: String) = Result.success(null)
        }
    setCreateProfile(CreateProfileViewModel(profileRepository = repository))

    composeTestRule.onNodeWithTag(CreateProfileTestTags.NAME_INPUT).performTextInput(" Ada ")
    composeTestRule
        .onNodeWithTag(CreateProfileTestTags.SURNAME_INPUT)
        .performTextInput(" Lovelace ")

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, callCount)
    assertEquals("Ada", savedPerson?.firstName)
    assertEquals("Lovelace", savedPerson?.lastName)
    assertEquals(Role.PATIENT, savedRole)
  }

  @Test
  fun repositoryFailure_showsSubmitError() {
    val repository =
        object : ProfileRepository {
          override suspend fun createProfile(person: Person, role: Role): Result<Unit> =
              Result.failure(ProfileCreationError.PermissionDenied)

          override suspend fun getCurrentProfile() = Result.success(null)

          override suspend fun getProfile(uid: String) = Result.success(null)
        }
    setCreateProfile(CreateProfileViewModel(profileRepository = repository))

    composeTestRule.onNodeWithTag(CreateProfileTestTags.NAME_INPUT).performTextInput("Ada")
    composeTestRule.onNodeWithTag(CreateProfileTestTags.SURNAME_INPUT).performTextInput("Lovelace")
    composeTestRule
        .onNodeWithTag(CreateProfileTestTags.CAREGIVER_BUTTON)
        .performScrollTo()
        .performClick()
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(CreateProfileTestTags.SUBMIT_ERROR)
        .performScrollTo()
        .assertIsDisplayed()
        .assertTextEquals("You don't have permission to create a profile.")
  }

  // ---------- Validation when role is chosen with empty fields ----------

  @Test
  fun pressingPatient_withEmptyFields_highlightsInputsInRedAndAsksToFillOut() {
    setCreateProfile()

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEmptyFieldValidationIsShown()
  }

  @Test
  fun pressingCaregiver_withEmptyFields_highlightsInputsInRedAndAsksToFillOut() {
    setCreateProfile()

    composeTestRule
        .onNodeWithTag(CreateProfileTestTags.CAREGIVER_BUTTON)
        .performScrollTo()
        .performClick()
    composeTestRule.waitForIdle()

    assertEmptyFieldValidationIsShown()
  }

  @Test
  fun previewShowsCreateProfileAndTheSignOutButton() {
    composeTestRule.setContent { CreateProfilePreview() }

    composeTestRule.onNodeWithText("Create an account").assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(SignOutButtonTestTags.BUTTON)
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun previewRepositoriesReturnSafeResults() = runBlocking {
    val person = Person(firstName = "Ada", lastName = "Lovelace")
    val credential = CustomCredential("preview", Bundle())

    assertTrue(PreviewProfileRepository.createProfile(person, Role.PATIENT).isSuccess)
    assertEquals(null, PreviewProfileRepository.getCurrentProfile().getOrNull())
    assertEquals(null, PreviewProfileRepository.getProfile("uid").getOrNull())
    assertEquals(null, PreviewSignOutAuthRepository.currentUser)
    assertTrue(PreviewSignOutAuthRepository.signInWithGoogle(credential).isFailure)
    assertTrue(PreviewSignOutAuthRepository.reauthenticateWithGoogle(credential).isFailure)
    assertTrue(PreviewSignOutAuthRepository.signOut().isSuccess)
  }

  @Test
  fun signOutSuccess_endsTheSessionOnceAndDoesNotSaveAProfile() {
    var profileWrites = 0
    var signOutCalls = 0
    var signedOutCount = 0
    val profileViewModel =
        CreateProfileViewModel(
            profileRepository =
                object : ProfileRepository {
                  override suspend fun createProfile(person: Person, role: Role): Result<Unit> {
                    profileWrites++
                    return Result.success(Unit)
                  }

                  override suspend fun getCurrentProfile() = Result.success(null)

                  override suspend fun getProfile(uid: String) = Result.success(null)
                },
        )
    setCreateProfile(
        viewModel = profileViewModel,
        signOutViewModel =
            SignOutViewModel(
                countingAuthRepository(
                    signOutResult = Result.success(Unit),
                    onSignOut = { signOutCalls++ },
                ),
            ),
        onSignedOut = { signedOutCount++ },
    )

    composeTestRule.onNodeWithTag(SignOutButtonTestTags.BUTTON).performScrollTo().performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, signOutCalls)
    assertEquals(1, signedOutCount)
    assertEquals(0, profileWrites)
  }

  @Test
  fun signOutFailure_showsTheSignOutToastAndStaysOnCreateProfile() {
    var signedOutCount = 0
    setCreateProfile(
        signOutViewModel =
            SignOutViewModel(
                countingAuthRepository(
                    signOutResult = Result.failure(IllegalStateException("offline")),
                    currentUser = AuthUser("uid"),
                ),
            ),
        onSignedOut = { signedOutCount++ },
    )

    composeTestRule.onNodeWithTag(SignOutButtonTestTags.BUTTON).performScrollTo().performClick()
    composeTestRule.waitForIdle()

    assertEquals(0, signedOutCount)
    assertEquals("Could not sign out. Please try again.", ShadowToast.getTextOfLatestToast())
    composeTestRule.onNodeWithTag(CreateProfileTestTags.NAME_INPUT).assertIsDisplayed()
  }

  private fun assertEmptyFieldValidationIsShown() {
    composeTestRule.onNodeWithTag(CreateProfileTestTags.NAME_INPUT).assert(hasErrorSemantics())
    composeTestRule.onNodeWithTag(CreateProfileTestTags.SURNAME_INPUT).assert(hasErrorSemantics())

    composeTestRule
        .onNodeWithTag(CreateProfileTestTags.NAME_ERROR, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals(FILL_OUT_MESSAGE)
    composeTestRule
        .onNodeWithTag(CreateProfileTestTags.SURNAME_ERROR, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals(FILL_OUT_MESSAGE)
  }
}

private fun idleAuthRepository(): AuthRepository = countingAuthRepository(Result.success(Unit))

private fun countingAuthRepository(
    signOutResult: Result<Unit>,
    currentUser: AuthUser? = AuthUser("uid"),
    onSignOut: () -> Unit = {},
): AuthRepository =
    object : AuthRepository {
      override val currentUser: AuthUser? = currentUser

      override suspend fun signInWithGoogle(credential: Credential): Result<AuthUser> =
          error("unused")

      override suspend fun reauthenticateWithGoogle(credential: Credential): Result<Unit> =
          error("unused")

      override suspend fun signOut(): Result<Unit> {
        onSignOut()
        return signOutResult
      }
    }
