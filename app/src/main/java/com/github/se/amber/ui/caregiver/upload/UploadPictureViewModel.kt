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

data class UploadPictureUIState(
    val title: String = "",
    val description: String = "",
    val date: String = "",
    val involvedName: String = "",
    val picture: MediaItem.Picture =
        MediaItem.Picture(
            id = "",
            authorId = "",
            storagePath = "",
            createdAt = 0L,
            description = "",
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

// data class Picture(
//  override val id: String = "",
//  override val authorId: String = "",
//  override val storagePath: String = "",
//  override val createdAt: Long = 0L,
//  override val description: String = "",
//  override val date: MemoryDate? = null,
//  override val location: GeoLocation? = null,
//  override val shownIds: List<String> = emptyList(),
//  override val authorizedIds: List<String> = emptyList(),
//  override val status: MediaStatus = MediaStatus.APPROVED,
//  val width: Int = 0, // pixels, of the stored file; lets the UI reserve space before loading
//  val height: Int = 0,
// ) : MediaItem

// TODO: implement repository
class UploadPictureViewModel(
    // private val repository: MediaRepository = MediaRepositoryProvider.repository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(UploadPictureUIState())
  val uiState: StateFlow<UploadPictureUIState> = _uiState.asStateFlow()

  /** Clears the error message in the UI state. */
  fun clearErrorMsg() {
    _uiState.value = _uiState.value.copy(errorMsg = null)
  }

  /** Sets an error message in the UI state. */
  private fun setErrorMsg(errorMsg: String) {
    _uiState.value = _uiState.value.copy(errorMsg = errorMsg)
  }

  fun uploadPicture() {
    // TODO: to implement
  }

  fun takePicture() {
    // TODO: to implement
  }

  fun selectPicture() {
    // TODO: to implement
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

  fun setStory(picture: MediaItem.Picture) {
    _uiState.value = _uiState.value.copy(picture = picture)
  }
}
