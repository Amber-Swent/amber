// Written by MeiliV, with assistance from OpenAI Codex.
// Modified by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.patient.seeMedias.audios

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.media.audio.AudioPlayer
import com.github.se.amber.data.media.audio.AudioPlayerProvider
import com.github.se.amber.data.media.audio.AudioState
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MediaRepositoryProvider
import com.github.se.amber.ui.media.audio.AudioPlaybackState
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMediasErrors
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMediasUIState
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMediasViewModel
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val AUDIOS_ERRORS =
    SeeGeneralMediasErrors(
        loadProfile = R.string.seeAudiosErrorLoadProfile,
        noProfile = R.string.seeAudiosErrorNoProfile,
        noCircle = R.string.seeAudiosErrorNoCircle,
        loadCircle = R.string.seeAudiosErrorLoadCircle,
        loadMedia = R.string.seeAudiosErrorLoadMedia,
    )

/**
 * Loads the patient's approved audio memories (loading logic in [SeeGeneralMediasViewModel]). Uses
 * the shared [AudioPlayer] and exposes the state of each audio card without changing the list
 * state.
 *
 * @param mediaRepository observes approved media and downloads audio files.
 * @param circleRepository loads the patient's care circle.
 * @param profileRepository loads the current patient profile.
 * @param audioPlayer plays audio and reports playback progress.
 */
class SeeAudiosViewModel(
    mediaRepository: MediaRepository = MediaRepositoryProvider.repository,
    circleRepository: CareCircleRepository = CareCircleRepositoryProvider.default.repository,
    profileRepository: ProfileRepository = ProfileRepositoryProvider.default.repository,
    private val audioPlayer: AudioPlayer = AudioPlayerProvider.player,
) :
    SeeGeneralMediasViewModel<MediaItem.Audio>(
        mediaRepository = mediaRepository,
        circleRepository = circleRepository,
        profileRepository = profileRepository,
        errors = AUDIOS_ERRORS,
    ) {
  private val _playbackStates = MutableStateFlow<Map<String, AudioPlaybackState>>(emptyMap())
  val playbackStates: StateFlow<Map<String, AudioPlaybackState>> = _playbackStates.asStateFlow()

  private var playbackJob: Job? = null
  private var activeAudioId: String? = null
  private var activeFile: File? = null
  private var loadingAudioId: String? = null

  init {
    viewModelScope.launch { audioPlayer.state.collect(::updatePlaybackState) }
    load()
  }

  override fun filterMedia(media: List<MediaItem>): List<MediaItem.Audio> =
      media.filterIsInstance<MediaItem.Audio>()

  /** Stops the playback if the active audio is no longer in the list. */
  override fun onItemsChanged(items: List<MediaItem.Audio>) {
    if (activeAudioId != null && items.none { it.id == activeAudioId }) {
      playbackJob?.cancel()
      activeFile?.let(audioPlayer::stop)
    }
  }

  /**
   * Downloads [audio] on first play, then pauses or resumes its loaded file. Download and play
   * failures show an error on that audio card.
   *
   * @param audio the memory whose playback button was pressed.
   */
  fun onPlayPause(audio: MediaItem.Audio) {
    val circle = (uiState.value as? SeeGeneralMediasUIState.Content<*>)?.careCircle ?: return
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
          // Idle if the current file is not the file for the active audio.
          // Only setting the state to Idle without the above "else if" was hiding player failures
          // that would clear the current file to null, therefore entering this else condition and
          // setting the state to Idle instead of showing an error.
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

  override fun onCleared() {
    playbackJob?.cancel()
    activeFile?.let(audioPlayer::stop)
    super.onCleared() // NOSONAR: the parent may add cleanup here later
  }

  private companion object {
    const val TAG = "SeeAudiosViewModel"
  }
}
