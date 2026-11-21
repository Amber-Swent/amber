// Written by MeiliV, with assistance from OpenAI Codex.
// Modified by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.patient.seeMedias.audios

import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.media.audio.AudioPlayer
import com.github.se.amber.data.media.audio.AudioPlayerProvider
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MediaRepositoryProvider
import com.github.se.amber.ui.media.audio.AudioPlaybackState
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMediasErrors
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMediasUIState
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMediasViewModel
import kotlinx.coroutines.flow.StateFlow

/** Error message resources used by the audio ViewModel, passed to [SeeGeneralMediasViewModel]. */
private val audiosErrors =
    SeeGeneralMediasErrors(
        loadProfile = R.string.seeAudiosErrorLoadProfile,
        noProfile = R.string.seeAudiosErrorNoProfile,
        noCircle = R.string.seeAudiosErrorNoCircle,
        loadCircle = R.string.seeAudiosErrorLoadCircle,
        loadMedia = R.string.seeAudiosErrorLoadMedia,
    )

/**
 * Loads the patient's approved audio memories (loading logic in [SeeGeneralMediasViewModel]). The
 * playback of each audio card is handled by an [AudioPlaybackController], without changing the list
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
    audioPlayer: AudioPlayer = AudioPlayerProvider.player,
) :
    SeeGeneralMediasViewModel<MediaItem.Audio>(
        mediaRepository = mediaRepository,
        circleRepository = circleRepository,
        profileRepository = profileRepository,
        errors = audiosErrors,
    ) {

  // Declared before the init block: the load started there may call onItemsChanged
  private val playback =
      AudioPlaybackController(audioPlayer, mediaRepository, viewModelScope) {
        (uiState.value as? SeeGeneralMediasUIState.Content<*>)?.careCircle
      }

  /** Playback state of each audio card, by media ID. */
  val playbackStates: StateFlow<Map<String, AudioPlaybackState>> = playback.playbackStates

  init {
    load()
  }

  override fun filterMedia(media: List<MediaItem>): List<MediaItem.Audio> =
      media.filterIsInstance<MediaItem.Audio>()

  /** Stops the playback if the active audio is no longer in the list. */
  override fun onItemsChanged(items: List<MediaItem.Audio>) = playback.onItemsChanged(items)

  /**
   * Downloads [audio] on first play, then pauses or resumes it.
   *
   * @param audio the memory whose playback button was pressed.
   */
  fun onPlayPause(audio: MediaItem.Audio) = playback.onPlayPause(audio)

  /**
   * Seeks the loaded [audio] to the start.
   *
   * @param audio the memory whose restart button was pressed.
   */
  fun onRestart(audio: MediaItem.Audio) = playback.onRestart(audio)

  override fun onCleared() {
    playback.release()
    super.onCleared() // NOSONAR: the parent may add cleanup here later
  }
}
