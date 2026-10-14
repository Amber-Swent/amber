// this code was written by Alaabrine
// with the aid of an AI coding agent
package com.github.se.amber.ui.accountCreation.caregiverOnboarding

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Choice a caregiver makes on [CaregiverJoinOrCreateCircle] (the "Choice between" onboarding step).
 *
 * A navigation owner routes [Create] to
 * [com.github.se.amber.ui.navigation.OnboardingScreen.CreateCareCircle] and [Join] to
 * [com.github.se.amber.ui.navigation.OnboardingScreen.EnterInvitation]. [None] until a button is
 * pressed or after [onChoiceHandled].
 */
enum class CareCircleEntryChoice {
  None,
  Create,
  Join,
}

/**
 * UI state for the caregiver join-or-create care circle step.
 *
 * @param displayName first name shown in the welcome header.
 * @param choice selected entry path, or [CareCircleEntryChoice.None] until the user chooses.
 */
data class CaregiverJoinOrCreateCircleUiState(
    val displayName: String = "",
    val choice: CareCircleEntryChoice = CareCircleEntryChoice.None,
)

/**
 * ViewModel for [CaregiverJoinOrCreateCircle]: exposes the caregiver's display name and records
 * whether they chose to create a care circle or join one via invitation.
 *
 * Enter-invitation and create-circle side effects are not handled here; the UI (or navigation
 * owner) reacts to [CaregiverJoinOrCreateCircleUiState.choice], then calls [onChoiceHandled].
 */
class CaregiverJoinOrCreateCircleViewModel(
    displayName: String = "",
) : ViewModel() {

  private val _uiState =
      MutableStateFlow(CaregiverJoinOrCreateCircleUiState(displayName = displayName))
  val uiState: StateFlow<CaregiverJoinOrCreateCircleUiState> = _uiState.asStateFlow()

  /** Records that the caregiver wants to create a new care circle. */
  fun onCreateCircleClick() {
    _uiState.value = _uiState.value.copy(choice = CareCircleEntryChoice.Create)
  }

  /** Records that the caregiver wants to join an existing care circle via invitation. */
  fun onJoinCircleClick() {
    _uiState.value = _uiState.value.copy(choice = CareCircleEntryChoice.Join)
  }

  /** Clears [CaregiverJoinOrCreateCircleUiState.choice] after navigation has consumed it. */
  fun onChoiceHandled() {
    if (_uiState.value.choice == CareCircleEntryChoice.None) return
    _uiState.value = _uiState.value.copy(choice = CareCircleEntryChoice.None)
  }
}
