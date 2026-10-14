// this code was written by Alaabrine
// with the aid of an AI coding agent
package com.github.se.amber.ui.invitationRedemption

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.invitation.FirebaseInvitationRedemptionRepository
import com.github.se.amber.data.invitation.InvitationRedemptionRepository
import com.github.se.amber.data.invitation.InvitationRedemptionResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the redeem-invite step.
 *
 * @param inviteCode invitation code / link entered by the user.
 * @param showError whether the code field should show the invalid-link error.
 * @param isRedeeming true while a redeem request is in flight.
 * @param redemptionResult result written on success for navigation; null until then / after
 *   handled.
 */
data class RedeemInviteUiState(
    val inviteCode: String = "",
    val showError: Boolean = false,
    val isRedeeming: Boolean = false,
    val redemptionResult: InvitationRedemptionResult? = null,
)

/**
 * ViewModel for [RedeemInvite]: validates the invite code and redeems it via
 * [InvitationRedemptionRepository].
 *
 * On success, [RedeemInviteUiState.redemptionResult] exposes the care circle and role so a
 * navigation owner can route to the patient or caregiver home graph. On failure, [showError] is set
 * so the UI can prompt a retry. Call [onRedemptionResultHandled] after navigation so the event is
 * not re-delivered.
 */
class RedeemInviteViewModel(
    private val invitationRedemptionRepository: InvitationRedemptionRepository =
        FirebaseInvitationRedemptionRepository(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(RedeemInviteUiState())
  val uiState: StateFlow<RedeemInviteUiState> = _uiState.asStateFlow()

  /**
   * Updates [RedeemInviteUiState.inviteCode] and clears [RedeemInviteUiState.showError] when the
   * value changes.
   */
  fun onInviteCodeChange(value: String) {
    _uiState.value =
        _uiState.value.copy(
            inviteCode = value,
            showError = false,
        )
  }

  /**
   * Validates the invite code, then redeems it via [InvitationRedemptionRepository].
   *
   * Does nothing while a redeem is already in progress. Does not call the repository when the code
   * is blank after trim; sets [RedeemInviteUiState.showError] instead.
   */
  fun onSubmit() {
    if (_uiState.value.isRedeeming) return

    val trimmedCode = _uiState.value.inviteCode.trim()
    if (trimmedCode.isEmpty()) {
      _uiState.value =
          _uiState.value.copy(
              inviteCode = trimmedCode,
              showError = true,
              redemptionResult = null,
          )
      return
    }

    viewModelScope.launch {
      _uiState.value =
          _uiState.value.copy(
              inviteCode = trimmedCode,
              isRedeeming = true,
              showError = false,
              redemptionResult = null,
          )
      try {
        invitationRedemptionRepository
            .redeemInvitation(trimmedCode)
            .onSuccess { result ->
              _uiState.value =
                  _uiState.value.copy(
                      isRedeeming = false,
                      showError = false,
                      redemptionResult = result,
                  )
            }
            .onFailure {
              _uiState.value =
                  _uiState.value.copy(
                      isRedeeming = false,
                      showError = true,
                      redemptionResult = null,
                  )
            }
      } catch (exception: CancellationException) {
        throw exception
      }
    }
  }

  /** Clears [RedeemInviteUiState.redemptionResult] after the navigation owner has consumed it. */
  fun onRedemptionResultHandled() {
    if (_uiState.value.redemptionResult == null) return
    _uiState.value = _uiState.value.copy(redemptionResult = null)
  }
}
