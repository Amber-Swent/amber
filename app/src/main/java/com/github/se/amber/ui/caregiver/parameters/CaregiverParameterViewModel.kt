/* Written by Yuhan YANG, with assistance from
 * Cursor.
 * Modify by Lou-Anne Maier, with assistance from
 * Claude Code
 */
package com.github.se.amber.ui.caregiver.parameters

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CaregiverParameterUIState(
    val errorMsg: String? = null,
)

/** ViewModel for the caregiver parameters screen */
class CaregiverParameterViewModel() : ViewModel() {
  private val _uiState = MutableStateFlow(CaregiverParameterUIState())
  val uiState: StateFlow<CaregiverParameterUIState> = _uiState.asStateFlow()

  /** Clears the current error in the UI state. */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMsg = null) }
  }

  /**
   * Sets an error message in the UI state.
   *
   * @param errorMsg message describing the error.
   */
  internal fun setErrorMsg(errorMsg: String) {
    _uiState.value = _uiState.value.copy(errorMsg = errorMsg)
  }
}
