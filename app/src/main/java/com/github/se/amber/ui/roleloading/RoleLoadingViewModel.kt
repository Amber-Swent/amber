// this code was written with the aid of AI
package com.github.se.amber.ui.roleloading

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.profile.FirebaseProfileRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.github.se.amber.model.user.Role
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RoleState {
  // waiting for finding the role
  data object Loading : RoleState

  // the role is loaded/ready
  data class Loaded(val role: Role) : RoleState

  // the user is connected but there are no user document with this uid
  data object NoProfile : RoleState

  // the user is not connected or if Firestore has failed
  data object Error : RoleState
}

data class RoleLoadingUIState(
    val roleState: RoleState = RoleState.Loading,
    val signedOut: Boolean = false,
    val errorMsg: String? = null,
)

class RoleLoadingViewModel(
    private val profileRepository: ProfileRepository = FirebaseProfileRepository(),
    private val authRepository: AuthRepository = AuthRepositoryProvider.repository,
) : ViewModel() {
  private val _state = MutableStateFlow(RoleLoadingUIState())
  val state: StateFlow<RoleLoadingUIState> = _state

  private var loadJob: Job? = null

  /** Clears the error message in the UI state. */
  fun clearErrorMsg() {
    _state.update { it.copy(errorMsg = null) }
  }

  /** Sets an error message in the UI state. */
  private fun setErrorMsg(errorMsg: String) {
    _state.update { it.copy(errorMsg = errorMsg) }
  }

  private fun setRoleState(roleState: RoleState) {
    _state.update { it.copy(roleState = roleState) }
  }

  fun loadRole() {
    // Ignore the call if a role loading operation is already in progress
    if (loadJob?.isActive == true) return
    loadJob = viewModelScope.launch {
      setRoleState(RoleState.Loading)
      val uid = authRepository.currentUser?.uid
      // case where no one is connected
      if (uid == null) {
        setRoleState(RoleState.Error)
        return@launch
      }
      profileRepository
          .getCurrentProfile()
          .fold(
              onSuccess = { profile ->
                setRoleState(
                    if (profile == null) RoleState.NoProfile else RoleState.Loaded(profile.role)
                )
              },
              onFailure = { e ->
                if (e is CancellationException) throw e
                Log.e("RoleLoadingViewModel", "Failed to load role", e)
                setRoleState(RoleState.Error)
              },
          )
    }
  }

  fun signOut() {
    viewModelScope.launch {
      authRepository
          .signOut()
          .fold(
              onSuccess = { _state.update { it.copy(signedOut = true) } },
              onFailure = { e -> setErrorMsg(e.localizedMessage ?: "Sign out failed") },
          )
    }
  }
}
