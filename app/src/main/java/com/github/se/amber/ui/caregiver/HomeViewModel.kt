package com.github.se.amber.ui.caregiver

import androidx.lifecycle.ViewModel
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HomeUIState(
    val patient: UserProfile =
        UserProfile(
            uid = "",
            role = Role.PATIENT,
            person = Person(),
            circleIds = emptyList(),
        ),
)

// TODO: implement repository
/** ViewModel responsible for providing data to the caregiver home screen. */
class HomeViewModel(
    // private val repository: MediaRepository = MediaRepositoryProvider.repository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(HomeUIState())
  val uiState: StateFlow<HomeUIState> = _uiState.asStateFlow()

  /** Returns the name of the patient associated with the caregiver home screen. */
  fun getPatientName(): String {
    // TODO : to be implement
    return ""
  }
}
