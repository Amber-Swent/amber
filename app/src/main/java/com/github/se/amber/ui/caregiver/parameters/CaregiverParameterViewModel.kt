// Written by Yuhan YANG, with assistance from Cursor.
// Modify by Lou-Anne Maier, with assistance from Claude Code
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.parameters

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * UI state of the caregiver parameters screen.
 *
 * @property errorMsgRes the error to show in a toast, as a string resource, or null for none.
 */
data class CaregiverParameterUIState(
    @param:StringRes val errorMsgRes: Int? = null,
)

/** ViewModel for the caregiver parameters screen */
class CaregiverParameterViewModel() : ViewModel() {
  private val _uiState = MutableStateFlow(CaregiverParameterUIState())
  val uiState: StateFlow<CaregiverParameterUIState> = _uiState.asStateFlow()

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
    _uiState.value = _uiState.value.copy(errorMsgRes = errorMsgRes)
  }
}
