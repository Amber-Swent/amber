// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.patient.seeMedias.medias

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.user.UserProfile
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

/**
 * UI state shared by every "See medias" screen (pictures, audios...). Errors are string resources,
 * which the screen resolves in the app language.
 *
 * @param T the kind of media displayed.
 */
sealed interface SeeGeneralMediasUIState<out T : MediaItem> {
  data object Loading : SeeGeneralMediasUIState<Nothing>

  /** Nothing can be shown: [errorMsgRes] replaces the screen, with a Retry button. */
  data class BlockingError(@param:StringRes val errorMsgRes: Int) : SeeGeneralMediasUIState<Nothing>

  /** Everything was loaded but there is no media of this kind to display. */
  data class Empty(
      val patientProfile: UserProfile,
      val careCircle: CareCircle,
      @param:StringRes val errorMsgRes: Int? = null,
  ) : SeeGeneralMediasUIState<Nothing>

  data class Content<out T : MediaItem>(
      val patientProfile: UserProfile,
      val careCircle: CareCircle,
      val items: List<T>,
      @param:StringRes val errorMsgRes: Int? = null,
  ) : SeeGeneralMediasUIState<T>
}

/**
 * The blocking / non-blocking error messages of a "See medias" screen, as string resources. Each
 * kind of media passes its own, so existing strings (and translations) stay as they are.
 */
data class SeeGeneralMediasErrors(
    @param:StringRes val loadProfile: Int,
    @param:StringRes val noProfile: Int,
    @param:StringRes val noCircle: Int,
    @param:StringRes val loadCircle: Int,
    @param:StringRes val loadMedia: Int,
)

/**
 * Loads the patient's profile, their first care circle, then observes the circle's approved media
 * and keeps only the ones of type [T].
 *
 * A subclass supplies the filter ([filterMedia]) and may react to list changes ([onItemsChanged]).
 * It MUST call [load] at the end of its own `init` block: starting the load from this class's
 * `init` would run before the subclass' properties exist.
 *
 * @param T the kind of media displayed.
 * @param mediaRepository observes approved media (and downloads files for subclasses that need it).
 * @param circleRepository loads the patient's care circle.
 * @param profileRepository loads the current patient profile.
 * @param errors the error messages of this kind of media.
 */
abstract class SeeGeneralMediasViewModel<T : MediaItem>(
    protected val mediaRepository: MediaRepository,
    private val circleRepository: CareCircleRepository,
    private val profileRepository: ProfileRepository,
    private val errors: SeeGeneralMediasErrors,
) : ViewModel() {

  private val _uiState =
      MutableStateFlow<SeeGeneralMediasUIState<T>>(SeeGeneralMediasUIState.Loading)
  val uiState: StateFlow<SeeGeneralMediasUIState<T>> = _uiState.asStateFlow()

  private var loadJob: Job? = null

  /** Keeps only the media of type [T], in the same order. */
  protected abstract fun filterMedia(media: List<MediaItem>): List<T>

  /**
   * Called with each new list of [T] before it is published in the UI state. Nothing by default.
   */
  protected open fun onItemsChanged(items: List<T>) {
    // Nothing to do by default
  }

  /** Reloads everything and shows the loading state. */
  fun retry() = load(showLoading = true)

  /** Reloads everything without flashing the loading screen. */
  fun refreshUIState() = load(showLoading = false)

  /** Clears the non-blocking error message in the UI state. */
  fun clearErrorMsg() {
    when (val state = _uiState.value) {
      is SeeGeneralMediasUIState.Empty -> _uiState.value = state.copy(errorMsgRes = null)
      is SeeGeneralMediasUIState.Content -> _uiState.value = state.copy(errorMsgRes = null)
      else -> Unit
    }
  }

  /**
   * Non-blocking error: if something is already displayed we keep it and add a message, otherwise
   * we fall back to a blocking error.
   */
  private fun setNonBlockingErrorMsg(@StringRes errorMsgRes: Int) {
    when (val state = _uiState.value) {
      is SeeGeneralMediasUIState.Empty -> _uiState.value = state.copy(errorMsgRes = errorMsgRes)
      is SeeGeneralMediasUIState.Content -> _uiState.value = state.copy(errorMsgRes = errorMsgRes)
      is SeeGeneralMediasUIState.Loading,
      is SeeGeneralMediasUIState.BlockingError -> setBlockingErrorMsg(errorMsgRes)
    }
  }

  /** Blocking error: replaces the whole screen with an error message and a Retry button. */
  private fun setBlockingErrorMsg(@StringRes errorMsgRes: Int) {
    _uiState.value = SeeGeneralMediasUIState.BlockingError(errorMsgRes)
  }

  /** Sequentially loads profile, circle id, care circle and then observes the media. */
  protected fun load(showLoading: Boolean = true) {
    loadJob?.cancel()
    if (showLoading) _uiState.value = SeeGeneralMediasUIState.Loading

    loadJob = viewModelScope.launch {
      // 1. Profile
      val profile =
          profileRepository.getCurrentProfile().getOrElse { e ->
            Log.e(tag, "Error fetching profile", e)
            setBlockingErrorMsg(errors.loadProfile)
            return@launch
          }
              ?: run {
                setBlockingErrorMsg(errors.noProfile)
                return@launch
              }

      // 2. Circle id (explicit error if missing)
      val circleId = profile.circleIds.firstOrNull { it.isNotBlank() }
      if (circleId == null) {
        setBlockingErrorMsg(errors.noCircle)
        return@launch
      }

      // 3. Care circle
      val circle =
          circleRepository.getCircle(circleId).getOrElse { e ->
            Log.e(tag, "Error fetching care circle", e)
            setBlockingErrorMsg(errors.loadCircle)
            return@launch
          }

      // 4. Media (live flow, already ordered newest first)
      mediaRepository
          .observeApprovedMedia(circleId)
          .retryWhen { e, attempt ->
            Log.e(tag, "Error observing media", e)
            // Show the message once, not on every retry
            if (attempt == 0L) setNonBlockingErrorMsg(errors.loadMedia)
            // Retry the subscription; the current list stays visible while we wait.
            delay(mediaRetryDelay)
            true
          }
          .collect { media ->
            val items = filterMedia(media)
            onItemsChanged(items)
            _uiState.value =
                if (items.isEmpty()) {
                  SeeGeneralMediasUIState.Empty(patientProfile = profile, careCircle = circle)
                } else {
                  SeeGeneralMediasUIState.Content(
                      patientProfile = profile,
                      careCircle = circle,
                      items = items,
                  )
                }
          }
    }
  }

  private val tag: String
    get() = javaClass.simpleName

  private companion object {
    /** How long to wait before re-subscribing to the media after an error. */
    val mediaRetryDelay = 3_000L.milliseconds
  }
}
