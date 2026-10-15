/* Written by Yuhan YANG, with assistance from
 * Cursor.
 */
package com.github.se.amber.ui.caregiver.parameters

import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CaregiverParameterUIState(
    val errorMsg: String? = null,
    val signedOut: Boolean = false,
)

/** ViewModel for the caregiver parameters screen. Sign-out is handled here. */
class CaregiverParameterViewModel(
    private val authRepository: AuthRepository = AuthRepositoryProvider.repository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(CaregiverParameterUIState())
  val uiState: StateFlow<CaregiverParameterUIState> = _uiState.asStateFlow()

  /** Clears the current error so it is not shown again. */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMsg = null) }
  }

  /** Ends the session and records success or the failure message. */
  fun signOut(credentialManager: CredentialManager) {
    viewModelScope.launch {
      authRepository
          .signOut()
          .fold(
              onSuccess = {
                try {
                  credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: CancellationException) {
                  throw e
                } catch (_: Exception) {
                  // AuthRepository already cleared provider state. A second clear must not
                  // block leaving the screen.
                }
                _uiState.update { it.copy(signedOut = true) }
              },
              onFailure = { throwable ->
                _uiState.update { it.copy(errorMsg = throwable.localizedMessage) }
              },
          )
    }
  }
}
