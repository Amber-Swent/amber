// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.patient.seeMedias.audios

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SeeAudiosUIState {
  data object Empty : SeeAudiosUIState
}

class SeeAudiosViewModel : ViewModel() {
  private val _uiState = MutableStateFlow<SeeAudiosUIState>(SeeAudiosUIState.Empty)
  val uiState: StateFlow<SeeAudiosUIState> = _uiState.asStateFlow()
}
