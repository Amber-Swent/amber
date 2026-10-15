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
 */
data class RedeemInviteUiState(
    val inviteCode: String = "",
    val showError: Boolean = false,
    val isRedeeming: Boolean = false,
)

/**
 * ViewModel for [RedeemInvite]: validates the invite code and redeems it via
 * [InvitationRedemptionRepository].
 *
 * @param onSuccess invoked with the redemption result after a successful redeem, so the navigation
 *   owner can route to the patient or caregiver home graph without observing UI state.
 */
class RedeemInviteViewModel(
    private val invitationRedemptionRepository: InvitationRedemptionRepository =
        FirebaseInvitationRedemptionRepository(),
    private val onSuccess: (InvitationRedemptionResult) -> Unit = {},
) : ViewModel() {

  private val _uiState = MutableStateFlow(RedeemInviteUiState())
  val uiState: StateFlow<RedeemInviteUiState> = _uiState.asStateFlow()

  private var hasRedeemed = false

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
   * Does nothing while a redeem is already in progress or after a successful redeem. Does not call
   * the repository when the code is blank after trim; sets [RedeemInviteUiState.showError] instead.
   */
  fun onSubmit() {
    if (_uiState.value.isRedeeming || hasRedeemed) return

    val trimmedCode = _uiState.value.inviteCode.trim()
    if (trimmedCode.isEmpty()) {
      _uiState.value =
          _uiState.value.copy(
              inviteCode = trimmedCode,
              showError = true,
          )
      return
    }

    viewModelScope.launch {
      _uiState.value =
          _uiState.value.copy(
              inviteCode = trimmedCode,
              isRedeeming = true,
              showError = false,
          )
      try {
        invitationRedemptionRepository
            .redeemInvitation(trimmedCode)
            .onSuccess { result ->
              hasRedeemed = true
              _uiState.value =
                  _uiState.value.copy(
                      isRedeeming = false,
                      showError = false,
                  )
              onSuccess(result)
            }
            .onFailure {
              _uiState.value =
                  _uiState.value.copy(
                      isRedeeming = false,
                      showError = true,
                  )
            }
      } catch (exception: CancellationException) {
        throw exception
      }
    }
  }
}
