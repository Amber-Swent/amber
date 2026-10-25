/* Written by Lou-Anne Maier and + , with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.profile.FirebaseProfileRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state displayed by the caregiver home screen.
 *
 * @param caregiver Caregiver's profile, owner of this Amber account
 * @param careCircle actual careCircle.
 * @param patient Patient of the actual careCircle - whose information is displayed on the screen.
 * @param errorMsg Error message to display, or null when there is no error.
 */
data class CaregiverHomeUIState(
    val caregiver: UserProfile =
        UserProfile(
            uid = "",
            role = Role.CAREGIVER,
            person = Person(),
            circleIds = emptyList(),
        ),
    val careCircle: CareCircle = CareCircle(),
    val patient: UserProfile =
        UserProfile(
            uid = "",
            role = Role.PATIENT,
            person = Person(),
            circleIds = emptyList(),
        ),
    val errorMsg: String? = null,
)

/**
 * ViewModel responsible for providing data to the caregiver home screen.
 *
 * @property profileRepository The repository used to fetch the profile of the current user.
 */
class CaregiverHomeViewModel(
    private val profileRepository: ProfileRepository = FirebaseProfileRepository(),
    private val circleRepository: CareCircleRepository =
        CareCircleRepositoryProvider.default.repository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(CaregiverHomeUIState())
  val uiState: StateFlow<CaregiverHomeUIState> = _uiState.asStateFlow()

  init {
    load()
  }

  /** Clears the error message from the UI state. */
  fun clearErrorMsg() {
    _uiState.value = _uiState.value.copy(errorMsg = null)
  }

  /**
   * Sets an error message in the UI state.
   *
   * @param errorMsg Error message to display in the UI.
   */
  internal fun setErrorMsg(errorMsg: String) {
    _uiState.value = _uiState.value.copy(errorMsg = errorMsg)
  }

  /**
   * Sequentially loads the caregiver profile, its first care circle, and then the patient profile
   * linked to that circle. Each step updates the UI state; on failure an error message is set and
   * the chain stops.
   */
  private fun load() {
    viewModelScope.launch {
      // 1. Caregiver profile
      val caregiver =
          profileRepository
              .getCurrentProfile()
              .fold(
                  onSuccess = { profile ->
                    if (profile == null) setErrorMsg("No profile found.")
                    profile
                  },
                  onFailure = { e ->
                    Log.e(TAG, "Error fetching caregiver profile", e)
                    setErrorMsg("Failed to load the profile: ${e.message}")
                    null
                  },
              ) ?: return@launch
      _uiState.update { it.copy(caregiver = caregiver) }

      // 2. First care circle id of the caregiver
      val circleId = caregiver.circleIds.firstOrNull { it.isNotBlank() }
      if (circleId == null) {
        setErrorMsg("No care circle found for this caregiver.")
        return@launch
      }

      // 3. Care circle
      val circle =
          circleRepository
              .getCircle(circleId)
              .fold(
                  onSuccess = { it },
                  onFailure = { e ->
                    Log.e(TAG, "Error fetching care circle", e)
                    setErrorMsg("Failed to load the care circle: ${e.message}")
                    null
                  },
              ) ?: return@launch
      _uiState.update { it.copy(careCircle = circle) }

      // 4. Patient id (empty until the patient has redeemed their code)
      if (circle.patientId.isBlank()) {
        setErrorMsg("No patient is linked to this care circle yet.")
        return@launch
      }

      // 5. Patient profile
      profileRepository
          .getProfile(circle.patientId)
          .fold(
              onSuccess = { patient ->
                if (patient != null) {
                  _uiState.update { it.copy(patient = patient) }
                } else {
                  setErrorMsg("No patient profile found.")
                }
              },
              onFailure = { e ->
                Log.e(TAG, "Error fetching patient profile", e)
                setErrorMsg("Failed to load the patient profile: ${e.message}")
              },
          )
    }
  }

  private companion object {
    const val TAG = "CaregiverHomeViewModel"
  }
}
