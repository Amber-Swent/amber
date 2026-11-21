// Written by MeiliV, with assistance from OpenAI Codex.
// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.patient.seeMedias.audios

import android.util.Log
import com.github.se.amber.data.media.audio.AudioPlayer
import com.github.se.amber.data.media.audio.AudioState
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.ui.media.audio.AudioPlaybackState
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Audio playback logic shared by every screen that displays audios. Downloads an audio on first
 * play, pauses or resumes it, and exposes the [AudioPlaybackState] of each audio card, without
 * touching the state of the list.
 *
 * Only one audio is active at a time: starting another one pauses the previous one.
 *
 * @param audioPlayer plays audio and reports playback progress.
 * @param mediaRepository downloads the audio files.
 * @param scope scope of the owner (the `viewModelScope` of the ViewModel), used for the download
 *   and for observing the player.
 * @param circleProvider the care circle currently displayed, or null if it is not loaded yet.
 *   Evaluated at each play, so the owner can read it from its own UI state.
 */
class AudioPlaybackController(
    private val audioPlayer: AudioPlayer,
    private val mediaRepository: MediaRepository,
    private val scope: CoroutineScope,
    private val circleProvider: () -> CareCircle?,
) {
  private val _playbackStates = MutableStateFlow<Map<String, AudioPlaybackState>>(emptyMap())

  /** Playback state of each audio, by media ID. An audio absent from the map is idle. */
  val playbackStates: StateFlow<Map<String, AudioPlaybackState>> = _playbackStates.asStateFlow()

  private var playbackJob: Job? = null
  private var activeAudioId: String? = null
  private var activeFile: File? = null
  private var loadingAudioId: String? = null

  init {
    scope.launch { audioPlayer.state.collect(::updatePlaybackState) }
  }

  /**
   * Stops the download or the playback of an audio that is no longer in the list.
   *
   * @param items the new list of audios displayed.
   */
  fun onItemsChanged(items: List<MediaItem.Audio>) {
    if (loadingAudioId != null && items.none { it.id == loadingAudioId }) {
      playbackJob?.cancel()
      loadingAudioId = null
    }
    if (activeAudioId != null && items.none { it.id == activeAudioId }) {
      playbackJob?.cancel()
      activeFile?.let(audioPlayer::stop)
    }
  }

  /**
   * Downloads [audio] on first play, then pauses or resumes its loaded file. Download and play
   * failures show an error on that audio card. Does nothing if the care circle is not loaded.
   *
   * @param audio the memory whose playback button was pressed.
   */
  fun onPlayPause(audio: MediaItem.Audio) {
    val circle = circleProvider() ?: return
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
    playbackJob = scope.launch {
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

  /**
   * Seeks the loaded [audio] to the start. Does nothing if this audio is not the loaded one.
   *
   * @param audio the memory whose restart button was pressed.
   */
  fun onRestart(audio: MediaItem.Audio) {
    if (activeAudioId == audio.id && audioPlayer.state.value.file == activeFile) {
      audioPlayer.seekTo(0L)
    }
  }

  /** Cancels the download and stops the player. To call from the owner's `onCleared`. */
  fun release() {
    playbackJob?.cancel()
    activeFile?.let(audioPlayer::stop)
  }

  /**
   * Updates the playback state of one audio card while preserving the states of other cards.
   *
   * @param id ID of the audio card to update.
   * @param state new playback state to expose for that card.
   */
  private fun setPlaybackState(id: String, state: AudioPlaybackState) {
    _playbackStates.value += (id to state)
  }

  /** Applies player progress only when its file matches the active audio. */
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

  private companion object {
    const val TAG = "AudioPlaybackController"
  }
}
