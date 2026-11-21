// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.caregiver.parameters.changeCareCircle

import android.util.Log
import com.github.se.amber.R
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.user.UserProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChangeCareCircleViewModelTest {
  private val profileRepository = mockk<ProfileRepository>()
  private val circleRepository = mockk<CareCircleRepository>()
  private val activeCircleRepository = mockk<ActiveCircleRepository>()
  private val activeId = MutableStateFlow<String?>("first")
  private val profile = UserProfile(uid = "caregiver", circleIds = listOf("first", "second"))

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    mockkStatic(Log::class)
    every { Log.e(any(), any(), any()) } returns 0
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(profile)
    coEvery { circleRepository.getCircle("first") } returns
        Result.success(CareCircle(id = "first", name = "Family"))
    coEvery { circleRepository.getCircle("second") } returns
        Result.success(CareCircle(id = "second", name = "Friends"))
    every { activeCircleRepository.activeCircleId(any(), any()) } returns activeId
    coEvery { activeCircleRepository.setActiveCircle(any(), any()) } returns Unit
  }

  @After
  fun tearDown() {
    unmockkAll()
    Dispatchers.resetMain()
  }

  private fun createViewModel() =
      ChangeCareCircleViewModel(
          profileRepository = profileRepository,
          circleRepository = circleRepository,
          activeCircleRepository = activeCircleRepository,
      )

  @Test
  fun startsLoadingUntilProfileCompletes() {
    val pendingProfile = CompletableDeferred<Result<UserProfile?>>()
    coEvery { profileRepository.getCurrentProfile() } coAnswers { pendingProfile.await() }
    val viewModel = createViewModel()

    assertEquals(ChangeCareCircleStatus.LOADING, viewModel.uiState.value.status)
    pendingProfile.complete(Result.success(profile))
    assertEquals(ChangeCareCircleStatus.READY, viewModel.uiState.value.status)
  }

  @Test
  fun exposesProvidedStateWithoutLoading() {
    val initialState =
        ChangeCareCircleUIState(
            status = ChangeCareCircleStatus.READY,
            circles = listOf(ChangeCareCircleChoice("circle", "Family")),
            activeCircleId = "circle",
        )

    val viewModel =
        ChangeCareCircleViewModel(
            initialState = initialState,
            profileRepository = profileRepository,
            circleRepository = circleRepository,
            activeCircleRepository = activeCircleRepository,
        )

    assertEquals(initialState, viewModel.uiState.value)
    coVerify(exactly = 0) { profileRepository.getCurrentProfile() }
  }

  @Test
  fun loadsDistinctCirclesAndActiveSelection() {
    coEvery { profileRepository.getCurrentProfile() } returns
        Result.success(profile.copy(circleIds = listOf("", "first", "second", "first")))

    val state = createViewModel().uiState.value

    assertEquals(ChangeCareCircleStatus.READY, state.status)
    assertEquals(
        listOf(
            ChangeCareCircleChoice("first", "Family"),
            ChangeCareCircleChoice("second", "Friends"),
        ),
        state.circles,
    )
    assertEquals("first", state.activeCircleId)
    coVerify(exactly = 1) { circleRepository.getCircle("first") }
  }

  @Test
  fun emptyMembershipShowsEmptyState() {
    coEvery { profileRepository.getCurrentProfile() } returns
        Result.success(profile.copy(circleIds = listOf("", " ")))

    assertEquals(ChangeCareCircleStatus.EMPTY, createViewModel().uiState.value.status)
    coVerify(exactly = 0) { circleRepository.getCircle(any()) }
  }

  @Test
  fun profileFailureShowsBlockingError() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.failure(Exception("offline"))

    val state = createViewModel().uiState.value

    assertEquals(ChangeCareCircleStatus.ERROR, state.status)
    assertEquals(R.string.changeCareCircleErrorLoadProfile, state.errorMsgRes)
  }

  @Test
  fun missingProfileShowsBlockingError() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(null)

    assertEquals(
        R.string.changeCareCircleErrorLoadProfile,
        createViewModel().uiState.value.errorMsgRes,
    )
  }

  @Test
  fun blankProfileUidShowsBlockingError() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(profile.copy(uid = ""))

    assertEquals(
        R.string.changeCareCircleErrorLoadProfile,
        createViewModel().uiState.value.errorMsgRes,
    )
  }

  @Test
  fun circleFailureShowsBlockingError() {
    coEvery { circleRepository.getCircle("second") } returns Result.failure(Exception("offline"))

    val state = createViewModel().uiState.value

    assertEquals(ChangeCareCircleStatus.ERROR, state.status)
    assertEquals(R.string.changeCareCircleErrorLoadCircle, state.errorMsgRes)
  }

  @Test
  fun unexpectedLoadExceptionShowsBlockingError() {
    every { activeCircleRepository.activeCircleId(any(), any()) } throws
        IllegalStateException("bad")

    assertEquals(
        R.string.changeCareCircleErrorLoadCircle,
        createViewModel().uiState.value.errorMsgRes,
    )
  }

  @Test
  fun activeFlowFailureShowsBlockingError() {
    every { activeCircleRepository.activeCircleId(any(), any()) } returns
        flow { throw IllegalStateException("bad") }

    assertEquals(ChangeCareCircleStatus.ERROR, createViewModel().uiState.value.status)
  }

  @Test
  fun observesChangesToTheActiveCircle() {
    val viewModel = createViewModel()

    activeId.value = "second"

    assertEquals("second", viewModel.uiState.value.activeCircleId)
  }

  @Test
  fun retryReloadsAfterFailure() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.failure(Exception("offline"))
    val viewModel = createViewModel()
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(profile)

    viewModel.loadCareCircles()

    assertEquals(ChangeCareCircleStatus.READY, viewModel.uiState.value.status)
    assertNull(viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun validSelectionIsPersistedBeforeStateChanges() {
    val pendingWrite = CompletableDeferred<Unit>()
    coEvery { activeCircleRepository.setActiveCircle("caregiver", "second") } coAnswers
        {
          pendingWrite.await()
        }
    val viewModel = createViewModel()

    viewModel.changeActiveCareCircle("second")
    assertEquals("first", viewModel.uiState.value.activeCircleId)
    pendingWrite.complete(Unit)

    coVerify(exactly = 1) { activeCircleRepository.setActiveCircle("caregiver", "second") }
    assertEquals("second", viewModel.uiState.value.activeCircleId)
  }

  @Test
  fun invalidAndCurrentSelectionsDoNotWrite() {
    val viewModel = createViewModel()

    viewModel.changeActiveCareCircle("unknown")
    viewModel.changeActiveCareCircle("first")

    coVerify(exactly = 0) { activeCircleRepository.setActiveCircle(any(), any()) }
  }

  @Test
  fun selectionBeforeLoadIsIgnored() {
    val pendingProfile = CompletableDeferred<Result<UserProfile?>>()
    coEvery { profileRepository.getCurrentProfile() } coAnswers { pendingProfile.await() }
    val viewModel = createViewModel()

    viewModel.changeActiveCareCircle("first")

    coVerify(exactly = 0) { activeCircleRepository.setActiveCircle(any(), any()) }
    pendingProfile.complete(Result.success(profile))
  }

  @Test
  fun secondSelectionIsIgnoredWhileWriteIsPending() {
    val pendingWrite = CompletableDeferred<Unit>()
    coEvery { activeCircleRepository.setActiveCircle("caregiver", "second") } coAnswers
        {
          pendingWrite.await()
        }
    val viewModel = createViewModel()

    viewModel.changeActiveCareCircle("second")
    viewModel.changeActiveCareCircle("second")

    coVerify(exactly = 1) { activeCircleRepository.setActiveCircle("caregiver", "second") }
    pendingWrite.complete(Unit)
  }

  @Test
  fun failedSelectionKeepsPreviousCircleAndCanRetry() {
    coEvery { activeCircleRepository.setActiveCircle("caregiver", "second") } throws
        IllegalStateException("offline")
    val viewModel = createViewModel()

    viewModel.changeActiveCareCircle("second")
    assertEquals("first", viewModel.uiState.value.activeCircleId)
    assertEquals(R.string.changeCareCircleErrorChange, viewModel.uiState.value.errorMsgRes)
    viewModel.clearErrorMsg()
    assertNull(viewModel.uiState.value.errorMsgRes)
    coEvery { activeCircleRepository.setActiveCircle("caregiver", "second") } returns Unit
    viewModel.changeActiveCareCircle("second")

    assertEquals("second", viewModel.uiState.value.activeCircleId)
  }

  @Test
  fun reloadCancelsPendingSelectionAndResetsState() {
    val pendingWrite = CompletableDeferred<Unit>()
    coEvery { activeCircleRepository.setActiveCircle("caregiver", "second") } coAnswers
        {
          pendingWrite.await()
        }
    val viewModel = createViewModel()
    viewModel.changeActiveCareCircle("second")
    coEvery { profileRepository.getCurrentProfile() } returns
        Result.success(profile.copy(circleIds = emptyList()))

    viewModel.loadCareCircles()

    assertEquals(ChangeCareCircleStatus.EMPTY, viewModel.uiState.value.status)
  }

  @Test
  fun selectionCompletionDoesNotReplaceBlockingError() {
    val failObservation = CompletableDeferred<Unit>()
    val pendingWrite = CompletableDeferred<Unit>()
    every { activeCircleRepository.activeCircleId(any(), any()) } returns
        flow {
          emit("first")
          failObservation.await()
          throw IllegalStateException("observation failed")
        }
    coEvery { activeCircleRepository.setActiveCircle("caregiver", "second") } coAnswers
        {
          pendingWrite.await()
        }
    val viewModel = createViewModel()

    viewModel.changeActiveCareCircle("second")
    failObservation.complete(Unit)
    pendingWrite.complete(Unit)
    viewModel.changeActiveCareCircle("second")

    assertEquals(ChangeCareCircleStatus.ERROR, viewModel.uiState.value.status)
    coVerify(exactly = 1) { activeCircleRepository.setActiveCircle("caregiver", "second") }
  }

  @Test
  fun staleSelectionDoesNotSelectCircleRemovedDuringReload() {
    val pendingWrite = CompletableDeferred<Unit>()
    coEvery { activeCircleRepository.setActiveCircle("caregiver", "second") } coAnswers
        {
          withContext(NonCancellable) { pendingWrite.await() }
        }
    val viewModel = createViewModel()
    viewModel.changeActiveCareCircle("second")
    coEvery { profileRepository.getCurrentProfile() } returns
        Result.success(profile.copy(circleIds = listOf("first")))

    viewModel.loadCareCircles()
    pendingWrite.complete(Unit)

    assertEquals(ChangeCareCircleStatus.READY, viewModel.uiState.value.status)
    assertEquals(listOf(ChangeCareCircleChoice("first", "Family")), viewModel.uiState.value.circles)
    assertEquals("first", viewModel.uiState.value.activeCircleId)
  }
}
