// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.patient.seeMedias.audios

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** States rendered by [SeeAudiosScreen]. Audio content states will be added later. */
sealed interface SeeAudiosUIState {
  data object Empty : SeeAudiosUIState
}

/** Holds the audio screen state, which currently remains [SeeAudiosUIState.Empty]. */
class SeeAudiosViewModel : ViewModel() {
  private val _uiState = MutableStateFlow<SeeAudiosUIState>(SeeAudiosUIState.Empty)

  val uiState: StateFlow<SeeAudiosUIState> = _uiState.asStateFlow()
}
