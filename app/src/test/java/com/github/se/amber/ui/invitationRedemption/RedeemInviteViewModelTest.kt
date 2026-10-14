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
  private lateinit var viewModel: RedeemInviteViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
    invitationRedemptionRepository = mockk()
    viewModel = RedeemInviteViewModel(invitationRedemptionRepository)
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
  }

  // ---------- onInviteCodeChange ----------

  @Test
  fun onInviteCodeChange_updatesInviteCodeInState() {
    viewModel.onInviteCodeChange("ABC-123")

    assertEquals("ABC-123", viewModel.uiState.value.inviteCode)
  }

  @Test
  fun onInviteCodeChange_clearsShowError() {
    viewModel.onSubmit()
    assertTrue(viewModel.uiState.value.showError)

    viewModel.onInviteCodeChange("ABC-123")

    assertFalse(viewModel.uiState.value.showError)
    assertEquals("ABC-123", viewModel.uiState.value.inviteCode)
  }

  // ---------- Validation (no repository call) ----------

  @Test
  fun onSubmit_blankCode_setsErrorAndDoesNotCallRepository() = runTest {
    viewModel.onSubmit()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state.showError)
    assertEquals("", state.inviteCode)
    assertFalse(state.isRedeeming)
    assertNull(state.redemptionResult)
    coVerify(exactly = 0) { invitationRedemptionRepository.redeemInvitation(any()) }
  }

  @Test
  fun onSubmit_whitespaceOnlyCode_treatedAsBlankAndDoesNotCallRepository() = runTest {
    viewModel.onInviteCodeChange("   ")

    viewModel.onSubmit()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state.showError)
    assertEquals("", state.inviteCode)
    coVerify(exactly = 0) { invitationRedemptionRepository.redeemInvitation(any()) }
  }

  // ---------- Successful redeem (mocked repository) ----------

  @Test
  fun onSubmit_patientSuccess_setsRedemptionResultAndClearsSaving() = runTest {
    val result = InvitationRedemptionResult(careCircleId = "circle-1", role = Role.PATIENT)
    coEvery { invitationRedemptionRepository.redeemInvitation(any()) } returns
        Result.success(result)
    viewModel.onInviteCodeChange("  invite-code  ")

    viewModel.onSubmit()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isRedeeming)
    assertFalse(state.showError)
    assertEquals(result, state.redemptionResult)
    assertEquals("invite-code", state.inviteCode)
    coVerify(exactly = 1) { invitationRedemptionRepository.redeemInvitation("invite-code") }
  }

  @Test
  fun onSubmit_caregiverSuccess_setsRedemptionResult() = runTest {
    val result = InvitationRedemptionResult(careCircleId = "circle-2", role = Role.CAREGIVER)
    coEvery { invitationRedemptionRepository.redeemInvitation(any()) } returns
        Result.success(result)
    viewModel.onInviteCodeChange("caregiver-code")

    viewModel.onSubmit()
    advanceUntilIdle()

    assertEquals(result, viewModel.uiState.value.redemptionResult)
    assertEquals(Role.CAREGIVER, viewModel.uiState.value.redemptionResult?.role)
    coVerify(exactly = 1) { invitationRedemptionRepository.redeemInvitation("caregiver-code") }
  }

  // ---------- Repository failures ----------

  @Test
  fun onSubmit_invalidCode_setsShowError() = runTest {
    coEvery { invitationRedemptionRepository.redeemInvitation(any()) } returns
        Result.failure(InvitationError.InvalidCode)
    viewModel.onInviteCodeChange("bad-code")

    viewModel.onSubmit()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isRedeeming)
    assertTrue(state.showError)
    assertNull(state.redemptionResult)
  }

  @Test
  fun onSubmit_invitationNotFound_setsShowError() = runTest {
    coEvery { invitationRedemptionRepository.redeemInvitation(any()) } returns
        Result.failure(InvitationError.InvitationNotFound)
    viewModel.onInviteCodeChange("missing-code")

    viewModel.onSubmit()
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.showError)
    assertNull(viewModel.uiState.value.redemptionResult)
  }

  @Test
  fun onSubmit_networkError_setsShowError() = runTest {
    coEvery { invitationRedemptionRepository.redeemInvitation(any()) } returns
        Result.failure(InvitationError.Network)
    viewModel.onInviteCodeChange("net-code")

    viewModel.onSubmit()
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.showError)
    assertNull(viewModel.uiState.value.redemptionResult)
    assertFalse(viewModel.uiState.value.isRedeeming)
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
    assertTrue(viewModel.uiState.value.showError)

    viewModel.onSubmit()
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.showError)
    assertEquals(Role.PATIENT, viewModel.uiState.value.redemptionResult?.role)
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

    assertEquals(Role.PATIENT, viewModel.uiState.value.redemptionResult?.role)
    assertFalse(viewModel.uiState.value.isRedeeming)
  }

  // ---------- Navigation event consumed ----------

  @Test
  fun onRedemptionResultHandled_clearsResult() = runTest {
    val result = InvitationRedemptionResult(careCircleId = "circle-1", role = Role.CAREGIVER)
    coEvery { invitationRedemptionRepository.redeemInvitation(any()) } returns
        Result.success(result)
    viewModel.onInviteCodeChange("code")

    viewModel.onSubmit()
    advanceUntilIdle()
    assertEquals(result, viewModel.uiState.value.redemptionResult)

    viewModel.onRedemptionResultHandled()

    assertNull(viewModel.uiState.value.redemptionResult)
    assertEquals("code", viewModel.uiState.value.inviteCode)
  }

  @Test
  fun onRedemptionResultHandled_whenNull_isNoOp() {
    viewModel.onRedemptionResultHandled()

    assertNull(viewModel.uiState.value.redemptionResult)
  }
}
