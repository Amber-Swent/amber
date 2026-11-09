// Written by Alaabrine with the aid of an AI coding agent
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.createProfile

import com.github.se.amber.R
import com.github.se.amber.data.profile.ProfileCreationError
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.validation.InputValidation
import com.github.se.amber.ui.onboarding.createProfile.CreateProfileUIState
import com.github.se.amber.ui.onboarding.createProfile.CreateProfileViewModel
import com.github.se.amber.ui.utils.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CreateProfileViewModel] with a mocked [ProfileRepository].
 *
 * Repository Firebase/emulator coverage lives in FirebaseProfileRepositoryTest and related emulator
 * suites; this class only verifies ViewModel validation and UI-state transitions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateProfileViewModelTests {

  private lateinit var profileRepository: ProfileRepository
  private var successCount = 0
  private var lastSuccessRole: Role? = null
  private lateinit var viewModel: CreateProfileViewModel
  private val onSuccess: (Role) -> Unit = { role ->
    successCount++
    lastSuccessRole = role
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
    profileRepository = mockk()
    successCount = 0
    lastSuccessRole = null
    viewModel = CreateProfileViewModel(profileRepository = profileRepository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkAll()
  }

  // ---------- Initial state ----------

  @Test
  fun initialState_hasEmptyFieldsAndNoErrors() {
    assertEquals(CreateProfileUIState(), viewModel.uiState.value)
    assertNull(viewModel.uiState.value.nameError)
    assertNull(viewModel.uiState.value.surnameError)
  }

  // ---------- Name format ----------

  @Test
  fun nameWithDigitsOrSymbols_showsFormatErrorAtOnceAndDisablesRoles() {
    viewModel.onNameChange("Ada2")
    viewModel.onSurnameChange("Love!ace")

    val state = viewModel.uiState.value
    assertEquals(UiText.Resource(R.string.inputErrorNameFormat), state.nameError)
    assertEquals(UiText.Resource(R.string.inputErrorNameFormat), state.surnameError)
    assertFalse(state.canSubmit)
  }

  @Test
  fun typingStopsAtNameMaxLength() {
    viewModel.onNameChange("A".repeat(InputValidation.NAME_MAX_LENGTH + 20))

    assertEquals(InputValidation.NAME_MAX_LENGTH, viewModel.uiState.value.name.length)
  }

  @Test
  fun onRoleSelected_unfinishedName_showsErrorWithoutCallingRepository() = runTest {
    viewModel.onNameChange("Anne-")
    viewModel.onSurnameChange("Hopper")
    assertNull(viewModel.uiState.value.nameError) // may still become "Anne-Marie"

    viewModel.onRoleSelected(Role.PATIENT, onSuccess)
    advanceUntilIdle()

    assertEquals(UiText.Resource(R.string.inputErrorNameFormat), viewModel.uiState.value.nameError)
    assertEquals(0, successCount)
    coVerify(exactly = 0) { profileRepository.createProfile(any(), any()) }
  }

  // ---------- onNameChange / onSurnameChange ----------

  @Test
  fun onNameChange_updatesNameInState() {
    viewModel.onNameChange("Ada")

    assertEquals("Ada", viewModel.uiState.value.name)
  }

  @Test
  fun onSurnameChange_updatesSurnameInState() {
    viewModel.onSurnameChange("Lovelace")

    assertEquals("Lovelace", viewModel.uiState.value.surname)
  }

  @Test
  fun onNameChange_clearsDerivedNameErrorWhenValueIsNonBlank() {
    viewModel.onRoleSelected(Role.PATIENT, onSuccess)
    assertEquals(UiText.Resource(R.string.createProfileFillOut), viewModel.uiState.value.nameError)

    viewModel.onNameChange("Ada")

    assertNull(viewModel.uiState.value.nameError)
    assertEquals("Ada", viewModel.uiState.value.name)
  }

  @Test
  fun onSurnameChange_clearsDerivedSurnameErrorWhenValueIsNonBlank() {
    viewModel.onRoleSelected(Role.PATIENT, onSuccess)
    assertEquals(
        UiText.Resource(R.string.createProfileFillOut),
        viewModel.uiState.value.surnameError,
    )

    viewModel.onSurnameChange("Lovelace")

    assertNull(viewModel.uiState.value.surnameError)
    assertEquals("Lovelace", viewModel.uiState.value.surname)
  }

  @Test
  fun onNameChange_keepsDerivedNameErrorWhenValueIsStillBlank() {
    viewModel.onRoleSelected(Role.PATIENT, onSuccess)
    assertEquals(UiText.Resource(R.string.createProfileFillOut), viewModel.uiState.value.nameError)

    viewModel.onNameChange("   ")

    assertEquals(UiText.Resource(R.string.createProfileFillOut), viewModel.uiState.value.nameError)
  }

  // ---------- Validation (no repository call) ----------

  @Test
  fun onRoleSelected_blankFields_setsDerivedErrorsAndDoesNotCallRepository() = runTest {
    viewModel.onRoleSelected(Role.PATIENT, onSuccess)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state.validationAttempted)
    assertEquals(UiText.Resource(R.string.createProfileFillOut), state.nameError)
    assertEquals(UiText.Resource(R.string.createProfileFillOut), state.surnameError)
    assertEquals("", state.name)
    assertEquals("", state.surname)
    assertFalse(state.isSaving)
    assertNull(state.errorMsgRes)
    assertEquals(0, successCount)
    coVerify(exactly = 0) { profileRepository.createProfile(any(), any()) }
  }

  @Test
  fun onRoleSelected_whitespaceOnlyFields_treatedAsBlankAndDoesNotCallRepository() = runTest {
    viewModel.onNameChange("   ")
    viewModel.onSurnameChange("\t")

    viewModel.onRoleSelected(Role.CAREGIVER, onSuccess)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(UiText.Resource(R.string.createProfileFillOut), state.nameError)
    assertEquals(UiText.Resource(R.string.createProfileFillOut), state.surnameError)
    assertEquals("", state.name)
    assertEquals("", state.surname)
    coVerify(exactly = 0) { profileRepository.createProfile(any(), any()) }
  }

  @Test
  fun onRoleSelected_blankNameOnly_setsNameErrorAndDoesNotCallRepository() = runTest {
    viewModel.onSurnameChange("Lovelace")

    viewModel.onRoleSelected(Role.PATIENT, onSuccess)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(UiText.Resource(R.string.createProfileFillOut), state.nameError)
    assertNull(state.surnameError)
    assertEquals("Lovelace", state.surname)
    coVerify(exactly = 0) { profileRepository.createProfile(any(), any()) }
  }

  @Test
  fun onRoleSelected_blankSurnameOnly_setsSurnameErrorAndDoesNotCallRepository() = runTest {
    viewModel.onNameChange("Ada")

    viewModel.onRoleSelected(Role.PATIENT, onSuccess)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertNull(state.nameError)
    assertEquals(UiText.Resource(R.string.createProfileFillOut), state.surnameError)
    assertEquals("Ada", state.name)
    coVerify(exactly = 0) { profileRepository.createProfile(any(), any()) }
  }

  // ---------- Successful create (mocked repository) ----------

  @Test
  fun onRoleSelected_patientSuccess_invokesOnSuccessAndClearsSaving() = runTest {
    coEvery { profileRepository.createProfile(any(), any()) } returns Result.success(Unit)
    viewModel.onNameChange("  Ada  ")
    viewModel.onSurnameChange("  Lovelace  ")

    viewModel.onRoleSelected(Role.PATIENT, onSuccess)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isSaving)
    assertNull(state.errorMsgRes)
    assertEquals(1, successCount)
    assertEquals(Role.PATIENT, lastSuccessRole)
    assertEquals("Ada", state.name)
    assertEquals("Lovelace", state.surname)
    coVerify(exactly = 1) {
      profileRepository.createProfile(
          person = Person(firstName = "Ada", lastName = "Lovelace"),
          role = Role.PATIENT,
      )
    }
  }

  @Test
  fun onRoleSelected_caregiverSuccess_invokesOnSuccess() = runTest {
    coEvery { profileRepository.createProfile(any(), any()) } returns Result.success(Unit)
    viewModel.onNameChange("Grace")
    viewModel.onSurnameChange("Hopper")

    viewModel.onRoleSelected(Role.CAREGIVER, onSuccess)
    advanceUntilIdle()

    assertEquals(1, successCount)
    assertEquals(Role.CAREGIVER, lastSuccessRole)
    coVerify(exactly = 1) {
      profileRepository.createProfile(
          person = Person(firstName = "Grace", lastName = "Hopper"),
          role = Role.CAREGIVER,
      )
    }
  }

  // ---------- Repository failures ----------

  @Test
  fun onRoleSelected_invalidPersonDetails_setsMappedSubmitError() = runTest {
    coEvery { profileRepository.createProfile(any(), any()) } returns
        Result.failure(ProfileCreationError.InvalidPersonDetails)
    fillValidNameAndSurname()

    viewModel.onRoleSelected(Role.PATIENT, onSuccess)
    advanceUntilIdle()

    assertEquals(
        R.string.createProfileErrorInvalidName,
        viewModel.uiState.value.errorMsgRes,
    )
    assertEquals(0, successCount)
    assertFalse(viewModel.uiState.value.isSaving)
  }

  @Test
  fun onRoleSelected_profileAlreadyExists_setsMappedSubmitError() = runTest {
    coEvery { profileRepository.createProfile(any(), any()) } returns
        Result.failure(ProfileCreationError.ProfileAlreadyExists)
    fillValidNameAndSurname()

    viewModel.onRoleSelected(Role.PATIENT, onSuccess)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isSaving)
    assertEquals(0, successCount)
    assertEquals(R.string.createProfileErrorExists, state.errorMsgRes)
  }

  @Test
  fun onRoleSelected_networkError_setsMappedOfflineSubmitError() = runTest {
    coEvery { profileRepository.createProfile(any(), any()) } returns
        Result.failure(ProfileCreationError.Network)
    fillValidNameAndSurname()

    viewModel.onRoleSelected(Role.CAREGIVER, onSuccess)
    advanceUntilIdle()

    assertEquals(
        R.string.createProfileErrorOffline,
        viewModel.uiState.value.errorMsgRes,
    )
    assertEquals(0, successCount)
  }

  @Test
  fun onRoleSelected_genericFailure_usesFallbackMessage() = runTest {
    coEvery { profileRepository.createProfile(any(), any()) } returns
        Result.failure(IllegalStateException("boom"))
    fillValidNameAndSurname()

    viewModel.onRoleSelected(Role.PATIENT, onSuccess)
    advanceUntilIdle()

    assertEquals(
        R.string.createProfileErrorUnknown,
        viewModel.uiState.value.errorMsgRes,
    )
    assertFalse(viewModel.uiState.value.isSaving)
  }

  @Test
  fun onRoleSelected_clearsPreviousSubmitErrorBeforeRetry() = runTest {
    coEvery { profileRepository.createProfile(any(), any()) } returnsMany
        listOf(
            Result.failure(ProfileCreationError.Network),
            Result.success(Unit),
        )
    fillValidNameAndSurname()

    viewModel.onRoleSelected(Role.PATIENT, onSuccess)
    advanceUntilIdle()
    assertEquals(
        R.string.createProfileErrorOffline,
        viewModel.uiState.value.errorMsgRes,
    )

    viewModel.onRoleSelected(Role.PATIENT, onSuccess)
    advanceUntilIdle()

    assertNull(viewModel.uiState.value.errorMsgRes)
    assertEquals(1, successCount)
    assertEquals(Role.PATIENT, lastSuccessRole)
  }

  // ---------- Concurrent save guard ----------

  @Test
  fun onRoleSelected_whileSaving_ignoresSecondCall() = runTest {
    val pending = CompletableDeferred<Result<Unit>>()
    coEvery { profileRepository.createProfile(any(), any()) } coAnswers { pending.await() }
    fillValidNameAndSurname()

    viewModel.onRoleSelected(Role.PATIENT, onSuccess)
    runCurrent()
    assertTrue(viewModel.uiState.value.isSaving)

    viewModel.onRoleSelected(Role.CAREGIVER, onSuccess)
    runCurrent()

    coVerify(exactly = 1) { profileRepository.createProfile(any(), any()) }

    pending.complete(Result.success(Unit))
    advanceUntilIdle()

    assertEquals(1, successCount)
    assertEquals(Role.PATIENT, lastSuccessRole)
    assertFalse(viewModel.uiState.value.isSaving)
  }

  @Test
  fun onRoleSelected_afterSuccessfulSave_reinvokesOnSuccessWithoutRepository() = runTest {
    coEvery { profileRepository.createProfile(any(), any()) } returns Result.success(Unit)
    fillValidNameAndSurname()

    viewModel.onRoleSelected(Role.PATIENT, onSuccess)
    advanceUntilIdle()
    assertEquals(1, successCount)
    assertEquals(Role.PATIENT, lastSuccessRole)

    viewModel.onRoleSelected(Role.CAREGIVER, onSuccess)
    advanceUntilIdle()

    coVerify(exactly = 1) { profileRepository.createProfile(any(), any()) }
    assertEquals(2, successCount)
    assertEquals(Role.PATIENT, lastSuccessRole)
    assertNull(viewModel.uiState.value.errorMsgRes)
  }

  private fun fillValidNameAndSurname() {
    viewModel.onNameChange("Ada")
    viewModel.onSurnameChange("Lovelace")
  }
}
