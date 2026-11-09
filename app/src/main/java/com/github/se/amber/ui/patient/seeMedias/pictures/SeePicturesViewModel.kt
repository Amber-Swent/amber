// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.patient.seeMedias.pictures

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
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

/**
 * UI state of the See Pictures screen. The errors are string resources, which the screen resolves
 * in the app language.
 */
sealed interface SeePicturesUIState {
  data object Loading : SeePicturesUIState

  /** Nothing can be shown: [messageRes] replaces the screen, with a Retry button. */
  data class BlockingError(@param:StringRes val messageRes: Int) : SeePicturesUIState

  // everything was successful but there are no pictures to display
  data class Empty(
      val patientProfile: UserProfile,
      val careCircle: CareCircle,
      @param:StringRes val errorMsgRes: Int? = null,
  ) : SeePicturesUIState

  data class Content(
      val patientProfile: UserProfile,
      val careCircle: CareCircle,
      val allMediaItems: List<MediaItem>,
      val allPictures: List<MediaItem.Picture>,
      @param:StringRes val errorMsgRes: Int? = null,
  ) : SeePicturesUIState
}

class SeePicturesViewModel(
    private val mediaRepository: MediaRepository = MediaRepositoryProvider.repository,
    private val circleRepository: CareCircleRepository =
        CareCircleRepositoryProvider.default.repository,
    private val profileRepository: ProfileRepository = ProfileRepositoryProvider.default.repository,
) : ViewModel() {

  // See Pictures UI state, start on loading by default.
  private val _uiState = MutableStateFlow<SeePicturesUIState>(SeePicturesUIState.Loading)
  val uiState: StateFlow<SeePicturesUIState> = _uiState.asStateFlow()

  private var loadJob: Job? = null

  init {
    load()
  }

  /** Reloads everything and shows the loading state. */
  fun retry() = load(showLoading = true)

  /** Reloads everything without flashing(display) the loading screen. */
  fun refreshUIState() {
    load(showLoading = false)
  }

  /** Clears the non-blocking error message in the UI state. */
  fun clearErrorMsg() {
    when (val state = _uiState.value) {
      is SeePicturesUIState.Empty -> _uiState.value = state.copy(errorMsgRes = null)
      is SeePicturesUIState.Content -> _uiState.value = state.copy(errorMsgRes = null)
      else -> Unit
    }
  }

  /**
   * Non-blocking error: if something is already displayed we keep it and add a message, otherwise
   * we fall back to a blocking error.
   */
  private fun setNonBlockingErrorMsg(@StringRes messageRes: Int) {
    when (val state = _uiState.value) {
      is SeePicturesUIState.Empty -> _uiState.value = state.copy(errorMsgRes = messageRes)
      is SeePicturesUIState.Content -> _uiState.value = state.copy(errorMsgRes = messageRes)
      is SeePicturesUIState.Loading,
      is SeePicturesUIState.BlockingError -> setBlockingErrorMsg(messageRes)
    }
  }

  /** Blocking error: replaces the whole screen with an error message and a Retry button */
  private fun setBlockingErrorMsg(@StringRes messageRes: Int) {
    _uiState.value = SeePicturesUIState.BlockingError(messageRes)
  }

  /** Sequentially loads profile, circle id, care circle and then observes the media. */
  private fun load(showLoading: Boolean = true) {
    loadJob?.cancel()
    if (showLoading) _uiState.value = SeePicturesUIState.Loading

    loadJob = viewModelScope.launch {
      // 1. Profile
      val profile =
          profileRepository.getCurrentProfile().getOrElse { e ->
            Log.e(TAG, "Error fetching profile", e)
            setBlockingErrorMsg(R.string.seePicturesErrorLoadProfile)
            return@launch
          }
              ?: run {
                setBlockingErrorMsg(R.string.seePicturesErrorNoProfile)
                return@launch
              }

      // 2. Circle id (explicit error if missing)
      val circleId = profile.circleIds.firstOrNull { it.isNotBlank() }
      if (circleId == null) {
        setBlockingErrorMsg(R.string.seePicturesErrorNoCircle)
        return@launch
      }

      // 3. Care circle
      val circle =
          circleRepository.getCircle(circleId).getOrElse { e ->
            Log.e(TAG, "Error fetching care circle", e)
            setBlockingErrorMsg(R.string.seePicturesErrorLoadCircle)
            return@launch
          }

      // 4. Media (live flow)
      val mediaFlow: Flow<List<MediaItem>> = mediaRepository.observeApprovedMedia(circleId)
      mediaFlow
          .catch { e ->
            Log.e(TAG, "Error observing media", e)
            setNonBlockingErrorMsg(R.string.seePicturesErrorLoadMedia)
          }
          .collect { medias ->
            val pictures = medias.filterIsInstance<MediaItem.Picture>()
            _uiState.value =
                if (pictures.isEmpty()) {
                  SeePicturesUIState.Empty(
                      patientProfile = profile,
                      careCircle = circle,
                  )
                } else {
                  SeePicturesUIState.Content(
                      patientProfile = profile,
                      careCircle = circle,
                      allMediaItems = medias,
                      allPictures = pictures,
                  )
                }
          }
    }
  }

  private companion object {
    const val TAG = "SeePicturesViewModel"
  }
}
