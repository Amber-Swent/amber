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
import com.github.se.amber.ui.caregiver.upload.pictures.PictureLocationUIState
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
    val location: PictureLocationUIState = PictureLocationUIState(),
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
 * @param pictureReader The reader used to copy selected pictures and read their dimensions.
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
  val involvedOptions: StateFlow<Map<String, String>> = MutableStateFlow(emptyMap<String, String>())

  fun clearErrorMsg() {}

  internal fun setErrorMsg(errorMsg: String) {}

  fun setDescription(description: String) {}

  fun setDate(date: MemoryDate?) {}

  fun toggleShownId(id: String) {}

  fun onAddPerson() {}

  fun setPersonFirstName(firstName: String) {}

  fun setPersonLastName(lastName: String) {}

  fun setPersonNickname(nickName: String) {}

  fun dismissAddPerson() {}

  fun addPerson() {}

  /** Starts recording; [UploadAudioUIState.waveform] then grows with the microphone level. */
  fun startRecording() {}

  /** Stops the recording and keeps it: [UploadAudioUIState.waveform] then holds the full audio. */
  fun stopRecording() {}

  /** Throws away the recording in progress, or the one that was recorded. */
  fun deleteRecording() {}

  fun uploadAudio() {}
}
