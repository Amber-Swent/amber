// Written by Alaabrine with the aid of an AI coding agent
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.invitationRedemption

import com.github.se.amber.R
import com.github.se.amber.data.invitation.InvitationError
import com.github.se.amber.data.invitation.InvitationRedemptionRepository
import com.github.se.amber.data.invitation.InvitationRedemptionResult
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.validation.InputValidation
import com.github.se.amber.ui.onboarding.invitationRedemption.InvitationRedemptionUIState
import com.github.se.amber.ui.onboarding.invitationRedemption.InvitationRedemptionViewModel
import com.github.se.amber.ui.onboarding.invitationRedemption.errorMessage
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
 * Unit tests for
 * [com.github.se.amber.ui.onboarding.invitationRedemption.InvitationRedemptionViewModel] with a
 * mocked [InvitationRedemptionRepository].
 *
 * Repository Firebase/Functions coverage lives in FirebaseInvitationRedemptionRepositoryTest; this
 * class only verifies ViewModel validation and UI-state transitions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InvitationRedemptionViewModelTest {

  private lateinit var invitationRedemptionRepository: InvitationRedemptionRepository
  private var successCount = 0
  private var lastSuccessResult: InvitationRedemptionResult? = null
  private lateinit var viewModel: InvitationRedemptionViewModel
  private val onSuccess: (InvitationRedemptionResult) -> Unit = { result ->
    successCount++
    lastSuccessResult = result
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
    invitationRedemptionRepository = mockk()
    successCount = 0
    lastSuccessResult = null
    viewModel =
        InvitationRedemptionViewModel(
            invitationRedemptionRepository = invitationRedemptionRepository,
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
    assertEquals(InvitationRedemptionUIState(), viewModel.uiState.value)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  // ---------- onInviteCodeChange ----------

  @Test
  fun onInviteCodeChange_storesCodeInCapitalsWithoutSpacesCutAtCodeLength() {
    viewModel.onInviteCodeChange(" abcd 2345 xyz")

    assertEquals("ABCD2345", viewModel.uiState.value.inviteCode)
  }

  @Test
  fun onInviteCodeChange_characterCodesNeverUse_showsErrorAtOnceAndDisablesSubmit() {
    viewModel.onInviteCodeChange("AB0")

    val state = viewModel.uiState.value
    assertEquals(UiText.Resource(R.string.inputErrorInvitationCodeFormat), state.errorMessage)
    assertFalse(state.canSubmit)
  }

  @Test
  fun onSubmit_incompleteCode_setsErrorAndDoesNotCallRepository() = runTest {
    viewModel.onInviteCodeChange("ABCD")
    assertNull(viewModel.uiState.value.errorMessage) // still being typed

    viewModel.onSubmit(onSuccess)
    advanceUntilIdle()

    assertEquals(
        UiText.Resource(
            R.string.inputErrorIncomplete,
            listOf(InputValidation.INVITATION_CODE_LENGTH),
        ),
        viewModel.uiState.value.errorMessage,
    )
    coVerify(exactly = 0) { invitationRedemptionRepository.redeemInvitation(any()) }
  }

  @Test
  fun onInviteCodeChange_clearsRedemptionError() = runTest {
    coEvery { invitationRedemptionRepository.redeemInvitation(any()) } returns
        Result.failure(InvitationError.InvalidCode)
    viewModel.onInviteCodeChange("BADC2345")
    viewModel.onSubmit(onSuccess)
    advanceUntilIdle()
    assertEquals(
        UiText.Resource(InvitationError.InvalidCode.errorMessage()),
        viewModel.uiState.value.errorMessage,
    )

    viewModel.onInviteCodeChange("ABCD2345")

    assertNull(viewModel.uiState.value.errorMsgRes)
    assertNull(viewModel.uiState.value.errorMessage)
    assertEquals("ABCD2345", viewModel.uiState.value.inviteCode)
  }

  // ---------- Validation (no repository call) ----------

  @Test
  fun onSubmit_blankCode_setsDerivedErrorAndDoesNotCallRepository() = runTest {
    viewModel.onSubmit(onSuccess)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state.validationAttempted)
    assertEquals(UiText.Resource(R.string.invitationRedemptionCodeRequired), state.errorMessage)
    assertEquals("", state.inviteCode)
    assertFalse(state.isRedeeming)
    assertEquals(0, successCount)
    coVerify(exactly = 0) { invitationRedemptionRepository.redeemInvitation(any()) }
  }

  @Test
  fun onSubmit_whitespaceOnlyCode_treatedAsBlank() = runTest {
    viewModel.onInviteCodeChange("   ")

    viewModel.onSubmit(onSuccess)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(UiText.Resource(R.string.invitationRedemptionCodeRequired), state.errorMessage)
    assertEquals("", state.inviteCode)
    coVerify(exactly = 0) { invitationRedemptionRepository.redeemInvitation(any()) }
  }

  // ---------- Successful redeem (mocked repository) ----------

  @Test
  fun onSubmit_patientSuccess_invokesOnSuccessAndClearsSaving() = runTest {
    val result = InvitationRedemptionResult(careCircleId = "circle-1", role = Role.PATIENT)
    coEvery { invitationRedemptionRepository.redeemInvitation(any()) } returns
        Result.success(result)
    viewModel.onInviteCodeChange("  abcd2345  ")

    viewModel.onSubmit(onSuccess)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isRedeeming)
    assertNull(state.errorMessage)
    assertEquals(1, successCount)
    assertEquals(result, lastSuccessResult)
    assertEquals("ABCD2345", state.inviteCode)
    coVerify(exactly = 1) { invitationRedemptionRepository.redeemInvitation("ABCD2345") }
  }

  @Test
  fun onSubmit_caregiverSuccess_invokesOnSuccess() = runTest {
    val result = InvitationRedemptionResult(careCircleId = "circle-2", role = Role.CAREGIVER)
    coEvery { invitationRedemptionRepository.redeemInvitation(any()) } returns
        Result.success(result)
    viewModel.onInviteCodeChange("CARE2345")

    viewModel.onSubmit(onSuccess)
    advanceUntilIdle()

    assertEquals(1, successCount)
    assertEquals(result, lastSuccessResult)
    assertEquals(Role.CAREGIVER, lastSuccessResult?.role)
    coVerify(exactly = 1) { invitationRedemptionRepository.redeemInvitation("CARE2345") }
  }

  // ---------- Repository failures ----------

  @Test
  fun onSubmit_invalidCode_setsMappedError() = runTest {
    coEvery { invitationRedemptionRepository.redeemInvitation(any()) } returns
        Result.failure(InvitationError.InvalidCode)
    viewModel.onInviteCodeChange("BADC2345")

    viewModel.onSubmit(onSuccess)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isRedeeming)
    assertEquals(UiText.Resource(InvitationError.InvalidCode.errorMessage()), state.errorMessage)
    assertEquals(0, successCount)
  }

  @Test
  fun onSubmit_invitationNotFound_setsMappedError() = runTest {
    coEvery { invitationRedemptionRepository.redeemInvitation(any()) } returns
        Result.failure(InvitationError.InvitationNotFound)
    viewModel.onInviteCodeChange("MSNG2345")

    viewModel.onSubmit(onSuccess)
    advanceUntilIdle()

    assertEquals(
        UiText.Resource(InvitationError.InvitationNotFound.errorMessage()),
        viewModel.uiState.value.errorMessage,
    )
    assertEquals(0, successCount)
  }

  @Test
  fun onSubmit_networkError_setsMappedOfflineError() = runTest {
    coEvery { invitationRedemptionRepository.redeemInvitation(any()) } returns
        Result.failure(InvitationError.Network)
    viewModel.onInviteCodeChange("NETW2345")

    viewModel.onSubmit(onSuccess)
    advanceUntilIdle()

    assertEquals(
        UiText.Resource(InvitationError.Network.errorMessage()),
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
            .map { it.errorMessage() }

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
    viewModel.onInviteCodeChange("RTRY2345")

    viewModel.onSubmit(onSuccess)
    advanceUntilIdle()
    assertEquals(
        UiText.Resource(InvitationError.InvalidCode.errorMessage()),
        viewModel.uiState.value.errorMessage,
    )

    viewModel.onSubmit(onSuccess)
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
    viewModel.onInviteCodeChange("PEND2345")

    viewModel.onSubmit(onSuccess)
    runCurrent()
    assertTrue(viewModel.uiState.value.isRedeeming)

    viewModel.onSubmit(onSuccess)
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
  fun onSubmit_afterSuccessfulRedeem_reinvokesOnSuccessWithoutRepository() = runTest {
    val result = InvitationRedemptionResult(careCircleId = "circle-1", role = Role.PATIENT)
    coEvery { invitationRedemptionRepository.redeemInvitation(any()) } returns
        Result.success(result)
    viewModel.onInviteCodeChange("CDEF2345")

    viewModel.onSubmit(onSuccess)
    advanceUntilIdle()
    assertEquals(1, successCount)

    viewModel.onSubmit(onSuccess)
    advanceUntilIdle()

    coVerify(exactly = 1) { invitationRedemptionRepository.redeemInvitation(any()) }
    assertEquals(2, successCount)
    assertEquals(result, lastSuccessResult)
  }
}
