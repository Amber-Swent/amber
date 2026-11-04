/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload.audios

import androidx.lifecycle.ViewModel
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.location.PlaceSearchRepository
import com.github.se.amber.data.location.PlaceSearchRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.AudioRecordingState
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MediaRepositoryProvider
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.model.user.Person
import com.github.se.amber.ui.caregiver.upload.media.MediaLocationUIState
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents the state displayed by the picture upload screen.
 *
 * @param description The description entered for the picture.
 * @param date The date associated with the picture, if one was selected.
 * @param location Where the picture was taken: the place search and the picked place.
 * @param shownIds The IDs of the people tagged in the picture.
 * @param audioFile The recorded audio, once the recording is over.
 * @param waveform The levels of the audio so far (while recording) or in full (once recorded).
 * @param recordingState Whether the audio is not yet recorded, being recorded, or recorded.
 * @param isUploading Whether an upload is currently in progress.
 * @param errorMsg The error message to display, if any.
 */
data class UploadAudioUIState(
    val description: String = "",
    val date: MemoryDate? = null,
    val location: MediaLocationUIState = MediaLocationUIState(),
    val shownIds: Set<String> = emptySet(),
    val audioFile: File? = null,
    val waveform: List<Float> = emptyList(),
    val isUploading: Boolean = false,
    val recordingState: AudioRecordingState = AudioRecordingState.IDLE,
    val errorMsg: String? = null,
    val isAddPersonOpen: Boolean = false,
    val isAddingPerson: Boolean = false,
    val personFirstName: String = "",
    val personLastName: String = "",
    val personNickname: String = "",
) {
  val canUpload: Boolean
    get() =
        audioFile != null &&
            recordingState == AudioRecordingState.RECORDED &&
            description.isNotBlank() &&
            date != null &&
            location.hasValidPlace &&
            !isUploading
}

/**
 * Manages the state and user actions for the picture upload screen.
 *
 * @param mediaRepo The repository used to create and upload media items.
 * @param authRepo The repository used to access the currently authenticated user.
 * @param profileRepo The repository used to load the authenticated user's profile.
 * @param circleRepo The repository used to load the user's care circle.
 * @param placeRepo The repository used to find real places from what the user types.
 */
class UploadAudioViewModel(
    private val mediaRepo: MediaRepository = MediaRepositoryProvider.repository,
    private val authRepo: AuthRepository = AuthRepositoryProvider.repository,
    private val profileRepo: ProfileRepository = ProfileRepositoryProvider.default.repository,
    private val circleRepo: CareCircleRepository = CareCircleRepositoryProvider.default.repository,
    private val placeRepo: PlaceSearchRepository = PlaceSearchRepositoryProvider.default.repository,
) : ViewModel() {
  private val _uiState =
      MutableStateFlow(UploadAudioUIState(date = MemoryDate(LocalDate.now().year)))
  val uiState: StateFlow<UploadAudioUIState> = _uiState.asStateFlow()
  private val _careCircle = MutableStateFlow<CareCircle?>(null)
  val careCircle: StateFlow<CareCircle?> = _careCircle.asStateFlow()

  private val _members = MutableStateFlow<Map<String, Person>?>(null)
  // people saved by the add-person dialog before the circle arrived, keyed by id
  val involvedOptions: StateFlow<Map<String, String>> = MutableStateFlow(emptyMap())

  /** Clears the current error message from the UI state. */
  fun clearErrorMsg() {
    TODO("Will be implemented once a global ViewModel is defined and can be extended.")
  }

  /**
   * Sets the error message shown by the audio upload screen.
   *
   * @param errorMsg The error message to display.
   */
  internal fun setErrorMsg(errorMsg: String) {
    TODO("Will be implemented once a global ViewModel is defined and can be extended.")
  }

  /**
   * Updates the audio description in the UI state.
   *
   * @param description The new audio description.
   */
  fun setDescription(description: String) {
    TODO("Will be implemented once a global ViewModel is defined and can be extended.")
  }

  /**
   * Updates the audio date in the UI state.
   *
   * @param date The date associated with the audio.
   */
  fun setDate(date: MemoryDate?) {
    TODO("Will be implemented once a global ViewModel is defined and can be extended.")
  }

  /**
   * Adds a person to the tagged people, or removes them if already tagged.
   *
   * @param id The ID of the person to select or deselect.
   */
  fun toggleShownId(id: String) {
    TODO("Will be implemented once a global ViewModel is defined and can be extended.")
  }

  /** Opens the add-person dialog. */
  fun onAddPerson() {
    TODO("Will be implemented once a global ViewModel is defined and can be extended.")
  }

  /**
   * Updates the first name typed in the add-person dialog.
   *
   * @param firstName The new first name.
   */
  fun setPersonFirstName(firstName: String) {
    TODO("Will be implemented once a global ViewModel is defined and can be extended.")
  }

  /**
   * Updates the last name typed in the add-person dialog.
   *
   * @param lastName The new last name.
   */
  fun setPersonLastName(lastName: String) {
    TODO("Will be implemented once a global ViewModel is defined and can be extended.")
  }

  /**
   * Updates the nickname typed in the add-person dialog.
   *
   * @param nickName The new nickname.
   */
  fun setPersonNickname(nickName: String) {
    TODO("Will be implemented once a global ViewModel is defined and can be extended.")
  }

  /** Closes the add-person dialog and clears its fields, unless a person is being saved. */
  fun dismissAddPerson() {
    TODO("Will be implemented once a global ViewModel is defined and can be extended.")
  }

  fun addPerson() {
    TODO("Will be implemented once a global ViewModel is defined and can be extended.")
  }

  /** Starts recording; [UploadAudioUIState.waveform] then grows with the microphone level. */
  fun startRecording() {
    TODO("Will be implemented once a global ViewModel is defined and can be extended.")
  }

  /** Stops the recording and keeps it: [UploadAudioUIState.waveform] then holds the full audio. */
  fun stopRecording() {
    TODO("Will be implemented once a global ViewModel is defined and can be extended.")
  }

  /** Throws away the recording in progress, or the one that was recorded. */
  fun deleteRecording() {
    TODO("Will be implemented once a global ViewModel is defined and can be extended.")
  }

  fun uploadAudio() {
    TODO("Will be implemented once a global ViewModel is defined and can be extended.")
  }
}
