// This file was written with the aid of AI
package com.github.se.amber.ui.authentication

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * State displayed by [CaregiverPasswordScreen].
 *
 * @param email Email address associated with the caregiver account.
 * @param password Password currently entered by the caregiver.
 */
data class CaregiverPasswordUiState(
    val email: String = "",
    val password: String = "",
)

/**
 * Owns the UI state of [CaregiverPasswordScreen].
 *
 * Password authentication is not performed here yet because the authentication repository does not
 * currently expose email/password authentication.
 */
class CaregiverPasswordViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(CaregiverPasswordUiState())

  /** Read-only state observed by the UI. */
  val uiState = _uiState.asStateFlow()

  /**
   * Updates the password entered by the caregiver.
   *
   * @param password New password value.
   */
  fun onPasswordChange(password: String) {
    _uiState.update { state -> state.copy(password = password) }
  }

  /**
   * Updates the email address displayed on the caregiver password screen.
   *
   * @param email Email address associated with the caregiver account.
   */
  fun setEmail(email: String) {
    _uiState.update { state -> state.copy(email = email) }
  }
}
