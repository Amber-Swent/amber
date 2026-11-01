/* Written by LaMaier, with assistance from
 * Claude (Anthropic).
 * Modified by MeiliV, with assistance from OpenAI Codex.
 * Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
 * Modified by LaMaier, with assistance from
 * Claude (Anthropic).
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
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MediaRepositoryProvider
import com.github.se.amber.model.media.MediaStatus
import com.github.se.amber.ui.caregiver.upload.media.UploadCheck
import com.github.se.amber.ui.caregiver.upload.media.UploadMediaUIState
import com.github.se.amber.ui.caregiver.upload.media.UploadMediaViewModel
import com.github.se.amber.ui.caregiver.upload.picture.SelectedPictureReader
import com.github.se.amber.ui.caregiver.upload.picture.reader
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Error message used by the caregiver Upload Picture ViewModel. */
enum class UploadPictureViewModelErrorMessage(val mess: String) {
  SELECT_PICTURE(mess = "Please select a picture first"),
  COULD_NOT_READ_PICTURE(mess = "Could not read your picture: "),
}

/** Everything the picture screen displays: the shared fields plus the picture. */
data class UploadPictureUIState(
    val media: UploadMediaUIState = UploadMediaUIState(),
    val pictureFile: File? = null,
    val pictureWidth: Int = 0,
    val pictureHeight: Int = 0,
) {
  val canUpload: Boolean
    get() = media.canUpload && pictureFile != null
}

/** The picture-only part of the state, owned by the ViewModel. */
private data class PictureContent(
    val file: File? = null,
    val width: Int = 0,
    val height: Int = 0,
)

class UploadPictureViewModel(
    mediaRepo: MediaRepository = MediaRepositoryProvider.repository,
    authRepo: AuthRepository = AuthRepositoryProvider.repository,
    profileRepo: ProfileRepository = ProfileRepositoryProvider.default.repository,
    circleRepo: CareCircleRepository = CareCircleRepositoryProvider.default.repository,
    placeRepo: PlaceSearchRepository = PlaceSearchRepositoryProvider.default.repository,
    private val pictureReader: SelectedPictureReader = reader,
) :
    UploadMediaViewModel(
        mediaRepo = mediaRepo,
        authRepo = authRepo,
        profileRepo = profileRepo,
        circleRepo = circleRepo,
        placeRepo = placeRepo,
    ) {
  private val _picture = MutableStateFlow(PictureContent())

  val uiState: StateFlow<UploadPictureUIState> =
      combine(mediaState, _picture) { common, picture ->
            UploadPictureUIState(common, picture.file, picture.width, picture.height)
          }
          .stateIn(viewModelScope, SharingStarted.Eagerly, UploadPictureUIState())

  fun uploadPicture() {
    val picture = _picture.value
    startUpload(checkUpload(picture)) { id, shownIds, ready ->
      buildPicture(id, shownIds, picture, ready)
    }
  }

  private fun checkUpload(picture: PictureContent): UploadCheck {
    val missingPicture =
        if (picture.file == null) UploadPictureViewModelErrorMessage.SELECT_PICTURE.mess else null
    val check = checkCommonUpload(missingPicture)
    return if (check is UploadCheck.Ready) check.copy(file = picture.file) else check
  }

  private fun buildPicture(
      id: String,
      shownIds: List<String>,
      picture: PictureContent,
      ready: UploadCheck.Ready,
  ): MediaItem.Picture {
    val common = _mediaState.value
    return MediaItem.Picture(
        id = id,
        authorId = ready.authorId,
        // must start with careCircles/<circleId>/ (see MediaRepositoryFirebase.getFile)
        storagePath = "careCircles/${ready.circle.id}/media/$id.jpg",
        createdAt = System.currentTimeMillis(),
        description = common.description,
        date = ready.date,
        location = common.location.pickedPlace,
        shownIds = shownIds,
        authorizedIds = emptyList(),
        status = MediaStatus.APPROVED,
        width = picture.width,
        height = picture.height,
    )
  }

  fun selectPicture(uri: String) {
    viewModelScope.launch {
      try {
        val picture = pictureReader.read(uri)
        setPicture(picture.file, picture.width, picture.height)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        setErrorMsg(
            UploadPictureViewModelErrorMessage.COULD_NOT_READ_PICTURE.mess + (e.message ?: "")
        )
      }
    }
  }

  internal fun setPicture(file: File, width: Int, height: Int) {
    _picture.value = PictureContent(file, width, height)
  }
}
