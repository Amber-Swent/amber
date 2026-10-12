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

/**
 * Represents the state displayed by the picture upload screen.
 *
 * @param title The title entered for the picture.
 * @param description The description entered for the picture.
 * @param date The date associated with the picture.
 * @param involvedName The name of the person involved in the picture.
 * @param picture The picture selected for upload.
 * @param errorMsg The error message to display, or `null` when there is no error.
 */
data class UploadPictureUIState(
    val title: String = "",
    val description: String = "",
    // TODO : will need to be adapt according to the Repo
    val date: String = "",
    val involvedName: String = "", // is the String version of Person, must be Map to shownIds
    val picture: MediaItem.Picture =
        MediaItem.Picture(
            id = "",
            authorId = "",
            storagePath = "",
            createdAt = 0L,
            description = description,
            date = null,
            location = null,
            shownIds = emptyList(),
            authorizedIds = emptyList(),
            status = MediaStatus.APPROVED,
            width = 0,
            height = 0,
        ),
    val errorMsg: String? = null,
)

/** Manages the state and user actions for the picture upload screen. */
// TODO: use Repo once available
class UploadPictureViewModel(
    // private val repository: MediaRepository = MediaRepositoryProvider.repository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(UploadPictureUIState())
  val uiState: StateFlow<UploadPictureUIState> = _uiState.asStateFlow()

  /** Clears the error message in the UI state. */
  fun clearErrorMsg() {
    _uiState.value = _uiState.value.copy(errorMsg = null)
  }

  /**
   * Stores an error message in the UI state.
   *
   * @param errorMsg The error message to store.
   */
  private fun setErrorMsg(errorMsg: String) {
    _uiState.value = _uiState.value.copy(errorMsg = errorMsg)
  }

  /** Uploads the picture currently stored in the UI state. */
  fun uploadPicture() {
    // TODO: to implement
  }

  /** Starts the process of taking a new picture. */
  fun takePicture() {
    // TODO: to implement
  }

  /** Starts the process of selecting an existing picture. */
  fun selectPicture() {
    // TODO: to implement
  }

  /**
   * Updates the title in the UI state.
   *
   * @param title The new title.
   */
  fun setTitle(title: String) {
    // Update _uiState accordingly
    _uiState.value = _uiState.value.copy(title = title)
  }

  /**
   * Updates the description in the UI state.
   *
   * @param description The new description.
   */
  fun setDescription(description: String) {
    _uiState.value = _uiState.value.copy(description = description)
  }

  /**
   * Updates the date in the UI state.
   *
   * @param date The new date.
   */
  fun setDate(date: String) {
    _uiState.value = _uiState.value.copy(date = date)
  }

  /**
   * Updates the involved person's name in the UI state.
   *
   * @param involvedName The new involved person's name.
   */
  fun setInvolvedName(involvedName: String) {
    _uiState.value = _uiState.value.copy(involvedName = involvedName)
  }

  /**
   * Updates the picture in the UI state.
   *
   * @param picture The new picture.
   */
  fun setStory(picture: MediaItem.Picture) {
    _uiState.value = _uiState.value.copy(picture = picture)
  }
}
