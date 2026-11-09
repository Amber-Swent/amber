// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.patient.seeMedias

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI state displayed by the patient See Medias screen.
 *
 * @property errorMsgRes the error to display, as a string resource, or null for none.
 */
data class SeeMediasUIState(
    @param:StringRes val errorMsgRes: Int? = null,
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
