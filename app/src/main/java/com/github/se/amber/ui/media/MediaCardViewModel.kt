// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.media

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.media.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal const val FAIL_LOAD_AUTHOR_PROFILE = "Failed to load the author profile:"
internal const val FAIL_LOAD_CURRENT_PROFILE = "Failed to load the current profile:"
internal const val FAIL_LOAD_CARE_CIRCLE = "Failed to load the care circle:"
internal const val AUTHOR_NOT_FOUND = "Author not found."
internal const val NO_CARE_CIRCLE_FOUND = "No care circle found."

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
   * Returns the value of this [Result], or logs the failure, sets an error message and calls
   * [onFailure] to stop the load. A [CancellationException] is always rethrown, so that the
   * cancellation of the coroutine is not turned into an error message.
   */
  private inline fun <T> Result<T>.getOrSetError(
      logMessage: String,
      errorPrefix: String,
      onFailure: () -> Nothing,
  ): T = getOrElse { e ->
    if (e is CancellationException) throw e
    Log.e(TAG, logMessage, e)
    setErrorMsg("$errorPrefix ${e.message}")
    onFailure()
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
          profileRepository.getProfile(media.authorId).getOrSetError(
              logMessage = "Error fetching author profile",
              errorPrefix = FAIL_LOAD_AUTHOR_PROFILE,
          ) {
            return@launch
          }

      if (authorProfile == null) {
        setErrorMsg(AUTHOR_NOT_FOUND)
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
          profileRepository.getCurrentProfile().getOrSetError(
              "Error fetching patient profile",
              FAIL_LOAD_CURRENT_PROFILE,
          ) {
            return@launch
          }

      val circleId = patientProfile?.circleIds?.firstOrNull()
      if (circleId == null) {
        setErrorMsg(NO_CARE_CIRCLE_FOUND)
        return@launch
      }

      // 3. Care circle
      val circle =
          circleRepository.getCircle(circleId).getOrSetError(
              "Error fetching care circle",
              FAIL_LOAD_CARE_CIRCLE,
          ) {
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
