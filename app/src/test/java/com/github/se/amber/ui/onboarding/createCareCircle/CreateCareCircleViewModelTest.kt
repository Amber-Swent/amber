// This file was written with the aid of AI.
// Modified by Yuhan YANG, with assistance from Cursor.
package com.github.se.amber.ui.onboarding

import com.github.se.amber.data.onboarding.OnboardingError
import com.github.se.amber.data.onboarding.OnboardingRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.ui.onboarding.createCareCircle.CreateCareCircleUIState
import com.github.se.amber.ui.onboarding.createCareCircle.CreateCareCircleViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

/** Exercises onboarding behavior with a local repository; no Firebase service is contacted. */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateCareCircleViewModelTest {
  private val createdCircle = CareCircle(id = "circle-1", name = "Family")
  private lateinit var repository: FakeOnboardingRepository
  private lateinit var viewModel: CreateCareCircleViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
    repository = FakeOnboardingRepository(Result.success(createdCircle))
    viewModel = CreateCareCircleViewModel(repository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun initialStateIsEmptyAndIdle() {
    assertEquals(CreateCareCircleUIState(), viewModel.uiState.value)
  }

  @Test
  fun typingStoresTheExactNameAndClearsValidationError() {
    viewModel.onNameChange(" ")
    assertEquals("Circle name cannot be empty", viewModel.uiState.value.nameError)

    viewModel.onNameChange("  Family  ")
    assertEquals("  Family  ", viewModel.uiState.value.name)
    assertNull(viewModel.uiState.value.nameError)
  }

  @Test
  fun blankSubmissionShowsFieldErrorWithoutCallingRepository() = runTest {
    viewModel.createCareCircle()
    advanceUntilIdle()

    assertEquals("Circle name cannot be empty", viewModel.uiState.value.nameError)
    assertFalse(viewModel.uiState.value.isLoading)
    assertTrue(repository.submittedNames.isEmpty())
  }

  @Test
  fun submissionTrimsNameBeforeCreatingCircle() = runTest {
    viewModel.onNameChange("  Family  ")
    viewModel.createCareCircle()
    advanceUntilIdle()

    assertEquals(listOf("Family"), repository.submittedNames)
    assertTrue(viewModel.uiState.value.isCreated)
  }

  @Test
  fun successfulCreationEmitsEventAndDisablesFurtherEdits() = runTest {
    viewModel.onNameChange("Family")
    viewModel.createCareCircle()
    advanceUntilIdle()

    assertEquals(Unit, viewModel.successEvents.first())
    assertFalse(viewModel.uiState.value.isLoading)
    assertTrue(viewModel.uiState.value.isCreated)
    viewModel.onNameChange("Other")
    viewModel.createCareCircle()
    advanceUntilIdle()
    assertEquals("Family", viewModel.uiState.value.name)
    assertEquals(listOf("Family"), repository.submittedNames)
  }

  @Test
  fun loadingBlocksDuplicateSubmissionAndEditing() = runTest {
    val pending = CompletableDeferred<Result<CareCircle>>()
    repository.pending = pending
    viewModel.onNameChange("Family")
    viewModel.createCareCircle()
    runCurrent()

    viewModel.onNameChange("Other")
    viewModel.createCareCircle()
    assertTrue(viewModel.uiState.value.isLoading)
    assertEquals("Family", viewModel.uiState.value.name)
    assertEquals(listOf("Family"), repository.submittedNames)

    pending.complete(Result.success(createdCircle))
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.isCreated)
  }

  @Test
  fun networkFailureLeavesFormReadyForRetry() = runTest {
    repository.result = Result.failure(OnboardingError.Network)
    viewModel.onNameChange("Family")
    viewModel.createCareCircle()
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertFalse(viewModel.uiState.value.isCreated)
    assertEquals("Family", viewModel.uiState.value.name)
    assertEquals(
        "You're offline. Check your connection and try again.",
        viewModel.errorEvents.first(),
    )

    repository.result = Result.success(createdCircle)
    viewModel.createCareCircle()
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.isCreated)
  }

  @Test
  fun thrownRepositoryExceptionShowsGenericRetryMessage() = runTest {
    repository.thrown = IllegalStateException("internal detail")
    viewModel.onNameChange("Family")
    viewModel.createCareCircle()
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(
        "Couldn't create your care circle. Please try again.",
        viewModel.errorEvents.first(),
    )
  }

  @Test
  fun authenticationFailureExplainsHowToRecover() = runTest {
    repository.result = Result.failure(OnboardingError.NotAuthenticated)
    viewModel.onNameChange("Family")
    viewModel.createCareCircle()
    advanceUntilIdle()

    assertEquals(
        "Your session expired. Sign in and try again.",
        viewModel.errorEvents.first(),
    )
  }

  @Test
  fun permissionFailurePreservesEnteredNameAndEmitsError() = runTest {
    repository.result = Result.failure(OnboardingError.PermissionDenied)
    viewModel.onNameChange("Family")
    viewModel.createCareCircle()
    advanceUntilIdle()

    assertEquals("Family", viewModel.uiState.value.name)
    assertFalse(viewModel.uiState.value.isCreated)
    assertFalse(viewModel.uiState.value.isLoading)

    assertEquals(
        "Creation was denied. Check your account and try again.",
        viewModel.errorEvents.first(),
    )
  }

  private class FakeOnboardingRepository(var result: Result<CareCircle>) : OnboardingRepository {
    val submittedNames = mutableListOf<String>()
    var pending: CompletableDeferred<Result<CareCircle>>? = null
    var thrown: Exception? = null

    override suspend fun createInitialCareCircle(name: String): Result<CareCircle> {
      submittedNames += name
      thrown?.let { throw it }
      return pending?.await() ?: result
    }
  }
}
