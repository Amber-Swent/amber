/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 * Modified by MeiliV, with assistance from OpenAI Codex.
 * Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
 * Modified by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload.pictures

import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.location.PlaceSearchRepository
import com.github.se.amber.data.location.PlaceSearchRepositoryProvider
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
import com.github.se.amber.ui.caregiver.upload.media.MediaLocationUIState
import com.github.se.amber.ui.caregiver.upload.media.MediaUIState
import com.github.se.amber.ui.caregiver.upload.media.MediaViewModel
import com.github.se.amber.ui.caregiver.upload.picture.SelectedPictureReader
import com.github.se.amber.ui.caregiver.upload.picture.reader
import java.io.File
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Error messages specific to the caregiver Upload Picture ViewModel; the ones shared with the other
 * upload screens are in
 * [com.github.se.amber.ui.caregiver.upload.media.MediaViewModelErrorMessage].
 */
enum class UploadPictureViewModelErrorMessage(val mess: String) {
  SELECT_PICTURE(mess = "Please select a picture first"),
  COULD_NOT_READ_PICTURE(mess = "Could not read your picture: "),
}

/**
 * Represents the state displayed by the picture upload screen.
 *
 * @param description The description entered for the picture.
 * @param date The date associated with the picture, if one was selected.
 * @param location Where the picture was taken: the place search and the picked place.
 * @param shownIds The IDs of the people tagged in the picture.
 * @param pictureFile The local picture file selected for upload.
 * @param pictureWidth The width of the selected picture in pixels.
 * @param pictureHeight The height of the selected picture in pixels.
 * @param isUploading Whether an upload is currently in progress.
 * @param errorMsg The error message to display, if any.
 */
data class UploadPictureUIState(
    override val description: String = "",
    override val date: MemoryDate? = null,
    override val location: MediaLocationUIState = MediaLocationUIState(),
    override val shownIds: Set<String> = emptySet(),
    val pictureFile: File? = null,
    val pictureWidth: Int = 0,
    val pictureHeight: Int = 0,
    override val isUploading: Boolean = false,
    override val errorMsg: String? = null,
    override val isAddPersonOpen: Boolean = false,
    override val isAddingPerson: Boolean = false,
    override val personFirstName: String = "",
    override val personLastName: String = "",
    override val personNickname: String = "",
) : MediaUIState<UploadPictureUIState> {
  override val canUpload: Boolean
    get() =
        pictureFile != null &&
            description.isNotBlank() &&
            date != null &&
            location.hasValidPlace &&
            !isUploading

  override fun copyMedia(
      description: String,
      date: MemoryDate?,
      location: MediaLocationUIState,
      shownIds: Set<String>,
      isUploading: Boolean,
      errorMsg: String?,
      isAddPersonOpen: Boolean,
      isAddingPerson: Boolean,
      personFirstName: String,
      personLastName: String,
      personNickname: String,
  ): UploadPictureUIState =
      copy(
          description = description,
          date = date,
          location = location,
          shownIds = shownIds,
          isUploading = isUploading,
          errorMsg = errorMsg,
          isAddPersonOpen = isAddPersonOpen,
          isAddingPerson = isAddingPerson,
          personFirstName = personFirstName,
          personLastName = personLastName,
          personNickname = personNickname,
      )
}

/**
 * Manages the state and user actions for the picture upload screen. What it shares with the other
 * upload screens (circle, description, date, place, people) is in [MediaViewModel].
 *
 * @param mediaRepo The repository used to create and upload media items.
 * @param authRepo The repository used to access the currently authenticated user.
 * @param profileRepo The repository used to load the authenticated user's profile.
 * @param circleRepo The repository used to load the user's care circle.
 * @param placeRepo The repository used to find real places from what the user types.
 * @param pictureReader The reader used to copy selected pictures and read their dimensions.
 */
class UploadPictureViewModel(
    mediaRepo: MediaRepository = MediaRepositoryProvider.repository,
    authRepo: AuthRepository = AuthRepositoryProvider.repository,
    profileRepo: ProfileRepository = ProfileRepositoryProvider.default.repository,
    circleRepo: CareCircleRepository = CareCircleRepositoryProvider.default.repository,
    placeRepo: PlaceSearchRepository = PlaceSearchRepositoryProvider.default.repository,
    private val pictureReader: SelectedPictureReader = reader,
) :
    MediaViewModel<UploadPictureUIState>(
        initialState = UploadPictureUIState(date = MemoryDate(LocalDate.now().year)),
        mediaRepo = mediaRepo,
        authRepo = authRepo,
        profileRepo = profileRepo,
        circleRepo = circleRepo,
        placeRepo = placeRepo,
    ) {

  override fun checkContent(state: UploadPictureUIState): String? =
      if (state.pictureFile == null) UploadPictureViewModelErrorMessage.SELECT_PICTURE.mess
      else null

  override fun fileOf(state: UploadPictureUIState): File? = state.pictureFile

  override fun createItem(
      id: String,
      state: UploadPictureUIState,
      authorId: String,
      circle: CareCircle,
      date: MemoryDate,
      shownIds: List<String>,
  ): MediaItem =
      MediaItem.Picture(
          id = id,
          authorId = authorId,
          // must start with careCircles/<circleId>/ (see MediaRepositoryFirebase.getFile)
          storagePath = "careCircles/${circle.id}/media/$id.jpg",
          createdAt = System.currentTimeMillis(),
          description = state.description,
          date = date,
          location = state.location.pickedPlace,
          shownIds = shownIds,
          authorizedIds = emptyList(), // empty = whole circle can see it, for the moment
          status = MediaStatus.APPROVED,
          width = state.pictureWidth,
          height = state.pictureHeight,
      )

  /**
   * Reads a selected picture and stores it in the UI state.
   *
   * @param uri The string representation of the selected picture URI.
   */
  fun selectPicture(uri: String) {
    viewModelScope.launch {
      try {
        val picture = pictureReader.read(uri)
        setPicture(
            picture.file,
            picture.width,
            picture.height,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        setErrorMsg(
            UploadPictureViewModelErrorMessage.COULD_NOT_READ_PICTURE.mess + (e.message ?: "")
        )
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
  internal fun setPicture(file: File, width: Int, height: Int) {
    _uiState.update {
      it.copy(
          pictureFile = file,
          pictureWidth = width,
          pictureHeight = height,
      )
    }
  }
}
