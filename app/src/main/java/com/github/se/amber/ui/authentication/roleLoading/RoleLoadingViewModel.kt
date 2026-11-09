// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Alaabrine, with assistance from Cursor.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.authentication.roleLoading

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.data.account.AccountDeletionRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.github.se.amber.model.user.Role
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "RoleLoadingViewModel"

sealed interface RoleState {
  // waiting for finding the role
  data object Loading : RoleState

  // the role is loaded/ready
  data class Loaded(val role: Role) : RoleState

  // signed in, but there is no user document for this uid
  data object NoProfile : RoleState

  // profile exists, but the user has not created or joined a care circle yet
  data class NeedsCareCircle(val destination: CareCircleDestination) : RoleState

  // the user is not connected or if Firestore has failed
  data object Error : RoleState
}

/** Where to send a user who already has a profile but no care circle. */
enum class CareCircleDestination {
  /** Caregiver: choose between creating a circle and entering an invitation. */
  Choice,
  /** Patient: enter an invitation code. */
  Invitation,
}

data class RoleLoadingUIState(
    val roleState: RoleState = RoleState.Loading,
    val isSigningOut: Boolean = false,
    val signedOut: Boolean = false,
    @param:StringRes val errorMsgRes: Int? = null,
)

class RoleLoadingViewModel(
    private val profileRepository: ProfileRepository = ProfileRepositoryProvider.default.repository,
    private val authRepository: AuthRepository = AuthRepositoryProvider.repository,
    // A provider: its repository is only built for an account that is gone, as building it touches
    // Firebase
    private val deletionRepositoryProvider: AccountDeletionRepositoryProvider =
        AccountDeletionRepositoryProvider.default,
) : ViewModel() {
  private val _state = MutableStateFlow(RoleLoadingUIState())
  val state: StateFlow<RoleLoadingUIState> = _state

  private var loadJob: Job? = null

  /** Clears the error message in the UI state. */
  fun clearErrorMsg() {
    _state.update { it.copy(errorMsgRes = null) }
  }

  /** Sets an error message, as a string resource, in the UI state. */
  private fun setErrorMsg(@StringRes errorMsgRes: Int) {
    _state.update { it.copy(errorMsgRes = errorMsgRes) }
  }

  private fun setRoleState(roleState: RoleState) {
    _state.update { it.copy(roleState = roleState) }
  }

  fun loadRole() {
    // Ignore the call if a role loading operation is already in progress
    if (loadJob?.isActive == true) return
    loadJob = viewModelScope.launch {
      setRoleState(RoleState.Loading)
      val wasSignedIn = authRepository.currentUser != null
      val profile = async { profileRepository.getCurrentProfile() }
      if (authRepository.refreshCurrentUser() == null) {
        // The account is gone, for example deleted: there is no profile to load or create
        profile.cancel()
        if (wasSignedIn) clearGoneAccount()
        _state.update { it.copy(signedOut = true) }
        return@launch
      }
      profile
          .await()
          .fold(
              onSuccess = { profile ->
                val hasCircle = profile?.circleIds?.any { it.isNotBlank() } == true
                setRoleState(
                    when {
                      profile == null -> RoleState.NoProfile
                      !hasCircle -> RoleState.NeedsCareCircle(destinationFor(profile.role))
                      else -> RoleState.Loaded(profile.role)
                    }
                )
              },
              onFailure = { e ->
                Log.e(TAG, "Failed to load role", e)
                setRoleState(RoleState.Error)
              },
          )
    }
  }

  /**
   * Clears what an account the server no longer accepts left on this device: its cached documents,
   * pending uploads and temporary files. It may have been deleted from another phone, or by a
   * deletion that stopped before clearing this one. On success the app restarts on its first
   * screen; if clearing fails, the user is still signed out, and the next launch tries again.
   */
  private suspend fun clearGoneAccount() {
    deletionRepositoryProvider.repository.clearGoneAccount().onFailure { e ->
      Log.e(TAG, "Could not clear the data of an account that is gone", e)
    }
  }

  private fun destinationFor(role: Role): CareCircleDestination =
      when (role) {
        Role.CAREGIVER -> CareCircleDestination.Choice
        Role.PATIENT -> CareCircleDestination.Invitation
      }

  fun signOut() {
    if (_state.value.isSigningOut) return
    _state.update { it.copy(isSigningOut = true) }
    viewModelScope.launch {
      authRepository
          .signOut()
          .fold(
              onSuccess = { _state.update { it.copy(signedOut = true, isSigningOut = false) } },
              onFailure = { e ->
                Log.e(TAG, "Failed to sign out", e)
                if (authRepository.currentUser == null) {
                  // Firebase is already signed out: treat it as a successful sign-out
                  _state.update { it.copy(signedOut = true, isSigningOut = false) }
                } else {
                  _state.update { it.copy(isSigningOut = false) }
                  setErrorMsg(R.string.signOutError)
                }
              },
          )
    }
  }
}
