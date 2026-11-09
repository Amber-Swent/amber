// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.media

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
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

/**
 * UI state of one [MediaCard].
 *
 * @property addedByNickname name of the person who added the media.
 * @property relatedNicknames names of the persons shown in the media.
 * @property isLoading whether the names are still loading.
 * @property errorMsgRes non-blocking error to show in a toast, as a string resource, or null.
 */
data class MediaCardUIState(
    val addedByNickname: String = "",
    val relatedNicknames: List<String> = emptyList(),
    val isLoading: Boolean = true,
    @param:StringRes val errorMsgRes: Int? = null,
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
    _uiState.value = _uiState.value.copy(errorMsgRes = null)
  }

  /** Sets an error message in the UI state, as a string resource. */
  private fun setErrorMsg(@StringRes errorMsgRes: Int) {
    // to be able to retry to load when we next see it
    loadedMedia = null
    _uiState.value = _uiState.value.copy(isLoading = false, errorMsgRes = errorMsgRes)
  }

  /**
   * Returns the value of this [Result], or logs the failure, sets [errorRes] as the error message
   * and calls [onFailure] to stop the load. The exception message is only logged, never shown. A
   * [CancellationException] is always rethrown, so that the cancellation of the coroutine is not
   * turned into an error message.
   */
  private inline fun <T> Result<T>.getOrSetError(
      logMessage: String,
      @StringRes errorRes: Int,
      onFailure: () -> Nothing,
  ): T = getOrElse { e ->
    if (e is CancellationException) throw e
    Log.e(TAG, logMessage, e)
    setErrorMsg(errorRes)
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
              errorRes = R.string.mediaCardErrorLoadAuthor,
          ) {
            return@launch
          }

      if (authorProfile == null) {
        setErrorMsg(R.string.mediaCardErrorAuthorNotFound)
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
              R.string.mediaCardErrorLoadProfile,
          ) {
            return@launch
          }

      val circleId = patientProfile?.circleIds?.firstOrNull()
      if (circleId == null) {
        setErrorMsg(R.string.mediaCardErrorNoCircle)
        return@launch
      }

      // 3. Care circle
      val circle =
          circleRepository.getCircle(circleId).getOrSetError(
              "Error fetching care circle",
              R.string.mediaCardErrorLoadCircle,
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
