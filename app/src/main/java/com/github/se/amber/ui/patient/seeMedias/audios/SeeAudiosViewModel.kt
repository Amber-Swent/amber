// Written by MeiliV, with assistance from OpenAI Codex.
// Modified by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.patient.seeMedias.audios

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MediaRepositoryProvider
import com.github.se.amber.model.user.UserProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed interface SeeAudiosUIState {
  data object Loading : SeeAudiosUIState

  data class BlockingError(val message: String) : SeeAudiosUIState

  data class Empty(
      val patientProfile: UserProfile,
      val careCircle: CareCircle,
      val errorMsg: String? = null,
  ) : SeeAudiosUIState

  data class Content(
      val patientProfile: UserProfile,
      val careCircle: CareCircle,
      val allAudios: List<MediaItem.Audio>,
      val errorMsg: String? = null,
  ) : SeeAudiosUIState
}

/** Loads the patient, care circle, and approved audio memories. */
class SeeAudiosViewModel(
    private val mediaRepository: MediaRepository = MediaRepositoryProvider.repository,
    private val circleRepository: CareCircleRepository =
        CareCircleRepositoryProvider.default.repository,
    private val profileRepository: ProfileRepository = ProfileRepositoryProvider.default.repository,
) : ViewModel() {
  private val _uiState = MutableStateFlow<SeeAudiosUIState>(SeeAudiosUIState.Loading)
  val uiState: StateFlow<SeeAudiosUIState> = _uiState.asStateFlow()

  private var loadJob: Job? = null

  init {
    load()
  }

  fun retry() = load(showLoading = true)

  fun refreshUIState() = load(showLoading = false)

  fun clearErrorMsg() {
    when (val state = _uiState.value) {
      is SeeAudiosUIState.Empty -> _uiState.value = state.copy(errorMsg = null)
      is SeeAudiosUIState.Content -> _uiState.value = state.copy(errorMsg = null)
      else -> Unit
    }
  }

  /** Keeps an existing state on error and falls back to a blocking error while loading. */
  private fun setNonBlockingErrorMsg(message: String) {
    when (val state = _uiState.value) {
      is SeeAudiosUIState.Empty -> _uiState.value = state.copy(errorMsg = message)
      is SeeAudiosUIState.Content -> _uiState.value = state.copy(errorMsg = message)
      is SeeAudiosUIState.Loading,
      is SeeAudiosUIState.BlockingError -> setBlockingErrorMsg(message)
    }
  }

  private fun setBlockingErrorMsg(message: String) {
    _uiState.value = SeeAudiosUIState.BlockingError(message)
  }

  private fun load(showLoading: Boolean = true) {
    loadJob?.cancel()
    if (showLoading) _uiState.value = SeeAudiosUIState.Loading

    loadJob = viewModelScope.launch {
      // Get patient's profile
      val profile =
          profileRepository.getCurrentProfile().getOrElse { error ->
            Log.e(TAG, "Error fetching profile", error)
            setBlockingErrorMsg("Failed to load the profile: ${error.message}")
            return@launch
          }
              ?: run {
                setBlockingErrorMsg("No profile found.")
                return@launch
              }

      // Get patient's circle id
      val circleId = profile.circleIds.firstOrNull { it.isNotBlank() }
      if (circleId == null) {
        setBlockingErrorMsg("No care circle found for this patient.")
        return@launch
      }

      // Get patient's care circle
      val circle =
          circleRepository.getCircle(circleId).getOrElse { error ->
            Log.e(TAG, "Error fetching care circle", error)
            setBlockingErrorMsg("Failed to load the care circle: ${error.message}")
            return@launch
          }

      // The approved media flow is already ordered newest first.
      val mediaFlow: Flow<List<MediaItem>> = mediaRepository.observeApprovedMedia(circleId) // observeApprovedMedia() returns newest first
      mediaFlow
          .catch { error ->
            Log.e(TAG, "Error observing media", error)
            setNonBlockingErrorMsg("Failed to load media: ${error.message}")
          }
          .collect { media ->
            val audios = media.filterIsInstance<MediaItem.Audio>() // Preserves the order so no need to reinforce newest first
            _uiState.value =
                if (audios.isEmpty()) {
                  SeeAudiosUIState.Empty(patientProfile = profile, careCircle = circle)
                } else {
                  SeeAudiosUIState.Content(
                      patientProfile = profile,
                      careCircle = circle,
                      allAudios = audios,
                  )
                }
          }
    }
  }

  private companion object {
    const val TAG = "SeeAudiosViewModel"
  }
}
