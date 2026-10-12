package com.github.se.amber.ui.caregiver

import androidx.lifecycle.ViewModel
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CaregiverHomeUIState(
    val patient: UserProfile =
        UserProfile(
            uid = "",
            role = Role.PATIENT,
            person = Person(),
            circleIds = emptyList(),
        ),
)

// TODO: unExtract repository once it's implement
/** ViewModel responsible for providing data to the caregiver home screen. */
class CaregiverHomeViewModel(
    // private val repository: MediaRepository = MediaRepositoryProvider.repository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(CaregiverHomeUIState())
  val uiState: StateFlow<CaregiverHomeUIState> = _uiState.asStateFlow()
}
