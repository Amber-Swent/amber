// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Alaabrine, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.patient

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state of the patient home screen.
 *
 * @property patientProfile profile of the signed-in patient, whose first name is greeted.
 * @property errorMsgRes the error to show in a toast, as a string resource, or null for none.
 */
data class PatientHomeUIState(
    val patientProfile: UserProfile =
        UserProfile(
            uid = "",
            role = Role.PATIENT,
            person = Person(),
            circleIds = emptyList(),
        ),
    @param:StringRes val errorMsgRes: Int? = null,
)

/**
 * ViewModel for the Patient Home screen.
 *
 * Responsible for managing the UI state
 *
 * @property profileRepository The repository used to fetch the Profile.
 */
class PatientHomeViewModel(
    private val profileRepository: ProfileRepository = ProfileRepositoryProvider.default.repository,
) : ViewModel() {

  // Home UI state
  private val _uiState = MutableStateFlow(PatientHomeUIState())
  val uiState: StateFlow<PatientHomeUIState> = _uiState.asStateFlow()

  init {
    getProfile()
  }

  /** Clears the error message in the UI state. */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMsgRes = null) }
  }

  /** Sets an error message in the UI state, as a string resource. */
  private fun setErrorMsg(@StringRes errorMsgRes: Int) {
    _uiState.update { it.copy(errorMsgRes = errorMsgRes) }
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
                  setErrorMsg(R.string.patientHomeErrorNoProfile)
                }
              },
              onFailure = { e ->
                Log.e("PatientHomeViewModel", "Error fetching profile", e)
                setErrorMsg(R.string.patientHomeErrorLoadProfile)
              },
          )
    }
  }
}
