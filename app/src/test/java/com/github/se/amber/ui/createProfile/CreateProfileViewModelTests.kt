// this code was written by Alaabrine
// with the aid of an AI coding agent
package com.github.se.amber.ui.createProfile

import com.github.se.amber.data.profile.FirebaseProfileRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.ui.accountCreation.CreateProfileUiState
import com.github.se.amber.ui.accountCreation.CreateProfileViewModel
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
  private lateinit var viewModel: CreateProfileViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
    profileRepository = mockk()
    viewModel = CreateProfileViewModel(profileRepository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkAll()
  }

  // ---------- Initial state ----------

  @Test
  fun initialState_hasEmptyFieldsAndNoErrors() {
    assertEquals(CreateProfileUiState(), viewModel.uiState.value)
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
  fun onNameChange_clearsNameErrorWhenValueIsNonBlank() {
    viewModel.onRoleSelected(Role.PATIENT)
    assertTrue(viewModel.uiState.value.nameError)

    viewModel.onNameChange("Ada")

    assertFalse(viewModel.uiState.value.nameError)
    assertEquals("Ada", viewModel.uiState.value.name)
  }

  @Test
  fun onSurnameChange_clearsSurnameErrorWhenValueIsNonBlank() {
    viewModel.onRoleSelected(Role.PATIENT)
    assertTrue(viewModel.uiState.value.surnameError)

    viewModel.onSurnameChange("Lovelace")

    assertFalse(viewModel.uiState.value.surnameError)
    assertEquals("Lovelace", viewModel.uiState.value.surname)
  }

  @Test
  fun onNameChange_keepsNameErrorWhenValueIsStillBlank() {
    viewModel.onRoleSelected(Role.PATIENT)
    assertTrue(viewModel.uiState.value.nameError)

    viewModel.onNameChange("   ")

    assertTrue(viewModel.uiState.value.nameError)
  }

  // ---------- Validation (no repository call) ----------

  @Test
  fun onRoleSelected_blankFields_setsErrorsAndDoesNotCallRepository() = runTest {
    viewModel.onRoleSelected(Role.PATIENT)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state.nameError)
    assertTrue(state.surnameError)
    assertEquals("", state.name)
    assertEquals("", state.surname)
    assertFalse(state.isSaving)
    assertNull(state.submitError)
    assertNull(state.createdRole)
    coVerify(exactly = 0) { profileRepository.createProfile(any(), any()) }
  }

  @Test
  fun onRoleSelected_whitespaceOnlyFields_treatedAsBlankAndDoesNotCallRepository() = runTest {
    viewModel.onNameChange("   ")
    viewModel.onSurnameChange("\t")

    viewModel.onRoleSelected(Role.CAREGIVER)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state.nameError)
    assertTrue(state.surnameError)
    assertEquals("", state.name)
    assertEquals("", state.surname)
    coVerify(exactly = 0) { profileRepository.createProfile(any(), any()) }
  }

  @Test
  fun onRoleSelected_blankNameOnly_setsNameErrorAndDoesNotCallRepository() = runTest {
    viewModel.onSurnameChange("Lovelace")

    viewModel.onRoleSelected(Role.PATIENT)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state.nameError)
    assertFalse(state.surnameError)
    assertEquals("Lovelace", state.surname)
    coVerify(exactly = 0) { profileRepository.createProfile(any(), any()) }
  }

  @Test
  fun onRoleSelected_blankSurnameOnly_setsSurnameErrorAndDoesNotCallRepository() = runTest {
    viewModel.onNameChange("Ada")

    viewModel.onRoleSelected(Role.PATIENT)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.nameError)
    assertTrue(state.surnameError)
    assertEquals("Ada", state.name)
    coVerify(exactly = 0) { profileRepository.createProfile(any(), any()) }
  }

  // ---------- Successful create (mocked repository) ----------

  @Test
  fun onRoleSelected_patientSuccess_setsCreatedRoleAndClearsSaving() = runTest {
    coEvery { profileRepository.createProfile(any(), any()) } returns Result.success(Unit)
    viewModel.onNameChange("  Ada  ")
    viewModel.onSurnameChange("  Lovelace  ")

    viewModel.onRoleSelected(Role.PATIENT)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isSaving)
    assertNull(state.submitError)
    assertEquals(Role.PATIENT, state.createdRole)
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
  fun onRoleSelected_caregiverSuccess_setsCreatedRole() = runTest {
    coEvery { profileRepository.createProfile(any(), any()) } returns Result.success(Unit)
    viewModel.onNameChange("Grace")
    viewModel.onSurnameChange("Hopper")

    viewModel.onRoleSelected(Role.CAREGIVER)
    advanceUntilIdle()

    assertEquals(Role.CAREGIVER, viewModel.uiState.value.createdRole)
    coVerify(exactly = 1) {
      profileRepository.createProfile(
          person = Person(firstName = "Grace", lastName = "Hopper"),
          role = Role.CAREGIVER,
      )
    }
  }

  // ---------- Repository failures ----------

  @Test
  fun onRoleSelected_profileCreationError_setsSubmitErrorMessage() = runTest {
    coEvery { profileRepository.createProfile(any(), any()) } returns
        Result.failure(FirebaseProfileRepository.ProfileCreationError.ProfileAlreadyExists)
    fillValidNameAndSurname()

    viewModel.onRoleSelected(Role.PATIENT)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isSaving)
    assertNull(state.createdRole)
    assertEquals(
        FirebaseProfileRepository.ProfileCreationError.ProfileAlreadyExists.message,
        state.submitError,
    )
  }

  @Test
  fun onRoleSelected_networkError_setsOfflineSubmitError() = runTest {
    coEvery { profileRepository.createProfile(any(), any()) } returns
        Result.failure(FirebaseProfileRepository.ProfileCreationError.Network)
    fillValidNameAndSurname()

    viewModel.onRoleSelected(Role.CAREGIVER)
    advanceUntilIdle()

    assertEquals(
        FirebaseProfileRepository.ProfileCreationError.Network.message,
        viewModel.uiState.value.submitError,
    )
    assertNull(viewModel.uiState.value.createdRole)
  }

  @Test
  fun onRoleSelected_genericFailure_usesThrowableMessageOrFallback() = runTest {
    coEvery { profileRepository.createProfile(any(), any()) } returns
        Result.failure(IllegalStateException("boom"))
    fillValidNameAndSurname()

    viewModel.onRoleSelected(Role.PATIENT)
    advanceUntilIdle()

    assertEquals("boom", viewModel.uiState.value.submitError)
    assertFalse(viewModel.uiState.value.isSaving)
  }

  @Test
  fun onRoleSelected_clearsPreviousSubmitErrorBeforeRetry() = runTest {
    coEvery { profileRepository.createProfile(any(), any()) } returnsMany
        listOf(
            Result.failure(FirebaseProfileRepository.ProfileCreationError.Network),
            Result.success(Unit),
        )
    fillValidNameAndSurname()

    viewModel.onRoleSelected(Role.PATIENT)
    advanceUntilIdle()
    assertEquals(
        FirebaseProfileRepository.ProfileCreationError.Network.message,
        viewModel.uiState.value.submitError,
    )

    viewModel.onRoleSelected(Role.PATIENT)
    advanceUntilIdle()

    assertNull(viewModel.uiState.value.submitError)
    assertEquals(Role.PATIENT, viewModel.uiState.value.createdRole)
  }

  // ---------- Concurrent save guard ----------

  @Test
  fun onRoleSelected_whileSaving_ignoresSecondCall() = runTest {
    val pending = CompletableDeferred<Result<Unit>>()
    coEvery { profileRepository.createProfile(any(), any()) } coAnswers { pending.await() }
    fillValidNameAndSurname()

    viewModel.onRoleSelected(Role.PATIENT)
    runCurrent()
    assertTrue(viewModel.uiState.value.isSaving)

    viewModel.onRoleSelected(Role.CAREGIVER)
    runCurrent()

    coVerify(exactly = 1) { profileRepository.createProfile(any(), any()) }

    pending.complete(Result.success(Unit))
    advanceUntilIdle()

    assertEquals(Role.PATIENT, viewModel.uiState.value.createdRole)
    assertFalse(viewModel.uiState.value.isSaving)
  }

  @Test
  fun onRoleSelected_afterSuccessfulSave_ignoresSecondCall() = runTest {
    coEvery { profileRepository.createProfile(any(), any()) } returns Result.success(Unit)
    fillValidNameAndSurname()

    viewModel.onRoleSelected(Role.PATIENT)
    advanceUntilIdle()
    assertEquals(Role.PATIENT, viewModel.uiState.value.createdRole)

    viewModel.onRoleSelected(Role.CAREGIVER)
    advanceUntilIdle()

    coVerify(exactly = 1) { profileRepository.createProfile(any(), any()) }
    assertEquals(Role.PATIENT, viewModel.uiState.value.createdRole)
    assertNull(viewModel.uiState.value.submitError)
  }

  private fun fillValidNameAndSurname() {
    viewModel.onNameChange("Ada")
    viewModel.onSurnameChange("Lovelace")
  }
}
