// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.patient.seeMedias.audios

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.media.audio.AudioPlayer
import com.github.se.amber.data.media.audio.AudioPlayerProvider
import com.github.se.amber.data.media.audio.AudioState
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.R
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MediaRepositoryProvider
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.media.audio.AudioPlaybackState
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

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

/** Loads the patient, care circle, and approved audio memories. */
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

  fun retry() = load(showLoading = true)

  fun refreshUIState() = load(showLoading = false)

  fun onPlayPause(audio: MediaItem.Audio) {
    val circle = (_uiState.value as? SeeAudiosUIState.Content)?.careCircle ?: return
    val playerState = audioPlayer.state.value
    if (activeAudioId == audio.id && playerState.file == activeFile && activeFile != null) {
      if (playerState.isPlaying) audioPlayer.pause() else audioPlayer.resume()
      return
    }

    playbackJob?.cancel()
    loadingAudioId?.takeIf { it != audio.id }?.let { id ->
      setPlaybackState(id, AudioPlaybackState.Idle(durationMs = _playbackStates.value[id]?.durationMs ?: 0L))
    }
    loadingAudioId = audio.id
    setPlaybackState(audio.id, AudioPlaybackState.Loading(durationMs = audio.durationMs))
    playbackJob = viewModelScope.launch {
      try {
        val file = mediaRepository.getFile(circle, audio)
        activeAudioId?.takeIf { it != audio.id }?.let { id ->
          setPlaybackState(id, AudioPlaybackState.Idle(durationMs = _playbackStates.value[id]?.durationMs ?: 0L))
        }
        activeAudioId = audio.id
        activeFile = file
        audioPlayer.play(file)
        loadingAudioId = null
        updatePlaybackState(audioPlayer.state.value)
      } catch (error: IOException) {
        Log.e(TAG, "Error playing audio", error)
        loadingAudioId = null
        setPlaybackState(audio.id, AudioPlaybackState.Error(durationMs = audio.durationMs))
      }
    }
  }

  private fun setPlaybackState(id: String, state: AudioPlaybackState) {
      _playbackStates.value += (id to state)
  }

  private fun updatePlaybackState(playerState: AudioState) {
    val id = activeAudioId ?: return
    if (loadingAudioId == id || _playbackStates.value[id] is AudioPlaybackState.Error) return
    val durationMs = _playbackStates.value[id]?.durationMs ?: 0L
    val state =
        if (activeFile != null && playerState.file == activeFile) {
          if (playerState.isPlaying) {
            AudioPlaybackState.Playing(playerState.positionMs, playerState.durationMs)
          } else {
            AudioPlaybackState.Paused(playerState.positionMs, playerState.durationMs)
          }
        } else {
          AudioPlaybackState.Idle(durationMs = durationMs)
        }
    setPlaybackState(id, state)
  }

  fun onRestart(audio: MediaItem.Audio) {
    if (activeAudioId == audio.id && audioPlayer.state.value.file == activeFile) {
      audioPlayer.seekTo(0L)
    }
  }

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
          .catch { error ->
            Log.e(TAG, "Error observing media", error)
            setNonBlockingErrorMsg(R.string.seeAudiosErrorLoadMedia)
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
    super.onCleared()
  }

  private companion object {
    const val TAG = "SeeAudiosViewModel"
  }
}
