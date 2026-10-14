// this code was written with the aid of AI

package com.github.se.amber.ui.patient

import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
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
    val signedOut: Boolean = false,
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
    private val authRepository: AuthRepository = AuthRepositoryProvider.repository,
) : ViewModel() {

  // Home UI state
  private val _uiState = MutableStateFlow(PatientHomeUIState())
  val uiState: StateFlow<PatientHomeUIState> = _uiState.asStateFlow()

  init {
    getProfile()
  }

  fun clearErrorMsg() {
    _uiState.value = _uiState.value.copy(errorMsg = null)
  }

  /** Sets an error message in the UI state. */
  private fun setErrorMsg(errorMsg: String) {
    _uiState.value = _uiState.value.copy(errorMsg = errorMsg)
  }

  /** Fetches the profile from the repository and updates the UI state. */
  private fun getProfile() {
    viewModelScope.launch {
      profileRepository
          .getCurrentProfile()
          .fold(
              onSuccess = { profile ->
                if (profile != null) {
                  _uiState.value = PatientHomeUIState(patientProfile = profile)
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

  /** Initiates sign-out and update the UI state on success or failure */
  fun signOut(credentialManager: CredentialManager) {
    viewModelScope.launch {
      authRepository
          .signOut()
          .fold(
              onSuccess = {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
                _uiState.update { it.copy(signedOut = true) }
              },
              onFailure = { throwable ->
                _uiState.update { it.copy(errorMsg = throwable.localizedMessage) }
              },
          )
    }
  }
}
