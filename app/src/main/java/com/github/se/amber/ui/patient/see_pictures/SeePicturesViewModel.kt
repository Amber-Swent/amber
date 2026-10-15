// this code was written with the aid of AI

package com.github.se.amber.ui.patient.see_pictures

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.profile.FirebaseProfileRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MediaRepositoryProvider
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SeePicturesUIState(
    val patientProfile: UserProfile =
        UserProfile(
            uid = "",
            role = Role.PATIENT,
            person = Person(),
            circleIds = emptyList(),
        ),
    val careCircle: CareCircle = CareCircle(),
    val allMediaItems: List<MediaItem> = emptyList(),
    val allPictures: List<MediaItem.Picture> = emptyList(),
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val errorMsg: String? = null,
)

class SeePicturesViewModel(
    private val mediaRepository: MediaRepository = MediaRepositoryProvider.repository,
    private val circleRepository: CareCircleRepository =
        CareCircleRepositoryProvider.default.repository,
    private val profileRepository: ProfileRepository = FirebaseProfileRepository(),
) : ViewModel() {

  // See Pictures UI state
  private val _uiState = MutableStateFlow(SeePicturesUIState())
  val uiState: StateFlow<SeePicturesUIState> = _uiState.asStateFlow()

  //  completed when getProfile() has finish to get the profile, so profil is either null or exist
  private var profileResult = CompletableDeferred<UserProfile?>()
  private var picturesJob: Job? = null
  private var circleLoaded = false
  private var mediaLoaded = false

  init {
    getProfile()
    getCareCircle()
    getAllPictures()
  }

  /** Refreshes the UI state by fetching all pictures from the repository */
  fun refreshUIState() {
    getAllPictures()
  }

  /** Clears the error message in the UI state. */
  fun clearErrorMsg() {
    _uiState.value = _uiState.value.copy(errorMsg = null)
  }

  /** Non-blocking errors: sets an error message in the UI state. */
  private fun setErrorMsg(errorMsg: String) {
    _uiState.value = _uiState.value.copy(errorMsg = errorMsg)
  }

  /** Blocking error: shown inside the screen with a Retry button. */
  private fun setLoadError(message: String) {
    _uiState.value = _uiState.value.copy(isLoading = false, loadError = message)
  }

  /** Stops the loading state once both the care circle and the first media list are there. */
  private fun updateLoading() {
    if (circleLoaded && mediaLoaded) {
      _uiState.value = _uiState.value.copy(isLoading = false)
    }
  }

  /** Reloads profile, care circle and pictures. */
  fun retry() {
    profileResult = CompletableDeferred()
    _uiState.value = _uiState.value.copy(isLoading = true, loadError = null)
    circleLoaded = false
    mediaLoaded = false
    getProfile()
    getCareCircle()
    getAllPictures()
  }

  /** wait the end of the profil loading and give back either null or the id of the carecircle */
  private suspend fun awaitCircleId(): String? =
      profileResult.await()?.circleIds?.firstOrNull { it.isNotBlank() }

  /** Fetches all pictures from the repository and update the UI state */
  private fun getAllPictures() {
    picturesJob?.cancel()
    picturesJob = viewModelScope.launch {
      try {
        // wait that the profil is loaded to get the id of the carecircle.
        val circleId = awaitCircleId() ?: return@launch

        mediaRepository.observeApprovedMedia(circleId).collect { medias ->
          _uiState.value =
              _uiState.value.copy(
                  allMediaItems = medias,
                  allPictures = medias.filterIsInstance<MediaItem.Picture>(),
              )
          mediaLoaded = true
          updateLoading()
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e("SeePicturesViewModel", "Error observing media", e)
        setErrorMsg("Failed to load media: ${e.message}")
        mediaLoaded = true
        updateLoading()
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
                  if (profile.circleIds.none { it.isNotBlank() }) {
                    setLoadError("No care circle found for this patient.")
                  }
                } else {
                  setLoadError("No profile found.")
                }
                profileResult.complete(profile)
              },
              onFailure = { e ->
                Log.e("SeePicturesViewModel", "Error fetching profile", e)
                setLoadError("Failed to load the profile: ${e.message}")
                profileResult.complete(null)
              },
          )
    }
  }

  /** Fetches the patient's care circle once the profile is loaded and updates the UI state. */
  private fun getCareCircle() {
    viewModelScope.launch {
      // wait that the profil is loaded to get the id of the carecircle.
      val circleId = awaitCircleId() ?: return@launch

      circleRepository
          .getCircle(circleId)
          .fold(
              onSuccess = { circle ->
                _uiState.value = _uiState.value.copy(careCircle = circle)
                circleLoaded = true
                updateLoading()
              },
              onFailure = { e ->
                Log.e("SeePicturesViewModel", "Error fetching care circle", e)
                setLoadError("Failed to load the care circle: ${e.message}")
              },
          )
    }
  }
}
