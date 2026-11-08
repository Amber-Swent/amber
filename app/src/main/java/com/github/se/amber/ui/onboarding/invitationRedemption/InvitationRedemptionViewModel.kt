// Written by Alaabrine with the aid of an AI coding agent
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.onboarding.invitationRedemption

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.data.invitation.InvitationError
import com.github.se.amber.data.invitation.InvitationRedemptionRepository
import com.github.se.amber.data.invitation.InvitationRedemptionRepositoryProvider
import com.github.se.amber.data.invitation.InvitationRedemptionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the redeem-invite step.
 *
 * Blank-code errors are derived once validation has been attempted. [redemptionErrorRes] carries
 * mapped [InvitationError] messages from the repository. Errors are string resources, resolved by
 * the screen in the app language.
 *
 * @param inviteCode invitation code / link entered by the user.
 * @param validationAttempted true after the user has pressed Submit at least once.
 * @param redemptionErrorRes actionable repository failure message, or null when there is none.
 * @param isRedeeming true while a redeem request is in flight.
 */
data class InvitationRedemptionUIState(
    val inviteCode: String = "",
    val validationAttempted: Boolean = false,
    @param:StringRes val redemptionErrorRes: Int? = null,
    val isRedeeming: Boolean = false,
) {
  /**
   * Message shown under the code field: blank-code validation when attempted, otherwise the latest
   * mapped redemption failure.
   */
  @get:StringRes
  val errorMessageRes: Int?
    get() =
        when {
          validationAttempted && inviteCode.isBlank() -> R.string.invitationRedemptionCodeRequired
          else -> redemptionErrorRes
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
    _uiState.update { it.copy(inviteCode = value, redemptionErrorRes = null) }
  }

  /**
   * Validates the invite code, then redeems it via [InvitationRedemptionRepository].
   *
   * [onSuccess] is invoked after a successful redeem (and again if the user returns and presses
   * Submit), so the navigation owner can route without observing UI state. Does nothing while a
   * redeem is already in progress. Trims only the value sent to the repository so the entered code
   * stays in the field after failure. Does not call the repository when the code is blank after
   * trim; the derived [InvitationRedemptionUIState.errorMessageRes] is shown instead.
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
          redemptionErrorRes = null,
      )
    }
    if (trimmedCode.isBlank()) return

    viewModelScope.launch {
      _uiState.update { it.copy(isRedeeming = true, redemptionErrorRes = null) }
      invitationRedemptionRepository
          .redeemInvitation(trimmedCode)
          .onSuccess { result ->
            redeemedResult = result
            _uiState.update { it.copy(isRedeeming = false, redemptionErrorRes = null) }
            onSuccess(result)
          }
          .onFailure { error ->
            _uiState.update {
              it.copy(
                  isRedeeming = false,
                  redemptionErrorRes = error.errorMessage(),
              )
            }
          }
    }
  }
}

/** Maps a repository failure to the string resource of the message shown to the user. */
@StringRes
internal fun Throwable.errorMessage(): Int =
    when (this) {
      is InvitationError.InvalidCode -> R.string.invitationRedemptionErrorInvalidCode
      is InvitationError.InvitationNotFound -> R.string.invitationRedemptionErrorNotFound
      is InvitationError.InvitationExpired -> R.string.invitationRedemptionErrorExpired
      is InvitationError.InvitationAlreadyUsed -> R.string.invitationRedemptionErrorAlreadyUsed
      is InvitationError.UserAlreadyBelongsToCircle ->
          R.string.invitationRedemptionErrorAlreadyInCircle
      is InvitationError.MissingOrInvalidUserProfile ->
          R.string.invitationRedemptionErrorProfileMissing
      is InvitationError.InvalidOrUnavailableCareCircle ->
          R.string.invitationRedemptionErrorCircleUnavailable
      is InvitationError.PatientSlotAlreadyOccupied ->
          R.string.invitationRedemptionErrorPatientSlotTaken
      is InvitationError.Network -> R.string.invitationRedemptionErrorOffline
      else -> R.string.invitationRedemptionErrorUnknown
    }
