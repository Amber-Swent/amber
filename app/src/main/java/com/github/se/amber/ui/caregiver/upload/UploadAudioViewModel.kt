/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload

import androidx.lifecycle.ViewModel
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UploadAudioUIState(
    val title: String = "",
    val description: String = "",
    val date: String = "",
    val involvedName: String = "",
    val audio: MediaItem.Audio =
        MediaItem.Audio(
            id = "",
            authorId = "",
            storagePath = "",
            createdAt = 0L,
            date = null,
            location = null,
            shownIds = emptyList(),
            authorizedIds = emptyList(),
            status = MediaStatus.APPROVED,
            durationMs = 0L,
            transcript = null,
        ),
    val audioState: AudioState = AudioState.UNRECORD,
    val errorMsg: String? = null,
)

// TODO: implement repository
class UploadAudioViewModel(
    // private val repository: MediaRepository = MediaRepositoryProvider.repository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(UploadAudioUIState())
  val uiState: StateFlow<UploadAudioUIState> = _uiState.asStateFlow()

  /** Clears the error message in the UI state. */
  fun clearErrorMsg() {
    _uiState.value = _uiState.value.copy(errorMsg = null)
  }

  /** Sets an error message in the UI state. */
  private fun setErrorMsg(errorMsg: String) {
    _uiState.value = _uiState.value.copy(errorMsg = errorMsg)
  }

  /* Must convert and check format date */
  fun uploadAudio() {
      TODO("to be implement")
  }

  fun startRecordAudio() {
      TODO("to be implement")
  }

  fun deleteAudio() {
      TODO("to be implement")
  }

  /* Must actualize uiState */
  fun stopRecordAudio() {
      TODO("to be implement")
  }

  fun setTitle(title: String) {
    // Update _uiState accordingly
    _uiState.value = _uiState.value.copy(title = title)
  }

  fun setDescription(description: String) {
    _uiState.value = _uiState.value.copy(description = description)
  }

  fun setDate(date: String) {
    _uiState.value = _uiState.value.copy(date = date)
  }

  fun setInvolvedName(involvedName: String) {
    _uiState.value = _uiState.value.copy(involvedName = involvedName)
  }
}
