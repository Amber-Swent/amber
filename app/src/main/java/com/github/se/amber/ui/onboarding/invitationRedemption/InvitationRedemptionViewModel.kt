// this code was written by Alaabrine
// with the aid of an AI coding agent
// Modified by Yuhan YANG, with assistance from Cursor.
package com.github.se.amber.ui.onboarding.invitationRedemption

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.invitation.InvitationError
import com.github.se.amber.data.invitation.InvitationRedemptionRepository
import com.github.se.amber.data.invitation.InvitationRedemptionRepositoryProvider
import com.github.se.amber.data.invitation.InvitationRedemptionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val CODE_REQUIRED_MESSAGE = "Enter an invitation code and try again."

/**
 * UI state for the redeem-invite step.
 *
 * Blank-code errors are derived once validation has been attempted. [redemptionError] carries
 * mapped [InvitationError] messages from the repository.
 *
 * @param inviteCode invitation code / link entered by the user.
 * @param validationAttempted true after the user has pressed Submit at least once.
 * @param redemptionError actionable repository failure message, or null when there is none.
 * @param isRedeeming true while a redeem request is in flight.
 */
data class InvitationRedemptionUIState(
    val inviteCode: String = "",
    val validationAttempted: Boolean = false,
    val redemptionError: String? = null,
    val isRedeeming: Boolean = false,
) {
  /**
   * Message shown under the code field: blank-code validation when attempted, otherwise the latest
   * mapped redemption failure.
   */
  val errorMessage: String?
    get() =
        when {
          validationAttempted && inviteCode.isBlank() -> CODE_REQUIRED_MESSAGE
          else -> redemptionError
        }
}

/**
 * ViewModel for [InvitationRedemptionScreen]: validates the invite code and redeems it via
 * [InvitationRedemptionRepository].
 *
 * @param invitationRedemptionRepository source of invitation redeem operations.
 */
class InvitationRedemptionViewModel(
    private val invitationRedemptionRepository: InvitationRedemptionRepository =
        InvitationRedemptionRepositoryProvider.default.repository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(InvitationRedemptionUIState())
  val uiState: StateFlow<InvitationRedemptionUIState> = _uiState.asStateFlow()

  private var redeemedResult: InvitationRedemptionResult? = null

  /**
   * Updates [InvitationRedemptionUIState.inviteCode] and clears a previous redemption error so the
   * user can retry with a corrected code.
   */
  fun onInviteCodeChange(value: String) {
    _uiState.update { it.copy(inviteCode = value, redemptionError = null) }
  }

  /**
   * Validates the invite code, then redeems it via [InvitationRedemptionRepository].
   *
   * [onSuccess] is invoked after a successful redeem (and again if the user returns and presses
   * Submit), so the navigation owner can route without observing UI state. Does nothing while a
   * redeem is already in progress. Trims only the value sent to the repository so the entered code
   * stays in the field after failure. Does not call the repository when the code is blank after
   * trim; the derived [InvitationRedemptionUIState.errorMessage] is shown instead.
   */
  fun onSubmit(onSuccess: (InvitationRedemptionResult) -> Unit = {}) {
    redeemedResult?.let {
      onSuccess(it)
      return
    }
    if (_uiState.value.isRedeeming) return

    val trimmedCode = _uiState.value.inviteCode.trim()
    _uiState.update {
      it.copy(
          validationAttempted = true,
          redemptionError = null,
      )
    }
    if (trimmedCode.isBlank()) return

    viewModelScope.launch {
      _uiState.update { it.copy(isRedeeming = true, redemptionError = null) }
      invitationRedemptionRepository
          .redeemInvitation(trimmedCode)
          .onSuccess { result ->
            redeemedResult = result
            _uiState.update { it.copy(isRedeeming = false, redemptionError = null) }
            onSuccess(result)
          }
          .onFailure { error ->
            _uiState.update {
              it.copy(
                  isRedeeming = false,
                  redemptionError = error.toActionableMessage(),
              )
            }
          }
    }
  }
}

internal fun Throwable.toActionableMessage(): String =
    when (this) {
      is InvitationError.InvalidCode -> "That invitation code isn't valid. Check it and try again."
      is InvitationError.InvitationNotFound ->
          "No invitation matches this code. Check it and try again."
      is InvitationError.InvitationExpired -> "This invitation has expired. Ask for a new one."
      is InvitationError.InvitationAlreadyUsed ->
          "This invitation was already used. Ask for a new one."
      is InvitationError.UserAlreadyBelongsToCircle -> "You're already in a care circle."
      is InvitationError.MissingOrInvalidUserProfile -> "Complete your profile, then try again."
      is InvitationError.InvalidOrUnavailableCareCircle ->
          "This care circle is unavailable. Ask for a new invitation."
      is InvitationError.PatientSlotAlreadyOccupied ->
          "This circle already has a patient. Ask the caregiver for help."
      is InvitationError.Network -> "You're offline. Check your connection and try again."
      is InvitationError.Unknown -> "Couldn't redeem this invitation. Please try again."
      else -> "Couldn't redeem this invitation. Please try again."
    }
