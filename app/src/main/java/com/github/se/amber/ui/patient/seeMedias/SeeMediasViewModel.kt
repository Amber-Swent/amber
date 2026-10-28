// this code was written with the aid of AI

package com.github.se.amber.ui.patient.seeMedias

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI state displayed by the patient See Medias screen.
 *
 * @param errorMsg Error message to display, or null when there is no error.
 */
data class SeeMediasUIState(
    val errorMsg: String? = null,
)

/**
 * ViewModel for the Patient See Medias screen.
 *
 * Responsible for managing the UI state
 */
class SeeMediasViewModel() : ViewModel() {

  private val _uiState = MutableStateFlow(SeeMediasUIState())
  val uiState: StateFlow<SeeMediasUIState> = _uiState.asStateFlow()

  /** Clears the error message in the UI state. */
  fun clearErrorMsg() {
    _uiState.value = _uiState.value.copy(errorMsg = null)
  }

  /**
   * Sets an error message in the UI state.
   *
   * Note: internal instead of private; It stays hidden from other modules but is reachable from
   * tests in the same module.
   *
   * @param errorMsg message describing the error.
   */
  internal fun setErrorMsg(errorMsg: String) {
    _uiState.value = _uiState.value.copy(errorMsg = errorMsg)
  }
}
