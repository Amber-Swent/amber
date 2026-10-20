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
    // TODO check if we really need an errorMsg
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
}
