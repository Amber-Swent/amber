package com.github.se.amber.ui.media

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.media.MediaItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MediaCardUIState(
    val addedByNickname: String = "",
    val relatedNicknames: List<String> = emptyList(),
    val isLoading: Boolean = true,
    // non-blocking error
    val errorMsg: String? = null,
)

class MediaCardViewModel(
    private val profileRepository: ProfileRepository = ProfileRepositoryProvider.default.repository,
    private val circleRepository: CareCircleRepository =
        CareCircleRepositoryProvider.default.repository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(MediaCardUIState())
  val uiState: StateFlow<MediaCardUIState> = _uiState.asStateFlow()

  private var loadJob: Job? = null
  private var loadedMedia: MediaItem? = null

  /** Clears the error message in the UI state. */
  fun clearErrorMsg() {
    _uiState.value = _uiState.value.copy(errorMsg = null)
  }

  /** Sets an error message in the UI state. */
  private fun setErrorMsg(message: String) {
    // to be able to retry to load when we next see it
    loadedMedia = null
    _uiState.value = _uiState.value.copy(isLoading = false, errorMsg = message)
  }

  /**
   * Loads the data needed to display the card of [media]: the nickname of the person who added it
   * and the nickames of all the related persons Cancels any previous load still running.
   *
   * @param media the media we want the card of
   */
  fun load(media: MediaItem) {
    if (media == loadedMedia) return
    loadedMedia = media
    loadJob?.cancel()
    _uiState.value = MediaCardUIState(isLoading = true)

    loadJob = viewModelScope.launch {
      // 1. Author profile
      val authorProfile =
          profileRepository.getProfile(media.authorId).getOrElse { e ->
            Log.e(TAG, "Error fetching author profile", e)
            setErrorMsg("Failed to load the author: ${e.message}")
            return@launch
          }

      if (authorProfile == null) {
        setErrorMsg("Author not found.")
        return@launch
      }
      _uiState.value =
          MediaCardUIState(
              // TODO replace this by the nickname once it is add to the createProfile screen
              addedByNickname = authorProfile.person.firstName,
              isLoading = false,
          )
      // Nothing to look up if nobody is credited on this media
      if (media.shownIds.isEmpty()) return@launch

      // 2. Patient profile (the current profile) -> first care circle id
      val patientProfile =
          profileRepository.getCurrentProfile().getOrElse { e ->
            Log.e(TAG, "Error fetching patient profile", e)
            setErrorMsg("Failed to load the profile: ${e.message}")
            return@launch
          }

      val circleId = patientProfile?.circleIds?.firstOrNull()
      if (circleId == null) {
        setErrorMsg("No care circle found.")
        return@launch
      }

      // 3. Care circle
      val circle =
          circleRepository.getCircle(circleId).getOrElse { e ->
            Log.e(TAG, "Error fetching care circle", e)
            setErrorMsg("Failed to load the care circle: ${e.message}")
            return@launch
          }

      // 4. shownIds -> first names: people without an account first, then members' profiles
      // TODO replace this by the nickname once it is add to the createProfile screen
      val relatedNicknames =
          media.shownIds.mapNotNull { id ->
            circle.people[id]?.firstName
                ?: profileRepository.getProfile(id).getOrNull()?.person?.firstName
          }

      _uiState.value = _uiState.value.copy(relatedNicknames = relatedNicknames)
    }
  }

  private companion object {
    const val TAG = "MediaCardViewModel"
  }
}
