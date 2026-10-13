/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MediaRepositoryProvider
import com.github.se.amber.model.media.MediaStatus
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.UserProfile
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

/** Represents the state displayed by the picture upload screen. */
data class UploadPictureUIState(
    val description: String = "",
    val date: MemoryDate? = null,
    val shownIds: Set<String> = emptySet(), // ids of the people tagged on the picture
    val pictureFile: File? = null,
    val pictureWidth: Int = 0,
    val pictureHeight: Int = 0,
    val isUploading: Boolean = false,
    val isUploaded: Boolean = false,
    val errorMsg: String? = null,
)

/** Manages the state and user actions for the picture upload screen. */
class UploadPictureViewModel(
    private val mediaRepo: MediaRepository = MediaRepositoryProvider.repository,
    private val authRepo: AuthRepository = AuthRepositoryProvider.repository,
    private val profileRepo: ProfileRepository = ProfileRepositoryProvider.repository,
    private val circleRepo: CareCircleRepository = CareCircleRepositoryProvider.repository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(UploadPictureUIState())
  val uiState: StateFlow<UploadPictureUIState> = _uiState.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _careCircle = MutableStateFlow<CareCircle?>(null)
    val careCircle: StateFlow<CareCircle?> = _careCircle.asStateFlow()

    /** id -> label shown on the "who is involved" chips. */
    val involvedOptions: StateFlow<Map<String, String>> =
        careCircle
            .map { circle ->
                if (circle == null) emptyMap()
                else
                    circle.people.mapValues { it.value.displayName() } +
                            circle.memberIds.associateWith { it } // members: raw uid until names are available
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private fun Person.displayName(): String =
        nickname?.takeIf { it.isNotBlank() }
            ?: listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

    init {
        loadCircle()
    }

    private fun loadCircle() {
        val uid = authRepo.currentUser?.uid
        if (uid == null) {
            setErrorMsg("Your identity can't be verified")
            return
        }
        viewModelScope.launch {
            val profile = profileRepo.getProfile(uid).getOrElse {
                setErrorMsg("Could not load your profile: ${it.message}")
                return@launch
            }
            val circleId = profile?.circleIds?.firstOrNull()
            if (circleId == null) {
                setErrorMsg("Your care circle isn't set up")
                return@launch
            }
            circleRepo.getCircle(circleId)
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
                        authorizedIds = emptyList(), // empty = whole circle can see it
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

  /** Called by the View once the camera or the gallery returned a local file. */
  fun setPicture(file: File, width: Int, height: Int) {
    _uiState.value =
        _uiState.value.copy(pictureFile = file, pictureWidth = width, pictureHeight = height)
  }

  fun clearErrorMsg() {
    _uiState.value = _uiState.value.copy(errorMsg = null)
  }

  fun setErrorMsg(errorMsg: String) {
    _uiState.value = _uiState.value.copy(errorMsg = errorMsg)
  }

  fun setDescription(description: String) {
    _uiState.value = _uiState.value.copy(description = description)
  }

  fun setDate(date: MemoryDate) {
    _uiState.value = _uiState.value.copy(date = date)
  }

  /** Adds the person to the tagged ones, or removes them if already tagged. */
  fun toggleShownId(id: String) {
    _uiState.update { state ->
      val updated = if (id in state.shownIds) state.shownIds - id else state.shownIds + id
      state.copy(shownIds = updated)
    }
  }

    fun giveAllPossibleId(): Set<String> {
        return careCircle.value!!.people.keys + careCircle.value!!.memberIds
    }
}
