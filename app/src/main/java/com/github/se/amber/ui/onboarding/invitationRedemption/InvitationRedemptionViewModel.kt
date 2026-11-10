// Written by Alaabrine with the aid of an AI coding agent
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.onboarding.invitationRedemption

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.data.invitation.InvitationError
import com.github.se.amber.data.invitation.InvitationRedemptionRepository
import com.github.se.amber.data.invitation.InvitationRedemptionRepositoryProvider
import com.github.se.amber.data.invitation.InvitationRedemptionResult
import com.github.se.amber.model.validation.InputValidation
import com.github.se.amber.ui.utils.UiText
import com.github.se.amber.ui.utils.errorText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the redeem-invite step.
 *
 * A code with characters codes never use shows its error at once; a blank or incomplete one only
 * once validation has been attempted. [errorMsgRes] carries mapped [InvitationError] messages from
 * the repository.
 *
 * @param inviteCode invitation code / link entered by the user.
 * @param validationAttempted true after the user has pressed Submit at least once.
 * @param errorMsgRes actionable repository failure message, or null when there is none.
 * @param isRedeeming true while a redeem request is in flight.
 */
data class InvitationRedemptionUIState(
    val inviteCode: String = "",
    val validationAttempted: Boolean = false,
    @param:StringRes val errorMsgRes: Int? = null,
    val isRedeeming: Boolean = false,
) {
  /**
   * Message shown under the code field: [validationError] if there is one, otherwise the latest
   * mapped redemption failure.
   */
  val errorMessage: UiText?
    get() = validationError ?: errorMsgRes?.let { UiText.Resource(it) }

  /**
   * Why the code can't be sent: a character codes never use, a blank or incomplete code once
   * validation was attempted; null otherwise.
   */
  val validationError: UiText?
    get() =
        InputValidation.INVITATION_CODE.errorText(
            inviteCode,
            submitted = validationAttempted,
            blankRes = R.string.invitationRedemptionCodeRequired,
            formatRes = R.string.inputErrorInvitationCodeFormat,
        )

  /**
   * Whether Submit can be pressed: no redeem in progress and a code that can be sent. A failed
   * redeem doesn't disable it, so that the user can retry, e.g. once back online.
   */
  val canSubmit: Boolean
    get() = !isRedeeming && validationError == null
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
   * user can retry with a corrected code. The code is put in capitals, without spaces, and cut at
   * [InputValidation.INVITATION_CODE_LENGTH], the way codes are generated.
   */
  fun onInviteCodeChange(value: String) {
    _uiState.update {
      it.copy(inviteCode = InputValidation.INVITATION_CODE.clean(value), errorMsgRes = null)
    }
  }

  /**
   * Validates the invite code, then redeems it via [InvitationRedemptionRepository].
   *
   * [onSuccess] is invoked after a successful redeem (and again if the user returns and presses
   * Submit), so the navigation owner can route without observing UI state. Does nothing while a
   * redeem is already in progress. Does not call the repository when the code is blank or isn't in
   * the format codes are generated in; the derived [InvitationRedemptionUIState.errorMessage] is
   * shown instead.
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
          errorMsgRes = null,
      )
    }
    if (!InputValidation.INVITATION_CODE.isValid(trimmedCode)) return

    viewModelScope.launch {
      _uiState.update { it.copy(isRedeeming = true, errorMsgRes = null) }
      invitationRedemptionRepository
          .redeemInvitation(trimmedCode)
          .onSuccess { result ->
            redeemedResult = result
            _uiState.update { it.copy(isRedeeming = false, errorMsgRes = null) }
            onSuccess(result)
          }
          .onFailure { error ->
            _uiState.update {
              it.copy(
                  isRedeeming = false,
                  errorMsgRes = error.errorMessage(),
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
