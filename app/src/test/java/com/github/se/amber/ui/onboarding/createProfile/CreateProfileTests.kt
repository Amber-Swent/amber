// Written by Alaabrine with the aid of AI coding agent
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.createProfile

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.data.profile.ProfileCreationError
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.ui.onboarding.createProfile.CreateProfileScreen
import com.github.se.amber.ui.onboarding.createProfile.CreateProfileTestTags
import com.github.se.amber.ui.onboarding.createProfile.CreateProfileViewModel
import com.github.se.amber.ui.patient.PatientUtilsTestTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CreateProfileUITests {
  @get:Rule val composeTestRule = createComposeRule()

  /** Text of [id] in the current test language. */
  private fun string(id: Int): String =
      ApplicationProvider.getApplicationContext<Context>().getString(id)

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

                    override suspend fun updateOnboardingProfile(person: Person, role: Role) =
                        Result.success(Unit)

                    override suspend fun getCurrentProfile() = Result.success(null)

                    override suspend fun getProfile(uid: String) = Result.success(null)
                  },
          ),
  ) {
    composeTestRule.setContent { CreateProfileScreen(viewModel = viewModel) }
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

          override suspend fun updateOnboardingProfile(person: Person, role: Role) =
              Result.success(Unit)

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

          override suspend fun updateOnboardingProfile(person: Person, role: Role) =
              Result.success(Unit)

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
        .assertTextEquals(string(R.string.createProfileErrorPermissionDenied))
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
  @Config(qualifiers = "fr")
  fun validationErrorIsInFrenchWhenThePhoneIsInFrench() {
    setCreateProfile()

    composeTestRule
        .onNodeWithTag(CreateProfileTestTags.CAREGIVER_BUTTON)
        .performScrollTo()
        .performClick()
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(CreateProfileTestTags.NAME_ERROR, useUnmergedTree = true)
        .assertTextEquals("Veuillez remplir ce champ")
  }

  private fun assertEmptyFieldValidationIsShown() {
    composeTestRule.onNodeWithTag(CreateProfileTestTags.NAME_INPUT).assert(hasErrorSemantics())
    composeTestRule.onNodeWithTag(CreateProfileTestTags.SURNAME_INPUT).assert(hasErrorSemantics())

    composeTestRule
        .onNodeWithTag(CreateProfileTestTags.NAME_ERROR, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.createProfileFillOut))
    composeTestRule
        .onNodeWithTag(CreateProfileTestTags.SURNAME_ERROR, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.createProfileFillOut))
  }
}
