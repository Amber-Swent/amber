// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.onboarding.changeCareCircle

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A care circle available to the caregiver. */
data class ChangeCareCircleChoice(val id: String, val name: String)

/** State displayed by [ChangeCareCircleScreen]. */
data class ChangeCareCircleUIState(
    val circles: List<ChangeCareCircleChoice> = emptyList(),
    val activeCircleId: String? = null,
)

/** Holds the future loading and selection actions for the care circle screen. */
class ChangeCareCircleViewModel(
    initialState: ChangeCareCircleUIState = ChangeCareCircleUIState(),
) : ViewModel() {
  private val _uiState = MutableStateFlow(initialState)
  val uiState: StateFlow<ChangeCareCircleUIState> = _uiState.asStateFlow()

  fun loadCareCircles() {
    // TODO: Load the caregiver's circles and active circle through repositories.
  }

  fun changeActiveCareCircle(circleId: String) {
    // TODO: Persist the selected active circle through the data layer and update UI state.
  }
}
