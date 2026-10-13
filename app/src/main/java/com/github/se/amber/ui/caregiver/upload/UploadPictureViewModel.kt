/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload

import androidx.lifecycle.ViewModel
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MediaRepositoryProvider
import com.github.se.amber.model.media.MemoryDate
import java.io.File
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
    val date: MemoryDate? = null,
    val involvedName: String = "", // comma-separated names, mapped to shownIds at upload time
    val pictureFile: File? = null,
    val pictureWidth: Int = 0,
    val pictureHeight: Int = 0,
    val isUploading: Boolean = false,
    val isUploaded: Boolean = false,
    val errorMsg: String? = null,
)

/** Manages the state and user actions for the picture upload screen. */
class UploadPictureViewModel(
    private val repository: MediaRepository = MediaRepositoryProvider.repository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
  private val _uiState = MutableStateFlow(UploadPictureUIState())
  val uiState: StateFlow<UploadPictureUIState> = _uiState.asStateFlow()

  /** Uploads the picture currently stored in the UI state. */
  fun uploadPicture() {}









































    /** Called by the View once the camera or the gallery returned a local file. */
    fun setPicture(file: File, width: Int, height: Int) {
        _uiState.value =
            _uiState.value.copy(pictureFile = file, pictureWidth = width, pictureHeight = height)
    }

    /** Called by the View when taking or selecting a picture failed. */
    fun onPictureError(message: String) = setErrorMsg(message)

    fun clearErrorMsg() {
        _uiState.value = _uiState.value.copy(errorMsg = null)
    }

    private fun setErrorMsg(errorMsg: String) {
        _uiState.value = _uiState.value.copy(errorMsg = errorMsg)
    }

  fun setTitle(title: String) {
    _uiState.value = _uiState.value.copy(title = title)
  }

  fun setDescription(description: String) {
    _uiState.value = _uiState.value.copy(description = description)
  }

  fun setDate(date: MemoryDate) {
    _uiState.value = _uiState.value.copy(date = date)
  }

  fun setInvolvedName(involvedName: String) {
    _uiState.value = _uiState.value.copy(involvedName = involvedName)
  }
}

// TODO : Should change this
//  Maps the typed names to the ids of the persons of the circle (Person repo not shown)
private fun fromStringToShownIds(careCircle : CareCircle, str : String): List<String> {
    val name : List<String> = extractPersonName(str)
    return name
}

fun extractPersonName(input: String): List<String> {
    val result = mutableListOf<String>()
    var previousCommaIndex = -1

    for (i in input.indices) {
        if (input[i] == ',') {
            if (previousCommaIndex != -1 && i > 0) {
                result.add( input.slice(previousCommaIndex..i) )
            }
            previousCommaIndex = i
        }
    }

    return result
}
