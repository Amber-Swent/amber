/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 * Modified by Alaabrine, with assistance from Cursor.
 */
package com.github.se.amber.ui.caregiver

import androidx.lifecycle.ViewModel
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI state displayed by the caregiver home screen.
 *
 * @param patient Patient whose information is displayed on the screen.
 * @param errorMsg Error message to display, or null when there is no error.
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
)

/**
 * ViewModel responsible for providing data to the caregiver home screen.
 *
 * Sign-out lives on the shared [com.github.se.amber.ui.authentication.signOut.SignOutViewModel].
 */
class CaregiverHomeViewModel : ViewModel() {
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
}
