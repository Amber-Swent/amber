// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.patient.seeMedias.audios

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.media.audio.AudioPlayer
import com.github.se.amber.data.media.audio.AudioPlayerProvider
import com.github.se.amber.data.media.audio.AudioState
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MediaRepositoryProvider
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.media.audio.AudioPlaybackState
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

/**
 * Patient audio list state. Errors block the screen until data is loaded; later errors keep the
 * current list visible. Playback state is exposed separately for each audio ID.
 */
sealed interface SeeAudiosUIState {
  data object Loading : SeeAudiosUIState

  data class BlockingError(@param:StringRes val errorMsgRes: Int) : SeeAudiosUIState

  data class Empty(
      val patientProfile: UserProfile,
      val careCircle: CareCircle,
      @param:StringRes val errorMsgRes: Int? = null,
  ) : SeeAudiosUIState

  data class Content(
      val patientProfile: UserProfile,
      val careCircle: CareCircle,
      val allAudios: List<MediaItem.Audio>,
      @param:StringRes val errorMsgRes: Int? = null,
  ) : SeeAudiosUIState
}

/**
 * Loads the patient's approved audio memories from their first care circle. Uses the shared
 * [AudioPlayer] and exposes the state of each audio card without changing the list state.
 *
 * @param mediaRepository observes approved media and downloads audio files.
 * @param circleRepository loads the patient's care circle.
 * @param profileRepository loads the current patient profile.
 * @param audioPlayer plays audio and reports playback progress.
 */
class SeeAudiosViewModel(
    private val mediaRepository: MediaRepository = MediaRepositoryProvider.repository,
    private val circleRepository: CareCircleRepository =
        CareCircleRepositoryProvider.default.repository,
    private val profileRepository: ProfileRepository = ProfileRepositoryProvider.default.repository,
    private val audioPlayer: AudioPlayer = AudioPlayerProvider.player,
) : ViewModel() {
  private val _uiState = MutableStateFlow<SeeAudiosUIState>(SeeAudiosUIState.Loading)
  val uiState: StateFlow<SeeAudiosUIState> = _uiState.asStateFlow()
  private val _playbackStates = MutableStateFlow<Map<String, AudioPlaybackState>>(emptyMap())
  val playbackStates: StateFlow<Map<String, AudioPlaybackState>> = _playbackStates.asStateFlow()

  private var loadJob: Job? = null
  private var playbackJob: Job? = null
  private var activeAudioId: String? = null
  private var activeFile: File? = null
  private var loadingAudioId: String? = null

  init {
    viewModelScope.launch { audioPlayer.state.collect(::updatePlaybackState) }
    load()
  }

  /** Reloads the screen after a blocking error. */
  fun retry() = load(showLoading = true)

  /** Reloads data while keeping the current screen visible. */
  fun refreshUIState() = load(showLoading = false)

  /**
   * Downloads [audio] on first play, then pauses or resumes its loaded file. Download and play
   * failures show an error on that audio card.
   *
   * @param audio the memory whose playback button was pressed.
   */
  fun onPlayPause(audio: MediaItem.Audio) {
    val circle = (_uiState.value as? SeeAudiosUIState.Content)?.careCircle ?: return
    val playerState = audioPlayer.state.value

    // Audio already loaded
    if (activeAudioId == audio.id && playerState.file == activeFile && activeFile != null) {
      // If an audio is downloading when this method is called, the download is canceled
      loadingAudioId
          ?.takeIf { it != audio.id }
          ?.let { id ->
            playbackJob?.cancel()
            // Audio that was downloading is set to Idle
            setPlaybackState(
                id,
                AudioPlaybackState.Idle(durationMs = _playbackStates.value[id]?.durationMs ?: 0L),
            )
            loadingAudioId = null
          }
      if (playerState.isPlaying) audioPlayer.pause() else audioPlayer.resume()
      return
    }

    // Currently played audio is paused when another one is downloaded
    playbackJob?.cancel()
    if (activeFile != null && playerState.file == activeFile && playerState.isPlaying) {
      audioPlayer.pause()
    }
    loadingAudioId
        ?.takeIf { it != audio.id }
        ?.let { id ->
          setPlaybackState(
              id,
              AudioPlaybackState.Idle(durationMs = _playbackStates.value[id]?.durationMs ?: 0L),
          )
        }
    loadingAudioId = audio.id
    setPlaybackState(audio.id, AudioPlaybackState.Loading(durationMs = audio.durationMs))
    playbackJob = viewModelScope.launch {
      try {
        val file = mediaRepository.getFile(circle, audio)
        activeAudioId
            ?.takeIf { it != audio.id }
            ?.let { id ->
              setPlaybackState(
                  id,
                  AudioPlaybackState.Idle(durationMs = _playbackStates.value[id]?.durationMs ?: 0L),
              )
            }
        activeAudioId = audio.id
        activeFile = file
        audioPlayer.play(file)
        loadingAudioId = null
        updatePlaybackState(audioPlayer.state.value)
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        Log.e(TAG, "Error playing audio", error)
        loadingAudioId = null
        setPlaybackState(audio.id, AudioPlaybackState.Error(durationMs = audio.durationMs))
      }
    }
  }

  private fun setPlaybackState(id: String, state: AudioPlaybackState) {
    _playbackStates.value += (id to state)
  }

  /** Applies player progress only when its file matches this screen's active audio. */
  private fun updatePlaybackState(playerState: AudioState) {
    val id = activeAudioId ?: return
    if (loadingAudioId == id) return
    val previousState = _playbackStates.value[id]
    if (
        previousState is AudioPlaybackState.Error &&
            (playerState.file == null || playerState.file == activeFile)
    )
        return
    val durationMs = previousState?.durationMs ?: 0L
    val state =
        if (activeFile != null && playerState.file == activeFile) {
          if (playerState.isPlaying) {
            AudioPlaybackState.Playing(playerState.positionMs, playerState.durationMs)
          } else {
            AudioPlaybackState.Paused(playerState.positionMs, playerState.durationMs)
          }
        } else if (
            activeFile != null &&
                playerState.file == null &&
                previousState is AudioPlaybackState.Playing
        ) {
          AudioPlaybackState.Error(durationMs = durationMs)
        } else {
          // Idle if the current file is not the file for the active audio
          // Only setting the state to Idle without the above "else if" was hiding player failures
          // that
          // would clear the current file to null, therefore entering this else condition and
          // setting the
          // state to Idle instead showing an error.
          AudioPlaybackState.Idle(durationMs = durationMs)
        }
    setPlaybackState(id, state)
  }

  /** Seeks the loaded [audio] to the start. */
  fun onRestart(audio: MediaItem.Audio) {
    if (activeAudioId == audio.id && audioPlayer.state.value.file == activeFile) {
      audioPlayer.seekTo(0L)
    }
  }

  /** Clears the transient media-loading error after the screen displays it. */
  fun clearErrorMsg() {
    when (val state = _uiState.value) {
      is SeeAudiosUIState.Empty -> _uiState.value = state.copy(errorMsgRes = null)
      is SeeAudiosUIState.Content -> _uiState.value = state.copy(errorMsgRes = null)
      else -> Unit
    }
  }

  /** Keeps an existing state on error and falls back to a blocking error while loading. */
  private fun setNonBlockingErrorMsg(@StringRes errorMsgRes: Int) {
    when (val state = _uiState.value) {
      is SeeAudiosUIState.Empty -> _uiState.value = state.copy(errorMsgRes = errorMsgRes)
      is SeeAudiosUIState.Content -> _uiState.value = state.copy(errorMsgRes = errorMsgRes)
      is SeeAudiosUIState.Loading,
      is SeeAudiosUIState.BlockingError -> setBlockingErrorMsg(errorMsgRes)
    }
  }

  private fun setBlockingErrorMsg(@StringRes errorMsgRes: Int) {
    _uiState.value = SeeAudiosUIState.BlockingError(errorMsgRes)
  }

  private fun load(showLoading: Boolean = true) {
    loadJob?.cancel()
    if (showLoading) _uiState.value = SeeAudiosUIState.Loading

    loadJob = viewModelScope.launch {
      // Get patient's profile
      val profile =
          profileRepository.getCurrentProfile().getOrElse { error ->
            Log.e(TAG, "Error fetching profile", error)
            setBlockingErrorMsg(R.string.seeAudiosErrorLoadProfile)
            return@launch
          }
              ?: run {
                setBlockingErrorMsg(R.string.seeAudiosErrorNoProfile)
                return@launch
              }

      // Get patient's circle id
      val circleId = profile.circleIds.firstOrNull { it.isNotBlank() }
      if (circleId == null) {
        setBlockingErrorMsg(R.string.seeAudiosErrorNoCircle)
        return@launch
      }

      // Get patient's care circle
      val circle =
          circleRepository.getCircle(circleId).getOrElse { error ->
            Log.e(TAG, "Error fetching care circle", error)
            setBlockingErrorMsg(R.string.seeAudiosErrorLoadCircle)
            return@launch
          }

      // The approved media flow is already ordered newest first.
      val mediaFlow: Flow<List<MediaItem>> = mediaRepository.observeApprovedMedia(circleId)
      mediaFlow
          .retryWhen { error, attempt ->
            Log.e(TAG, "Error observing media", error)
            if (attempt == 0L) setNonBlockingErrorMsg(R.string.seeAudiosErrorLoadMedia)
            // Retry the media subscription; the current list stays visible while we wait.
            delay(3_000L.milliseconds)
            true
          }
          .collect { media ->
            val audios = media.filterIsInstance<MediaItem.Audio>()
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

  override fun onCleared() {
    playbackJob?.cancel()
    activeFile?.let(audioPlayer::stop)
    super.onCleared() // NOSONAR: the parent may add cleanup here later
  }

  private companion object {
    const val TAG = "SeeAudiosViewModel"
  }
}
