/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state displayed by the caregiver home screen.
 *
 * @param patient Patient whose information is displayed on the screen.
 * @param errorMsg Error message to display, or null when there is no error.
 * @param signedOut Whether the caregiver has successfully signed out.
 */
data class CaregiverHomeUIState(
    val patient: UserProfile =
        UserProfile(
            uid = "",
            role = Role.PATIENT,
            person = Person(),
            circleIds = emptyList(),
        ),
    val errorMsg: String? = null,
    val signedOut: Boolean = false,
)

/**
 * ViewModel responsible for providing data to the caregiver home screen.
 *
 * @param authRepository Repository used to sign the caregiver out.
 */
class CaregiverHomeViewModel(
    private val authRepository: AuthRepository = AuthRepositoryProvider.repository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(CaregiverHomeUIState())
  val uiState: StateFlow<CaregiverHomeUIState> = _uiState.asStateFlow()

  /** Clears the error message from the UI state. */
  fun clearErrorMsg() {
    _uiState.value = _uiState.value.copy(errorMsg = null)
  }

  /**
   * Sets an error message in the UI state.
   *
   * @param errorMsg Error message to display in the UI.
   */
  internal fun setErrorMsg(errorMsg: String) {
    _uiState.value = _uiState.value.copy(errorMsg = errorMsg)
  }

  /**
   * Sets the signedOut parameter in the UI state.
   *
   * @param signedOut New value of signedOut
   */
  internal fun setSignedOut(signedOut: Boolean) {
    _uiState.value = _uiState.value.copy(signedOut = signedOut)
  }

  /** Initiates sign-out and updates the UI state on success or failure. */
  fun signOut() {
    viewModelScope.launch {
      authRepository
          .signOut()
          .fold(
              onSuccess = { setSignedOut(true) },
              onFailure = { throwable ->
                setErrorMsg(throwable.localizedMessage ?: "Sign out failed")
              },
          )
    }
  }
}
