// This file was written with the aid of AI
package com.github.se.amber.ui.authentication

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * State displayed by [PatientPasswordScreen].
 *
 * @param email Email currently entered by the patient.
 * @param password Password currently entered by the patient.
 */
data class PatientPasswordUiState(
    val email: String = "",
    val password: String = "",
)

/**
 * Owns the UI state of [PatientPasswordScreen].
 *
 * Password authentication is not performed here yet because the authentication repository does not
 * currently expose email/password authentication.
 */
class PatientPasswordViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(PatientPasswordUiState())

  val uiState = _uiState.asStateFlow() // Read-only

  /**
   * Updates the password entered by the patient.
   *
   * @param password New password value.
   */
  fun onPasswordChange(password: String) {
    _uiState.update { state -> state.copy(password = password) }
  }

  /**
   * Updates the email address displayed on the patient password screen.
   *
   * @param email Email address associated with the patient account.
   */
  fun setEmail(email: String) {
    _uiState.update { state -> state.copy(email = email) }
  }
}
