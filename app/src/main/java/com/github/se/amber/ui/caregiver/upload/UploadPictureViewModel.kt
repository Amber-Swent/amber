/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MediaRepositoryProvider
import com.github.se.amber.model.media.MediaStatus
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.model.user.Person
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Represents the state displayed by the picture upload screen.
 *
 * @param description The description entered for the picture.
 * @param date The date associated with the picture, if one was selected.
 * @param shownIds The IDs of the people tagged in the picture.
 * @param pictureFile The local picture file selected for upload.
 * @param pictureWidth The width of the selected picture in pixels.
 * @param pictureHeight The height of the selected picture in pixels.
 * @param isUploading Whether an upload is currently in progress.
 * @param isUploaded Whether the picture was uploaded successfully.
 * @param errorMsg The error message to display, if any.
 */
data class UploadPictureUIState(
    val description: String = "",
    val date: MemoryDate? = null,
    val shownIds: Set<String> = emptySet(),
    val pictureFile: File? = null,
    val pictureWidth: Int = 0,
    val pictureHeight: Int = 0,
    val isUploading: Boolean = false,
    val isUploaded: Boolean = false,
    val errorMsg: String? = null,
)

/**
 * Manages the state and user actions for the picture upload screen.
 *
 * @param mediaRepo The repository used to create and upload media items.
 * @param authRepo The repository used to access the currently authenticated user.
 */
class UploadPictureViewModel(
    private val mediaRepo: MediaRepository = MediaRepositoryProvider.repository,
    private val authRepo: AuthRepository = AuthRepositoryProvider.repository,
    private val profileRepo: ProfileRepository = ProfileRepositoryProvider.default.repository,
    private val circleRepo: CareCircleRepository = CareCircleRepositoryProvider.default.repository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(UploadPictureUIState())
  val uiState: StateFlow<UploadPictureUIState> = _uiState.asStateFlow()

  private val _careCircle = MutableStateFlow<CareCircle?>(null)
  val careCircle: StateFlow<CareCircle?> = _careCircle.asStateFlow()

  init {
    loadCircle()
  }

  // id -> label shown on the "who is involved" chips.
  // All the names in the care circle are clickable, using chips.
  // This prevents spelling mistakes and the omission of separators, making the code more robust.
  // TODO :add a button for adding a person has not yet been implemented. This will be done in M2.
  val involvedOptions: StateFlow<Map<String, String>> =
      careCircle
          .map { circle ->
            if (circle == null) emptyMap()
            else
                circle.people.mapValues { it.value.displayName() } +
                    circle.memberIds.associateWith { it }
          }
          .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

  private fun Person.displayName(): String =
      nickname?.takeIf { it.isNotBlank() }
          ?: listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

  /** Loads the care circle associated with the authenticated user. */
  private fun loadCircle() {
    val uid = authRepo.currentUser?.uid
    if (uid == null) {
      setErrorMsg("Your identity can't be verified")
      return
    }
    viewModelScope.launch {
      val profile =
          profileRepo.getProfile(uid).getOrElse {
            setErrorMsg("Could not load your profile: ${it.message}")
            return@launch
          }
      val circleId = profile?.circleIds?.firstOrNull()
      if (circleId == null) {
        setErrorMsg("Your care circle isn't set up")
        return@launch
      }
      circleRepo
          .getCircle(circleId)
          .onSuccess { _careCircle.value = it }
          .onFailure { setErrorMsg("Could not load your care circle: ${it.message}") }
    }
  }

  /** Uploads the picture currently stored in the UI state. */
  fun uploadPicture() {
    val authorId = authRepo.currentUser?.uid
    if (authorId == null) {
      setErrorMsg("Upload failed: your identity can't be verified")
      return
    }
    val circle = _careCircle.value
    if (circle == null) {
      setErrorMsg("Your care circle is still loading, try again")
      return
    }
    val state = _uiState.value
    val file = state.pictureFile
    if (file == null) {
      setErrorMsg("Please select a picture first")
      return
    }
    if (state.isUploading) return

    _uiState.value = state.copy(isUploading = true)
    viewModelScope.launch {
      try {
        val id = mediaRepo.newMediaId(circle.id)
        val item =
            MediaItem.Picture(
                id = id,
                authorId = authorId,
                // must start with careCircles/<circleId>/ (see MediaRepositoryFirebase.getFile)
                storagePath = "careCircles/${circle.id}/media/$id.jpg",
                createdAt = System.currentTimeMillis(),
                description = state.description,
                date = state.date,
                location = null,
                shownIds = state.shownIds.toList(),
                authorizedIds = emptyList(), // empty = whole circle can see it, for the moment
                status = MediaStatus.APPROVED,
                width = state.pictureWidth,
                height = state.pictureHeight,
            )
        mediaRepo.addMedia(circle, item, file)
        _uiState.value = _uiState.value.copy(isUploading = false, isUploaded = true)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(isUploading = false)
        setErrorMsg("Upload failed: ${e.message ?: "unknown error"}")
      }
    }
  }

  /**
   * Stores the local picture selected by the user.
   *
   * @param file The local picture file.
   * @param width The picture width in pixels.
   * @param height The picture height in pixels.
   */
  fun setPicture(file: File, width: Int, height: Int) {
    _uiState.value =
        _uiState.value.copy(
            pictureFile = file,
            pictureWidth = width,
            pictureHeight = height,
        )
  }

  /** Clears the current error message from the UI state. */
  fun clearErrorMsg() {
    _uiState.value = _uiState.value.copy(errorMsg = null)
  }

  /**
   * Sets the error message shown by the picture upload screen.
   *
   * @param errorMsg The error message to display.
   */
  fun setErrorMsg(errorMsg: String) {
    _uiState.value = _uiState.value.copy(errorMsg = errorMsg)
  }

  /**
   * Updates the picture description in the UI state.
   *
   * @param description The new picture description.
   */
  fun setDescription(description: String) {
    _uiState.value = _uiState.value.copy(description = description)
  }

  /**
   * Updates the picture date in the UI state.
   *
   * @param date The date associated with the picture.
   */
  fun setDate(date: MemoryDate) {
    _uiState.value = _uiState.value.copy(date = date)
  }

  /**
   * Adds a person to the tagged people, or removes them if already tagged.
   *
   * @param id The ID of the person to select or deselect.
   */
  fun toggleShownId(id: String) {
    _uiState.update { state ->
      val updated = if (id in state.shownIds) state.shownIds - id else state.shownIds + id
      state.copy(shownIds = updated)
    }
  }
}
