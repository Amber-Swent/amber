// this code was written by Alaabrine
// with the aid of an AI coding agent
package com.github.se.amber.ui.invitationRedemption

import com.github.se.amber.data.invitation.InvitationError
import com.github.se.amber.data.invitation.InvitationRedemptionRepository
import com.github.se.amber.data.invitation.InvitationRedemptionResult
import com.github.se.amber.model.user.Role
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlin.coroutines.cancellation.CancellationException
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
 * Unit tests for [RedeemInviteViewModel] with a mocked [InvitationRedemptionRepository].
 *
 * Repository Firebase/Functions coverage lives in FirebaseInvitationRedemptionRepositoryTest; this
 * class only verifies ViewModel validation and UI-state transitions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RedeemInviteViewModelTest {

  private lateinit var invitationRedemptionRepository: InvitationRedemptionRepository
  private var successCount = 0
  private var lastSuccessResult: InvitationRedemptionResult? = null
  private lateinit var viewModel: RedeemInviteViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
    invitationRedemptionRepository = mockk()
    successCount = 0
    lastSuccessResult = null
    viewModel =
        RedeemInviteViewModel(
            invitationRedemptionRepository = invitationRedemptionRepository,
            onSuccess = { result ->
              successCount++
              lastSuccessResult = result
            },
        )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkAll()
  }

  // ---------- Initial state ----------

  @Test
  fun initialState_hasEmptyCodeAndNoErrors() {
    assertEquals(RedeemInviteUiState(), viewModel.uiState.value)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  // ---------- onInviteCodeChange ----------

  @Test
  fun onInviteCodeChange_updatesInviteCodeInState() {
    viewModel.onInviteCodeChange("ABC-123")

    assertEquals("ABC-123", viewModel.uiState.value.inviteCode)
  }

  @Test
  fun onInviteCodeChange_clearsRedemptionError() = runTest {
    coEvery { invitationRedemptionRepository.redeemInvitation(any()) } returns
        Result.failure(InvitationError.InvalidCode)
    viewModel.onInviteCodeChange("bad-code")
    viewModel.onSubmit()
    advanceUntilIdle()
    assertEquals(
        InvitationError.InvalidCode.toActionableMessage(),
        viewModel.uiState.value.errorMessage,
    )

    viewModel.onInviteCodeChange("ABC-123")

    assertNull(viewModel.uiState.value.redemptionError)
    assertNull(viewModel.uiState.value.errorMessage)
    assertEquals("ABC-123", viewModel.uiState.value.inviteCode)
  }

  // ---------- Validation (no repository call) ----------

  @Test
  fun onSubmit_blankCode_setsDerivedErrorAndDoesNotCallRepository() = runTest {
    viewModel.onSubmit()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state.validationAttempted)
    assertEquals(CODE_REQUIRED_MESSAGE, state.errorMessage)
    assertEquals("", state.inviteCode)
    assertFalse(state.isRedeeming)
    assertEquals(0, successCount)
    coVerify(exactly = 0) { invitationRedemptionRepository.redeemInvitation(any()) }
  }

  @Test
  fun onSubmit_whitespaceOnlyCode_treatedAsBlankAndDoesNotCallRepository() = runTest {
    viewModel.onInviteCodeChange("   ")

    viewModel.onSubmit()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(CODE_REQUIRED_MESSAGE, state.errorMessage)
    assertEquals("", state.inviteCode)
    coVerify(exactly = 0) { invitationRedemptionRepository.redeemInvitation(any()) }
  }

  // ---------- Successful redeem (mocked repository) ----------

  @Test
  fun onSubmit_patientSuccess_invokesOnSuccessAndClearsSaving() = runTest {
    val result = InvitationRedemptionResult(careCircleId = "circle-1", role = Role.PATIENT)
    coEvery { invitationRedemptionRepository.redeemInvitation(any()) } returns
        Result.success(result)
    viewModel.onInviteCodeChange("  invite-code  ")

    viewModel.onSubmit()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isRedeeming)
    assertNull(state.errorMessage)
    assertEquals(1, successCount)
    assertEquals(result, lastSuccessResult)
    assertEquals("invite-code", state.inviteCode)
    coVerify(exactly = 1) { invitationRedemptionRepository.redeemInvitation("invite-code") }
  }

  @Test
  fun onSubmit_caregiverSuccess_invokesOnSuccess() = runTest {
    val result = InvitationRedemptionResult(careCircleId = "circle-2", role = Role.CAREGIVER)
    coEvery { invitationRedemptionRepository.redeemInvitation(any()) } returns
        Result.success(result)
    viewModel.onInviteCodeChange("caregiver-code")

    viewModel.onSubmit()
    advanceUntilIdle()

    assertEquals(1, successCount)
    assertEquals(result, lastSuccessResult)
    assertEquals(Role.CAREGIVER, lastSuccessResult?.role)
    coVerify(exactly = 1) { invitationRedemptionRepository.redeemInvitation("caregiver-code") }
  }

  // ---------- Repository failures ----------

  @Test
  fun onSubmit_invalidCode_setsMappedError() = runTest {
    coEvery { invitationRedemptionRepository.redeemInvitation(any()) } returns
        Result.failure(InvitationError.InvalidCode)
    viewModel.onInviteCodeChange("bad-code")

    viewModel.onSubmit()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isRedeeming)
    assertEquals(InvitationError.InvalidCode.toActionableMessage(), state.errorMessage)
    assertEquals(0, successCount)
  }

  @Test
  fun onSubmit_invitationNotFound_setsMappedError() = runTest {
    coEvery { invitationRedemptionRepository.redeemInvitation(any()) } returns
        Result.failure(InvitationError.InvitationNotFound)
    viewModel.onInviteCodeChange("missing-code")

    viewModel.onSubmit()
    advanceUntilIdle()

    assertEquals(
        InvitationError.InvitationNotFound.toActionableMessage(),
        viewModel.uiState.value.errorMessage,
    )
    assertEquals(0, successCount)
  }

  @Test
  fun onSubmit_networkError_setsMappedOfflineError() = runTest {
    coEvery { invitationRedemptionRepository.redeemInvitation(any()) } returns
        Result.failure(InvitationError.Network)
    viewModel.onInviteCodeChange("net-code")

    viewModel.onSubmit()
    advanceUntilIdle()

    assertEquals(
        InvitationError.Network.toActionableMessage(),
        viewModel.uiState.value.errorMessage,
    )
    assertEquals(0, successCount)
    assertFalse(viewModel.uiState.value.isRedeeming)
  }

  @Test
  fun onSubmit_mapsEachInvitationErrorDistinctly() {
    val messages =
        listOf(
                InvitationError.InvalidCode,
                InvitationError.InvitationNotFound,
                InvitationError.InvitationExpired,
                InvitationError.InvitationAlreadyUsed,
                InvitationError.UserAlreadyBelongsToCircle,
                InvitationError.MissingOrInvalidUserProfile,
                InvitationError.InvalidOrUnavailableCareCircle,
                InvitationError.PatientSlotAlreadyOccupied,
                InvitationError.Network,
                InvitationError.Unknown(IllegalStateException("x")),
            )
            .map { it.toActionableMessage() }

    assertEquals(messages.size, messages.toSet().size)
  }

  @Test
  fun onSubmit_clearsPreviousErrorBeforeRetry() = runTest {
    coEvery { invitationRedemptionRepository.redeemInvitation(any()) } returnsMany
        listOf(
            Result.failure(InvitationError.InvalidCode),
            Result.success(
                InvitationRedemptionResult(careCircleId = "circle-1", role = Role.PATIENT),
            ),
        )
    viewModel.onInviteCodeChange("retry-code")

    viewModel.onSubmit()
    advanceUntilIdle()
    assertEquals(
        InvitationError.InvalidCode.toActionableMessage(),
        viewModel.uiState.value.errorMessage,
    )

    viewModel.onSubmit()
    advanceUntilIdle()

    assertNull(viewModel.uiState.value.errorMessage)
    assertEquals(1, successCount)
    assertEquals(Role.PATIENT, lastSuccessResult?.role)
  }

  // ---------- Concurrent redeem guard ----------

  @Test
  fun onSubmit_whileRedeeming_ignoresSecondCall() = runTest {
    val pending = CompletableDeferred<Result<InvitationRedemptionResult>>()
    coEvery { invitationRedemptionRepository.redeemInvitation(any()) } coAnswers { pending.await() }
    viewModel.onInviteCodeChange("pending-code")

    viewModel.onSubmit()
    runCurrent()
    assertTrue(viewModel.uiState.value.isRedeeming)

    viewModel.onSubmit()
    runCurrent()

    coVerify(exactly = 1) { invitationRedemptionRepository.redeemInvitation(any()) }

    pending.complete(
        Result.success(InvitationRedemptionResult(careCircleId = "circle-1", role = Role.PATIENT)),
    )
    advanceUntilIdle()

    assertEquals(1, successCount)
    assertEquals(Role.PATIENT, lastSuccessResult?.role)
    assertFalse(viewModel.uiState.value.isRedeeming)
  }

  @Test
  fun onSubmit_afterSuccessfulRedeem_ignoresSecondCall() = runTest {
    val result = InvitationRedemptionResult(careCircleId = "circle-1", role = Role.PATIENT)
    coEvery { invitationRedemptionRepository.redeemInvitation(any()) } returns
        Result.success(result)
    viewModel.onInviteCodeChange("code")

    viewModel.onSubmit()
    advanceUntilIdle()
    assertEquals(1, successCount)

    viewModel.onSubmit()
    advanceUntilIdle()

    coVerify(exactly = 1) { invitationRedemptionRepository.redeemInvitation(any()) }
    assertEquals(1, successCount)
  }

  @Test
  fun onSubmit_cancellationException_doesNotCompleteRedeem() = runTest {
    coEvery { invitationRedemptionRepository.redeemInvitation(any()) } coAnswers
        {
          throw CancellationException("redeem cancelled")
        }
    viewModel.onInviteCodeChange("code")

    viewModel.onSubmit()
    advanceUntilIdle()

    // Catch rethrows cancellation before success/failure state is written.
    assertTrue(viewModel.uiState.value.isRedeeming)
    assertEquals(0, successCount)
    assertNull(lastSuccessResult)
    assertNull(viewModel.uiState.value.errorMessage)
  }
}
