// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Lou-Anne Maier, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.caregiver.upload.audios

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.location.PlaceSearchRepository
import com.github.se.amber.data.location.PlaceSearchRepositoryProvider
import com.github.se.amber.data.media.audio.AudioPlayer
import com.github.se.amber.data.media.audio.AudioPlayerProvider
import com.github.se.amber.data.media.audio.AudioRecorder
import com.github.se.amber.data.media.audio.AudioRecorderProvider
import com.github.se.amber.data.media.audio.WAVEFORM_BARS
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.AudioRecordingState
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MediaRepositoryProvider
import com.github.se.amber.model.media.MediaStatus
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.ui.caregiver.upload.media.CommonMediaUIState
import com.github.se.amber.ui.caregiver.upload.media.MediaUIState
import com.github.se.amber.ui.caregiver.upload.media.MediaViewModel
import com.github.se.amber.ui.media.audio.playbackFraction
import com.github.se.amber.ui.theme.NO_PLAYED_FRACTION
import java.io.File
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Error messages specific to the caregiver Upload Audio ViewModel; the ones shared with the other
 * upload screens are in [com.github.se.amber.ui.caregiver.upload.media.MediaViewModelErrorMessage].
 *
 * @property messageRes the message shown to the user, as a string resource.
 */
enum class UploadAudioViewModelErrorMessage(@param:StringRes val messageRes: Int) {
  RECORD_AUDIO(R.string.uploadAudioErrorRecord),
  MICROPHONE_NOT_ALLOWED(R.string.uploadAudioErrorMicrophoneNotAllowed),
  COULD_NOT_START_RECORDING(R.string.uploadAudioErrorStartRecording),
  COULD_NOT_STOP_RECORDING(R.string.uploadAudioErrorStopRecording),
  COULD_NOT_PLAY_RECORDING(R.string.uploadAudioErrorPlayRecording),
}

/**
 * Represents the state displayed by the audio upload screen.
 *
 * @param common The fields shared with the other upload screens: description, date, place, people,
 *   upload progress and error message.
 * @param audioFile The recorded audio, once the recording is over.
 * @param durationMs How long the recorded audio lasts, 0 until the recording is over.
 * @param waveform The levels of the audio so far (while recording) or in full (once recorded).
 * @param recordingState Whether the audio is not yet recorded, being recorded, recorded, or being
 *   listened to.
 * @param playedFraction How much of the audio has been heard while listening, from 0 to 1.
 * @param recordingErrorRes The text shown under the recording, as a string resource, e.g. when the
 *   recorder stopped at its maximum length; null when there is nothing to tell.
 */
data class UploadAudioUIState(
    override val common: CommonMediaUIState = CommonMediaUIState(),
    val audioFile: File? = null,
    val durationMs: Long = 0L,
    val waveform: List<Float> = emptyList(),
    val recordingState: AudioRecordingState = AudioRecordingState.IDLE,
    val playedFraction: Float = NO_PLAYED_FRACTION,
    @param:StringRes val recordingErrorRes: Int? = null,
) : MediaUIState<UploadAudioUIState> {
  override val canUpload: Boolean
    get() = audioFile != null && recordingState == AudioRecordingState.RECORDED && common.canUpload

  override fun withCommon(common: CommonMediaUIState): UploadAudioUIState = copy(common = common)
}

/**
 * Manages the state and user actions for the audio upload screen. What it shares with the other
 * upload screens (circle, description, date, place, people, upload) is in [MediaViewModel]; this
 * class only adds the recording.
 *
 * @param mediaRepo The repository used to create and upload media items.
 * @param authRepo The repository used to access the currently authenticated user.
 * @param profileRepo The repository used to load the authenticated user's profile.
 * @param circleRepo The repository used to load the user's care circle.
 * @param placeRepo The repository used to find real places from what the user types.
 * @param recorder The microphone recorder.
 * @param player The app's audio player, used to listen to the recording before uploading it.
 */
class UploadAudioViewModel(
    mediaRepo: MediaRepository = MediaRepositoryProvider.repository,
    authRepo: AuthRepository = AuthRepositoryProvider.repository,
    profileRepo: ProfileRepository = ProfileRepositoryProvider.default.repository,
    circleRepo: CareCircleRepository = CareCircleRepositoryProvider.default.repository,
    placeRepo: PlaceSearchRepository = PlaceSearchRepositoryProvider.default.repository,
    cleanupScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val recorder: AudioRecorder = AudioRecorderProvider.recorder,
    private val player: AudioPlayer = AudioPlayerProvider.player,
) :
    MediaViewModel<UploadAudioUIState>(
        initialState =
            UploadAudioUIState(
                common = CommonMediaUIState(date = MemoryDate(LocalDate.now().year))
            ),
        mediaRepo = mediaRepo,
        authRepo = authRepo,
        profileRepo = profileRepo,
        circleRepo = circleRepo,
        placeRepo = placeRepo,
        cleanupScope = cleanupScope,
    ) {

  // A Job is a handle on a coroutine (a task running in the background, started with `launch`).
  // Keeping it lets us check whether the task is still running (`isActive`) and cancel it
  // e.g. when the user deletes the recording.

  /**
   * The task started by [startRecording]: it starts the recorder, then waits for the recorder to
   * stop by itself (time or size limit) and hands over to [saveRecordingOnce]. Canceled when the
   * user stops or deletes the recording.
   */
  private var recordingJob: Job? = null

  /**
   * The task that adds each microphone level to the waveform while recording. Canceled as soon as
   * the recording ends, so the waveform no longer grows.
   */
  private var amplitudeJob: Job? = null

  /**
   * The one task that takes the finished recording from the recorder, started by [stopRecording] or
   * when the recorder stops at a limit. Kept so that only one runs: a second `recorder.stop()`
   * would find no recording, and canceling the first could delete its file. Also canceled if the
   * user deletes.
   */
  private var stoppingJob: Job? = null

  /** The task started by [playRecording]. Canceled if the user plays again or deletes. */
  private var playingJob: Job? = null

  /**
   * Checks whether an audio has been recorded for upload.
   *
   * @param state The current audio upload UI state.
   * @return The error to show if no audio is recorded, as a string resource, or null if it is.
   */
  @StringRes
  override fun checkContent(state: UploadAudioUIState): Int? =
      if (state.audioFile == null || state.recordingState != AudioRecordingState.RECORDED)
          UploadAudioViewModelErrorMessage.RECORD_AUDIO.messageRes
      else null

  /**
   * Gets the recorded audio file.
   *
   * @param state The current audio upload UI state.
   * @return The recorded file, or null if nothing is recorded.
   */
  override fun fileOf(state: UploadAudioUIState): File? = state.audioFile

  /**
   * Creates a media item containing the recorded audio and its upload metadata.
   *
   * @param id The unique identifier assigned to the media item.
   * @param state The current audio upload UI state.
   * @param authorId The identifier of the user who uploaded the audio.
   * @param circle The care circle associated with the audio.
   * @param date The date associated with the audio.
   * @param shownIds The identifiers of people shown in the audio.
   * @return The audio media item to save and upload.
   */
  override fun createItem(
      id: String,
      state: UploadAudioUIState,
      authorId: String,
      circle: CareCircle,
      date: MemoryDate,
      shownIds: List<String>,
  ): MediaItem =
      MediaItem.Audio(
          id = id,
          authorId = authorId,
          storagePath = "careCircles/${circle.id}/media/$id.m4a",
          createdAt = System.currentTimeMillis(),
          description = state.common.description,
          date = date,
          location = state.common.location.pickedPlace,
          shownIds = shownIds,
          authorizedIds = emptyList(),
          status = MediaStatus.APPROVED,
          durationMs = state.durationMs,
          waveform = state.waveform,
      )

  /** Starts recording; [UploadAudioUIState.waveform] then grows with the microphone level. */
  fun startRecording() {
    if (_uiState.value.recordingState != AudioRecordingState.IDLE) return
    // The state stays IDLE while the recorder starts: a second tap then would cancel this start and
    // begin another one
    if (recordingJob?.isActive == true) return
    recordingJob = viewModelScope.launch {
      try {
        recorder.start()
      } catch (e: CancellationException) {
        throw e
      } catch (e: SecurityException) {
        setErrorMsg(UploadAudioViewModelErrorMessage.MICROPHONE_NOT_ALLOWED.messageRes)
        return@launch
      } catch (e: Exception) {
        Log.e(TAG, "Could not start the recording", e)
        setErrorMsg(UploadAudioViewModelErrorMessage.COULD_NOT_START_RECORDING.messageRes)
        return@launch
      }
      _uiState.update {
        it.copy(
            recordingState = AudioRecordingState.RECORDING,
            waveform = emptyList(),
            recordingErrorRes = null,
        )
      }
      // The level is read on a timer, not collected: amplitude is a StateFlow, which doesn't emit
      // twice the same level, so the waveform would freeze during a silence
      amplitudeJob = launch {
        while (true) {
          delay(AMPLITUDE_SAMPLE_MS.milliseconds)
          val level = recorder.amplitude.value
          // the screen draws WAVEFORM_BARS bars at most: keep the latest ones, or the drawing
          // would freeze after the first few seconds
          _uiState.update { it.copy(waveform = (it.waveform + level).takeLast(WAVEFORM_BARS)) }
        }
      }
      // the recorder stops by itself at its limits: keep what was recorded
      recorder.isRecording.first { !it }
      amplitudeJob?.cancel()
      saveRecordingOnce(reachedLimit = true)
    }
  }

  /** Stops the recording and keeps it: [UploadAudioUIState.waveform] then holds the full audio. */
  fun stopRecording() {
    if (_uiState.value.recordingState != AudioRecordingState.RECORDING) return
    recordingJob?.cancel()
    amplitudeJob?.cancel()
    saveRecordingOnce()
  }

  /**
   * Starts saving the recording, unless it is already being saved, whether the recorder stopped at
   * a limit or the user tapped stop meanwhile. It runs in its own task, so stopping the
   * [recordingJob] doesn't cancel it.
   *
   * @param reachedLimit Whether the recorder stopped by itself at its time or size limit, so the
   *   user is told why the recording ended.
   */
  private fun saveRecordingOnce(reachedLimit: Boolean = false) {
    if (stoppingJob?.isActive == true) return
    stoppingJob = viewModelScope.launch { saveRecording(reachedLimit) }
  }

  /**
   * Takes the finished recording from the recorder and puts it in the state.
   *
   * @param reachedLimit Whether the recorder stopped by itself at a limit; the text under the
   *   recording then tells the user once the recording is kept.
   */
  private suspend fun saveRecording(reachedLimit: Boolean = false) {
    try {
      val audio = recorder.stop()
      _uiState.update {
        it.copy(
            audioFile = audio.file,
            durationMs = audio.durationMs,
            waveform = audio.waveform,
            recordingState = AudioRecordingState.RECORDED,
            recordingErrorRes = if (true) R.string.uploadAudioErrorMaxLength else null,
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Could not keep the recording", e)
      resetRecording()
      setErrorMsg(UploadAudioViewModelErrorMessage.COULD_NOT_STOP_RECORDING.messageRes)
    }
  }

  /**
   * Stops listening to the recorded audio: the state goes from LISTENING back to RECORDED, and the
   * next play starts again from the beginning. It does nothing if the audio is not being listened
   * to.
   */
  fun stopListening() {
    val state = _uiState.value
    if (state.recordingState != AudioRecordingState.LISTENING) return
    playingJob?.cancel()
    state.audioFile?.let { player.stop(it) }
    endListening()
  }

  /** Goes back from LISTENING to RECORDED, with nothing heard yet. */
  private fun endListening() {
    _uiState.update {
      if (it.recordingState != AudioRecordingState.LISTENING) it
      else
          it.copy(
              recordingState = AudioRecordingState.RECORDED,
              playedFraction = NO_PLAYED_FRACTION,
          )
    }
  }

  /** Throws away the recording in progress, or the one that was recorded. */
  fun deleteRecording() {
    discardRecording()?.let(::deleteOrWarn)
  }

  /** Cancel throws away the recording, in progress or recorded. */
  override fun discardContent(): File? = discardRecording()

  /**
   * Stops everything that uses the recording and clears it from the state.
   *
   * @return The recorded file, not deleted yet, or null if there was none.
   */
  private fun discardRecording(): File? {
    recordingJob?.cancel()
    amplitudeJob?.cancel()
    stoppingJob?.cancel()
    playingJob?.cancel()
    recorder.cancel()
    val file = _uiState.value.audioFile
    file?.let { player.stop(it) }
    resetRecording()
    return file
  }

  private fun resetRecording() {
    _uiState.update {
      it.copy(
          audioFile = null,
          durationMs = 0L,
          waveform = emptyList(),
          recordingState = AudioRecordingState.IDLE,
          playedFraction = NO_PLAYED_FRACTION,
          recordingErrorRes = null,
      )
    }
  }

  /**
   * Plays the recorded audio from the start: the state goes from RECORDED to LISTENING, and
   * [UploadAudioUIState.playedFraction] follows the player. When the audio ends, or is stopped or
   * replaced in the app's single player, the state goes back to RECORDED. It does nothing until the
   * audio is recorded.
   */
  fun playRecording() {
    val state = _uiState.value
    val file = state.audioFile ?: return
    if (state.recordingState != AudioRecordingState.RECORDED) return
    playingJob?.cancel()
    playingJob = viewModelScope.launch {
      try {
        player.play(file)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Could not play the recording", e)
        setErrorMsg(UploadAudioViewModelErrorMessage.COULD_NOT_PLAY_RECORDING.messageRes)
        return@launch
      }
      _uiState.update {
        it.copy(recordingState = AudioRecordingState.LISTENING, playedFraction = NO_PLAYED_FRACTION)
      }
      // follow the player until the audio ends, or is stopped or replaced by another one
      player.state
          .takeWhile { it.file == file && it.isPlaying }
          .collect { heard ->
            _uiState.update {
              it.copy(playedFraction = playbackFraction(heard.positionMs, heard.durationMs))
            }
          }
      endListening()
    }
  }

  override fun onCleared() {
    recorder.cancel()
    val state = _uiState.value
    val file = state.audioFile
    file?.let { player.stop(it) }
    // A recording that was never uploaded would stay on the phone until the next recording. An
    // uploaded one was moved into the outbox: nothing is left at this path, so deleting does
    // nothing. An upload still running is left alone: the outbox may be moving the file right now.
    if (!state.common.isUploading) file?.let(::deleteOrWarn)
  }

  private companion object {
    const val TAG = "UploadAudioViewModel"

    /** How often the microphone level is read: the period the recorder updates it at. */
    const val AMPLITUDE_SAMPLE_MS = 100L
  }
}
