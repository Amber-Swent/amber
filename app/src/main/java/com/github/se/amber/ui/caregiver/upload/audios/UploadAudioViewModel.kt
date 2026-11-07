// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.caregiver.upload.audios

import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.location.PlaceSearchRepository
import com.github.se.amber.data.location.PlaceSearchRepositoryProvider
import com.github.se.amber.data.media.audio.AudioRecorder
import com.github.se.amber.data.media.audio.AudioRecorderProvider
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
import java.io.File
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Error messages specific to the caregiver Upload Audio ViewModel; the ones shared with the other
 * upload screens are in [com.github.se.amber.ui.caregiver.upload.media.MediaViewModelErrorMessage].
 */
enum class UploadAudioViewModelErrorMessage(val mess: String) {
  RECORD_AUDIO(mess = "No audio recorded"),
  MICROPHONE_NOT_ALLOWED(mess = "The use of the microphone isn't permitted"),
  COULD_NOT_START_RECORDING(mess = "Could not start the recording: "),
  COULD_NOT_STOP_RECORDING(mess = "Could not keep the recording: "),
}

/**
 * Represents the state displayed by the audio upload screen.
 *
 * @param common The fields shared with the other upload screens: description, date, place, people,
 *   upload progress and error message.
 * @param audioFile The recorded audio, once the recording is over.
 * @param durationMs How long the recorded audio lasts, 0 until the recording is over.
 * @param waveform The levels of the audio so far (while recording) or in full (once recorded).
 * @param recordingState Whether the audio is not yet recorded, being recorded, or recorded.
 */
data class UploadAudioUIState(
    override val common: CommonMediaUIState = CommonMediaUIState(),
    val audioFile: File? = null,
    val durationMs: Long = 0L,
    val waveform: List<Float> = emptyList(),
    val recordingState: AudioRecordingState = AudioRecordingState.IDLE,
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
 */
class UploadAudioViewModel(
    mediaRepo: MediaRepository = MediaRepositoryProvider.repository,
    authRepo: AuthRepository = AuthRepositoryProvider.repository,
    profileRepo: ProfileRepository = ProfileRepositoryProvider.default.repository,
    circleRepo: CareCircleRepository = CareCircleRepositoryProvider.default.repository,
    placeRepo: PlaceSearchRepository = PlaceSearchRepositoryProvider.default.repository,
    private val recorder: AudioRecorder = AudioRecorderProvider.recorder,
) :
    MediaViewModel<UploadAudioUIState>(
        initialState =
            UploadAudioUIState(common = CommonMediaUIState(date = MemoryDate(LocalDate.now().year))),
        mediaRepo = mediaRepo,
        authRepo = authRepo,
        profileRepo = profileRepo,
        circleRepo = circleRepo,
        placeRepo = placeRepo,
    ) {

  // A Job is a handle on a coroutine (a task running in the background, started with `launch`).
  // Keeping it lets us check whether the task is still running (`isActive`) and cancel it
  // e.g. when the user deletes the recording.

  /**
   * The task started by [startRecording]: it starts the recorder, then waits for the recorder to
   * stop by itself (time or size limit) to keep the recording. Canceled when the user stops or
   * deletes the recording, so it doesn't keep it a second time.
   */
  private var recordingJob: Job? = null

  /**
   * The task that adds each microphone level to the waveform while recording. Cancelled as soon as
   * the recording ends, so the waveform no longer grows.
   */
  private var amplitudeJob: Job? = null

  /**
   * The task started by [stopRecording] that takes the finished recording from the recorder. Kept
   * to ignore a second tap on stop while it still runs, and to cancel it if the user deletes.
   */
  private var stoppingJob: Job? = null

  /**
   * Checks whether an audio has been recorded for upload.
   *
   * @param state The current audio upload UI state.
   * @return The error message to show if no audio is recorded, or null if it is.
   */
  override fun checkContent(state: UploadAudioUIState): String? =
      if (state.audioFile == null || state.recordingState != AudioRecordingState.RECORDED)
          UploadAudioViewModelErrorMessage.RECORD_AUDIO.mess
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
    recordingJob?.cancel()
    recordingJob =
        viewModelScope.launch {
          try {
            recorder.start()
          } catch (e: CancellationException) {
            throw e
          } catch (e: SecurityException) {
            setErrorMsg(UploadAudioViewModelErrorMessage.MICROPHONE_NOT_ALLOWED.mess + (e.message ?: "") )
            return@launch
          } catch (e: Exception) {
            setErrorMsg(
                UploadAudioViewModelErrorMessage.COULD_NOT_START_RECORDING.mess + (e.message ?: "")
            )
            return@launch
          }
          _uiState.update {
            it.copy(recordingState = AudioRecordingState.RECORDING, waveform = emptyList())
          }
          amplitudeJob =
              recorder.amplitude
                  .onEach { level -> _uiState.update { it.copy(waveform = it.waveform + level) } }
                  .launchIn(this)
          // the recorder stops by itself at its limits: keep what was recorded
          recorder.isRecording.first { !it }
          amplitudeJob?.cancel()
          saveRecording()
        }
  }

  /** Stops the recording and keeps it: [UploadAudioUIState.waveform] then holds the full audio. */
  fun stopRecording() {
    if (_uiState.value.recordingState != AudioRecordingState.RECORDING) return
    if (stoppingJob?.isActive == true) return
    recordingJob?.cancel()
    amplitudeJob?.cancel()
    stoppingJob = viewModelScope.launch { saveRecording() }
  }

  /** Takes the finished recording from the recorder and puts it in the state. */
  private suspend fun saveRecording() {
    try {
      val audio = recorder.stop()
      _uiState.update {
        it.copy(
            audioFile = audio.file,
            durationMs = audio.durationMs,
            waveform = audio.waveform,
            recordingState = AudioRecordingState.RECORDED,
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      resetRecording()
      setErrorMsg(
          UploadAudioViewModelErrorMessage.COULD_NOT_STOP_RECORDING.mess + (e.message ?: "")
      )
    }
  }

  /** Throws away the recording in progress, or the one that was recorded. */
  fun deleteRecording() {
    recordingJob?.cancel()
    amplitudeJob?.cancel()
    stoppingJob?.cancel()
    recorder.cancel()
    _uiState.value.audioFile?.delete()
    resetRecording()
  }

  private fun resetRecording() {
    _uiState.update {
      it.copy(
          audioFile = null,
          durationMs = 0L,
          waveform = emptyList(),
          recordingState = AudioRecordingState.IDLE,
      )
    }
  }

  override fun onCleared() {
    recorder.cancel()
  }
}
