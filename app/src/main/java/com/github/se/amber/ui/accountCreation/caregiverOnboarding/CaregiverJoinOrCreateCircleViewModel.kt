// this code was written by Alaabrine
// with the aid of an AI coding agent
package com.github.se.amber.ui.accountCreation.caregiverOnboarding

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI state for the caregiver join-or-create care circle step.
 *
 * @param displayName first name shown in the welcome header.
 */
data class CaregiverJoinOrCreateCircleUiState(
    val displayName: String = "",
)

/**
 * ViewModel for [CaregiverJoinOrCreateCircle]: exposes the caregiver's display name for the
 * welcome header. Create / join navigation is handled by the screen callbacks, not this ViewModel.
 */
class CaregiverJoinOrCreateCircleViewModel(
    displayName: String = "",
) : ViewModel() {

  private val _uiState =
      MutableStateFlow(CaregiverJoinOrCreateCircleUiState(displayName = displayName))
  val uiState: StateFlow<CaregiverJoinOrCreateCircleUiState> = _uiState.asStateFlow()
}
