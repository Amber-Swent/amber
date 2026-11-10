// Written by Alaabrine, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.patient.parameters

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * UI state displayed by the patient parameters screen.
 *
 * @property errorMsgRes the error to show in a toast, as a string resource, or null for none.
 */
data class PatientParameterUIState(
    @param:StringRes val errorMsgRes: Int? = null,
)

/** ViewModel for the patient parameters screen. */
class PatientParameterViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(PatientParameterUIState())
  val uiState: StateFlow<PatientParameterUIState> = _uiState.asStateFlow()

  /** Clears the current error in the UI state. */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMsgRes = null) }
  }

  /**
   * Sets an error message in the UI state.
   *
   * @param errorMsgRes string resource describing the error.
   */
  internal fun setErrorMsg(@StringRes errorMsgRes: Int) {
    _uiState.update { it.copy(errorMsgRes = errorMsgRes) }
  }
}
