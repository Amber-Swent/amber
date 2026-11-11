// Written by Lou-Anne Maier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by MeiliV, with assistance from OpenAI Codex.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Lou-Anne Maier, with assistance from Claude (Anthropic) via Claude Code.
// Modified with assistance from GitHub Copilot.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.upload.pictures

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.se.amber.R
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.location.PlaceSearchRepository
import com.github.se.amber.data.location.PlaceSearchRepositoryProvider
import com.github.se.amber.data.media.picture.CapturedPictureSource
import com.github.se.amber.data.media.picture.SelectedPictureReader
import com.github.se.amber.data.media.picture.capturedPictures
import com.github.se.amber.data.media.picture.reader
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
import com.github.se.amber.ui.caregiver.upload.media.CommonMediaUIState
import com.github.se.amber.ui.caregiver.upload.media.MediaUIState
import com.github.se.amber.ui.caregiver.upload.media.MediaViewModel
import java.io.File
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "UploadPicturesViewModel"

/**
 * Error messages specific to the caregiver Upload Picture ViewModel; the ones shared with the other
 * upload screens are in [com.github.se.amber.ui.caregiver.upload.media.MediaViewModelErrorMessage].
 *
 * @property messageRes the message shown to the user, as a string resource.
 */
enum class UploadPictureViewModelErrorMessage(@param:StringRes val messageRes: Int) {
  SELECT_PICTURE(R.string.uploadPictureErrorSelect),
  COULD_NOT_READ_PICTURE(R.string.uploadPictureErrorRead),
  COULD_NOT_OPEN_CAMERA(R.string.uploadPictureErrorCamera),
  NOT_A_PHOTO(R.string.uploadPictureErrorNotAPhoto),
}

/**
 * Represents the state displayed by the picture upload screen.
 *
 * @param common The fields shared with the other upload screens: description, date, place, people,
 *   upload progress and error message.
 * @param pictureFile The local picture file selected for upload.
 * @param pictureWidth The width of the selected picture in pixels.
 * @param pictureHeight The height of the selected picture in pixels.
 * @param isCapturing Whether the camera is open, or its picture is still being read.
 */
data class UploadPictureUIState(
    override val common: CommonMediaUIState = CommonMediaUIState(),
    val pictureFile: File? = null,
    val pictureWidth: Int = 0,
    val pictureHeight: Int = 0,
    val isCapturing: Boolean = false,
) : MediaUIState<UploadPictureUIState> {
  override val canUpload: Boolean
    get() = pictureFile != null && !isCapturing && common.canUpload

  override fun withCommon(common: CommonMediaUIState): UploadPictureUIState = copy(common = common)
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
 * @param capturedPictureSource The source of the files the camera app saves pictures to.
 * @param savedState Keeps the form and the selected capture if Android kills the app.
 * @param cleanupScope Where the last capture is deleted: the view model's own scope is over by
 *   then.
 */
class UploadPictureViewModel(
    mediaRepo: MediaRepository = MediaRepositoryProvider.repository,
    authRepo: AuthRepository = AuthRepositoryProvider.repository,
    profileRepo: ProfileRepository = ProfileRepositoryProvider.default.repository,
    circleRepo: CareCircleRepository = CareCircleRepositoryProvider.default.repository,
    placeRepo: PlaceSearchRepository = PlaceSearchRepositoryProvider.default.repository,
    private val pictureReader: SelectedPictureReader = reader,
    private val capturedPictureSource: CapturedPictureSource = capturedPictures,
    private val savedState: SavedStateHandle,
    private val cleanupScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) :
    MediaViewModel<UploadPictureUIState>(
        initialState =
            savedState.restoreDraft(
                UploadPictureUIState(
                    common = CommonMediaUIState(date = MemoryDate(LocalDate.now().year))
                )
            ),
        mediaRepo = mediaRepo,
        authRepo = authRepo,
        profileRepo = profileRepo,
        circleRepo = circleRepo,
        placeRepo = placeRepo,
    ) {
  /** URIs the camera app should save a new picture to: the screen opens the camera on each. */
  private val _cameraRequests = Channel<String>(Channel.BUFFERED)
  val cameraRequests: Flow<String> = _cameraRequests.receiveAsFlow()
  // The capture shown as the selected picture, deleted if it ends up not being uploaded
  private var selectedCaptureUri: String?
    get() = savedState[SELECTED_CAPTURE_KEY]
    set(uri) {
      savedState[SELECTED_CAPTURE_KEY] = uri
    }

  init {
    viewModelScope.launch { _uiState.collect(savedState::saveDraft) }
  }

  /**
   * Checks whether a picture has been selected for upload.
   *
   * @param state The current picture upload UI state.
   * @return The error to show if no picture is selected, as a string resource, or null if a picture
   *   is selected.
   */
  @StringRes
  override fun checkContent(state: UploadPictureUIState): Int? =
      if (state.pictureFile == null) UploadPictureViewModelErrorMessage.SELECT_PICTURE.messageRes
      else null

  /**
   * Gets the picture file selected for upload.
   *
   * @param state The current picture upload UI state.
   * @return The selected picture file, or null if no picture is selected.
   */
  override fun fileOf(state: UploadPictureUIState): File? = state.pictureFile

  /** The repository moved the picture's file: a capture that was uploaded is no longer ours. */
  override fun onUploaded() {
    selectedCaptureUri = null
  }

  /**
   * Creates a media item containing the selected picture and its upload metadata.
   *
   * @param id The unique identifier assigned to the media item.
   * @param state The current picture upload UI state.
   * @param authorId The identifier of the user who uploaded the picture.
   * @param circle The care circle associated with the picture.
   * @param date The date associated with the picture.
   * @param shownIds The identifiers of people shown in the picture.
   * @return The picture media item to save and upload.
   */
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
          description = state.common.description,
          date = date,
          location = state.common.location.pickedPlace,
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
    if (_uiState.value.common.isUploading) return
    viewModelScope.launch {
      try {
        val picture = pictureReader.read(uri)
        setPicture(
            picture.file,
            picture.width,
            picture.height,
        )
        discardSelectedCapture()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Reading the selected picture failed", e)
        setErrorMsg(UploadPictureViewModelErrorMessage.COULD_NOT_READ_PICTURE.messageRes)
      }
    }
  }

  /** Prepares a file for a new picture and asks the screen to open the camera on it. */
  fun onTakePicture() {
    val state = _uiState.value
    if (state.isCapturing || state.common.isUploading) return
    // Until the camera's picture is read: a second tap would delete the file being captured
    setCapturing(true)
    viewModelScope.launch {
      try {
        _cameraRequests.send(capturedPictureSource.newCaptureUri(keeping = selectedCaptureUri))
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Preparing a file for the camera failed", e)
        setCapturing(false)
        setErrorMsg(UploadPictureViewModelErrorMessage.COULD_NOT_OPEN_CAMERA.messageRes)
      }
    }
  }

  /**
   * Stores the picture the camera app just saved, replacing any earlier capture. A file that can't
   * be read is deleted, and the user can tap the camera again.
   *
   * @param uri A URI sent through [cameraRequests].
   */
  fun onPictureCaptured(uri: String) {
    viewModelScope.launch {
      try {
        val picture = capturedPictureSource.read(uri)
        setPicture(picture.file, picture.width, picture.height)
        discardSelectedCapture()
        selectedCaptureUri = uri
      } catch (e: CancellationException) {
        throw e
      } catch (_: IllegalArgumentException) {
        // Not a picture: a camera app may have saved a video
        setErrorMsg(UploadPictureViewModelErrorMessage.NOT_A_PHOTO.messageRes)
        capturedPictureSource.discard(uri)
      } catch (e: Exception) {
        Log.e(TAG, "Reading the captured picture failed", e)
        setErrorMsg(UploadPictureViewModelErrorMessage.COULD_NOT_READ_PICTURE.messageRes)
        capturedPictureSource.discard(uri)
      } finally {
        setCapturing(false)
      }
    }
  }

  /**
   * Deletes the file of a capture the user cancelled. Any picture already selected is kept.
   *
   * @param uri A URI sent through [cameraRequests].
   */
  fun onCaptureCancelled(uri: String) {
    viewModelScope.launch {
      try {
        capturedPictureSource.discard(uri)
      } finally {
        setCapturing(false)
      }
    }
  }

  /**
   * Tells the user that no camera app could be opened and deletes the file prepared for it.
   *
   * @param uri A URI sent through [cameraRequests].
   */
  fun onCameraUnavailable(uri: String) {
    setErrorMsg(UploadPictureViewModelErrorMessage.COULD_NOT_OPEN_CAMERA.messageRes)
    onCaptureCancelled(uri)
  }

  private suspend fun discardSelectedCapture() {
    selectedCaptureUri?.let { capturedPictureSource.discard(it) }
    selectedCaptureUri = null
  }

  /** A capture that wasn't uploaded is a private picture nothing will use any more. */
  override fun onCleared() {
    selectedCaptureUri?.let { uri -> cleanupScope.launch { capturedPictureSource.discard(uri) } }
    super.onCleared()
  }

  private fun setCapturing(isCapturing: Boolean) {
    _uiState.update { it.copy(isCapturing = isCapturing) }
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

  companion object {
    private const val SELECTED_CAPTURE_KEY = "selected_capture_uri"

    /** Gives the view model its saved state, which the default factory wouldn't. */
    val Factory = viewModelFactory {
      initializer { UploadPictureViewModel(savedState = createSavedStateHandle()) }
    }
  }
}
