/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 * Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents the current state of the upload screen.
 *
 * @param errorMsgRes error to display, as a string resource, or null when there is no error.
 */
data class UploadUIState(
    @param:StringRes val errorMsgRes: Int? = null,
)

/** Stores and updates the state of the upload screen. */
class UploadViewModel(
) : ViewModel() {
  private val _uiState = MutableStateFlow(UploadUIState())
  val uiState: StateFlow<UploadUIState> = _uiState.asStateFlow()

  /** Clears the error message in the UI state. */
  fun clearErrorMsg() {
    _uiState.value = _uiState.value.copy(errorMsgRes = null)
  }

  /**
   * Sets an error message in the UI state.
   *
   * Note: internal instead of private; It stays hidden from other modules but is reachable from
   * tests in the same module.
   *
   * @param errorMsgRes string resource describing the error.
   */
  internal fun setErrorMsg(@StringRes errorMsgRes: Int) {
    _uiState.value = _uiState.value.copy(errorMsgRes = errorMsgRes)
  }
}
