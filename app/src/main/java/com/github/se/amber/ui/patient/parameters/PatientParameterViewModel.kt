// Written by Alaabrine, with assistance from Cursor.
package com.github.se.amber.ui.patient.parameters

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * UI state displayed by the patient parameters screen.
 *
 * @param errorMsg Error message to display, or null when there is no error.
 */
data class PatientParameterUIState(
    val errorMsg: String? = null,
)

/** ViewModel for the patient parameters screen. */
class PatientParameterViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(PatientParameterUIState())
  val uiState: StateFlow<PatientParameterUIState> = _uiState.asStateFlow()

  /** Clears the current error in the UI state. */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMsg = null) }
  }

  /**
   * Sets an error message in the UI state.
   *
   * @param errorMsg Message describing the error.
   */
  internal fun setErrorMsg(errorMsg: String) {
    _uiState.update { it.copy(errorMsg = errorMsg) }
  }
}
