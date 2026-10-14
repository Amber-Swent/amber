// this code was written with the aid of AI

package com.github.se.amber.ui.patient.see_pictures

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MediaRepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.FirebaseProfileRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlin.coroutines.cancellation.CancellationException


data class SeePicturesUIState(
    val patientProfile: UserProfile =
        UserProfile(
            uid = "",
            role = Role.PATIENT,
            person = Person(),
            circleIds = emptyList(),
        ),
    val careCircle: CareCircle = CareCircle(),
    val errorMsg: String? = null,
    val allPictures: List<MediaItem> = emptyList(),
){

}

class SeePicturesViewModel(
    private val mediaRepository: MediaRepository = MediaRepositoryProvider.repository,
    private val circleRepository: CareCircleRepository = CareCircleRepositoryProvider.default.repository,
    private val profileRepository: ProfileRepository = FirebaseProfileRepository()
) : ViewModel() {

    // See Pictures UI state
    private val _uiState = MutableStateFlow(SeePicturesUIState())
    val uiState: StateFlow<SeePicturesUIState> = _uiState.asStateFlow()


    init{
        getProfile()
        getCareCircle()
        getAllPictures()
    }


    /** Refreshes the UI state by fetching all pictures from the repository */
    fun refreshUIState(){
        getAllPictures()
    }

    /** Clears the error message in the UI state. */
    fun clearErrorMsg(){
        _uiState.value = _uiState.value.copy(errorMsg = null)
    }

    /** Sets an error message in the UI state. */
    private fun setErrorMsg(errorMsg: String) {
        _uiState.value = _uiState.value.copy(errorMsg = errorMsg)
    }

    /** Fetches all pictures from the repository and update the UI state */
    private fun getAllPictures() {
        viewModelScope.launch {
            try {
                // wait that the profil is loaded to get the id of the carecircle.
                val circleId =
                    _uiState
                        .map { it.patientProfile.circleIds.firstOrNull() }
                        .filterNotNull()
                        .first()

                mediaRepository.observeApprovedMedia(circleId).collect { medias ->
                    _uiState.value = _uiState.value.copy(allPictures = medias)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SeePicturesViewModel", "Error observing media", e)
                setErrorMsg("Failed to load media: ${e.message}")
            }
        }
    }

    /** Fetches the profile from the repository and updates the UI state. */
    private fun getProfile() {
        viewModelScope.launch {
            profileRepository
                .getCurrentProfile()
                .fold(
                    onSuccess = { profile ->
                        if (profile != null) {
                            _uiState.value = _uiState.value.copy(patientProfile = profile)
                        } else {
                            setErrorMsg("No profile found.")
                        }
                    },
                    onFailure = { e ->
                        Log.e("SeePicturesViewModel", "Error fetching profile", e)
                        setErrorMsg("Failed to load the profile: ${e.message}")
                    },
                )
        }
    }

    /** Fetches the patient's care circle once the profile is loaded and updates the UI state. */
    private fun getCareCircle() {
        viewModelScope.launch {
            // wait that the profil is loaded to get the id of the carecircle.
            val circleId =
                _uiState
                    .map { it.patientProfile.circleIds.firstOrNull() }
                    .filterNotNull()
                    .first()

            circleRepository
                .getCircle(circleId)
                .fold(
                    onSuccess = { circle ->
                        _uiState.value = _uiState.value.copy(careCircle = circle)
                    },
                    onFailure = { e ->
                        Log.e("SeePicturesViewModel", "Error fetching care circle", e)
                        setErrorMsg("Failed to load the care circle: ${e.message}")
                    },
                )
        }
    }
}