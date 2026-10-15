// this code was written by Alaabrine
// with the aid of an AI coding agent
package com.github.se.amber.ui.accountCreation.caregiverOnboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the caregiver join-or-create care circle step.
 *
 * @param displayName first name shown in the welcome header.
 */
data class CaregiverJoinOrCreateCircleUiState(
    val displayName: String = "",
)

/**
 * ViewModel for [CaregiverJoinOrCreateCircle]: loads the caregiver's first name from
 * [ProfileRepository] for the welcome header. Create / join navigation is handled by the screen
 * callbacks, not this ViewModel.
 */
class CaregiverJoinOrCreateCircleViewModel(
    private val profileRepository: ProfileRepository = ProfileRepositoryProvider.default.repository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(CaregiverJoinOrCreateCircleUiState())
  val uiState: StateFlow<CaregiverJoinOrCreateCircleUiState> = _uiState.asStateFlow()

  init {
    loadDisplayName()
  }

  private fun loadDisplayName() {
    viewModelScope.launch {
      profileRepository.getCurrentProfile().onSuccess { profile ->
        val firstName = profile?.person?.firstName.orEmpty()
        _uiState.update { it.copy(displayName = firstName) }
      }
    }
  }
}
