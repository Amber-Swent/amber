// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.onboarding.changeCareCircle

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A care circle available to the caregiver.
 *
 * @param id Stable ID used to identify and select the circle.
 * @param name Name displayed on the chip and as the current circle.
 */
data class ChangeCareCircleChoice(val id: String, val name: String)

/**
 * Determines which content [ChangeCareCircleScreen] renders.
 *
 * [LOADING] shows progress, [EMPTY] shows the no-circles message, and [READY] shows the current
 * circle and available chips. The status determines the visible content.
 */
enum class ChangeCareCircleStatus {
  LOADING,
  EMPTY,
  READY,
}

/**
 * State displayed by [ChangeCareCircleScreen].
 *
 * The screen uses [status] to choose the visible section. In [ChangeCareCircleStatus.READY], it
 * looks up [activeCircleId] in [circles] for the current-circle name and selected chip. A null or
 * unmatched ID displays the localized fallback and leaves every chip unselected.
 *
 * @param status Determines whether loading, empty, or ready content is shown.
 * @param circles Care circles displayed as chips in the ready state.
 * @param activeCircleId ID of the selected circle, or null if none is active.
 */
data class ChangeCareCircleUIState(
    val status: ChangeCareCircleStatus = ChangeCareCircleStatus.EMPTY,
    val circles: List<ChangeCareCircleChoice> = emptyList(),
    val activeCircleId: String? = null,
)

/**
 * Owns the state and selection actions for the change care circle screen.
 *
 * The default state is [ChangeCareCircleStatus.EMPTY]. [initialState] also lets previews and tests
 * show other states without a repository.
 *
 * @param initialState Initial screen state; defaults to the empty state.
 */
class ChangeCareCircleViewModel(
    initialState: ChangeCareCircleUIState = ChangeCareCircleUIState(),
) : ViewModel() {
  private val _uiState = MutableStateFlow(initialState)
  val uiState: StateFlow<ChangeCareCircleUIState> = _uiState.asStateFlow()

  /**
   * Load the caregiver's circles and active circle into [uiState].
   *
   * This is currently a no-op. Repository access and loading, empty, and ready transitions are
   * reserved for the follow-up implementation.
   */
  fun loadCareCircles() {
    // TODO: Load the caregiver's circles and active circle through repositories.
  }

  /**
   * Will persist a selected circle and update [uiState] after success.
   *
   * Chip clicks call this method, but it is currently a no-op; the selection does not change yet.
   *
   * @param circleId ID of the circle selected by the caregiver.
   */
  fun changeActiveCareCircle(circleId: String) {
    // TODO: Persist the selected active circle through the data layer and update UI state.
  }
}
