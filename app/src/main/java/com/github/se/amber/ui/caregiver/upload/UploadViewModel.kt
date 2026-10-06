/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents the current state of the upload screen.
 *
 * @param errorMsg error message to display, or null when there is no error.
 */
data class UploadUIState(
    val errorMsg: String? = null,
)

// TODO: implement repository
/** Stores and updates the state of the upload screen. */
class UploadViewModel(
    // private val repository: MediaRepository = MediaRepositoryProvider.repository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(UploadUIState())
  val uiState: StateFlow<UploadUIState> = _uiState.asStateFlow()

  /** Clears the error message in the UI state. */
  fun clearErrorMsg() {
    _uiState.value = _uiState.value.copy(errorMsg = null)
  }

  /**
   * Sets an error message in the UI state.
   *
   * Note: internal instead of private;
   * It stays hidden from other modules but is reachable from tests in the same module.
   *
   * @param errorMsg message describing the error.
   */
  internal fun setErrorMsg(errorMsg: String) {
    _uiState.value = _uiState.value.copy(errorMsg = errorMsg)
  }
}
