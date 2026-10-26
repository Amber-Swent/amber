// this code was written with the aid of AI

package com.github.se.amber.ui.patient

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.profile.FirebaseProfileRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PatientHomeUIState(
    val patientProfile: UserProfile =
        UserProfile(
            uid = "",
            role = Role.PATIENT,
            person = Person(),
            circleIds = emptyList(),
        ),
    val errorMsg: String? = null,
)

/**
 * ViewModel for the Patient Home screen.
 *
 * Responsible for managing the UI state
 *
 * @property profileRepository The repository used to fetch the Profile.
 */
class PatientHomeViewModel(
    private val profileRepository: ProfileRepository = FirebaseProfileRepository(),
) : ViewModel() {

  // Home UI state
  private val _uiState = MutableStateFlow(PatientHomeUIState())
  val uiState: StateFlow<PatientHomeUIState> = _uiState.asStateFlow()

  init {
    getProfile()
  }

  /** Clears the error message in the UI state. */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMsg = null) }
  }

  /** Sets an error message in the UI state. */
  private fun setErrorMsg(errorMsg: String) {
    _uiState.update { it.copy(errorMsg = errorMsg) }
  }

  /** Fetches the profile from the repository and updates the UI state. */
  private fun getProfile() {
    viewModelScope.launch {
      profileRepository
          .getCurrentProfile()
          .fold(
              onSuccess = { profile ->
                if (profile != null) {
                  _uiState.update { it.copy(patientProfile = profile) }
                } else {
                  setErrorMsg("No profile found.")
                }
              },
              onFailure = { e ->
                Log.e("PatientHomeViewModel", "Error fetching profile", e)
                setErrorMsg("Failed to load the profile: ${e.message}")
              },
          )
    }
  }
}
