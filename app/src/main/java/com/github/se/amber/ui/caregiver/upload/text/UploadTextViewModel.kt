// Written by Yuhan YANG, with assistance from Cursor.
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.caregiver.upload.text

import androidx.annotation.StringRes
import com.github.se.amber.R
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.circle.ActiveCircleRepositoryProvider
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
import com.github.se.amber.model.validation.InputValidation
import com.github.se.amber.ui.caregiver.upload.media.CommonMediaUIState
import com.github.se.amber.ui.caregiver.upload.media.MediaUIState
import com.github.se.amber.ui.caregiver.upload.media.MediaViewModel
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.update

/**
 * Error messages specific to the caregiver Upload Text ViewModel; the ones shared with the other
 * upload screens are in [com.github.se.amber.ui.caregiver.upload.media.MediaViewModelErrorMessage].
 *
 * @property messageRes the message shown to the user, as a string resource.
 */
enum class UploadTextViewModelErrorMessage(@param:StringRes val messageRes: Int) {
  TITLE_EMPTY(R.string.uploadTextTitleEmpty),
  TEXT_EMPTY(R.string.uploadTextDescriptionEmpty),
}

/**
 * UI state of the text upload screen.
 *
 * @property title the story title typed by the caregiver. [MediaItem.Text] has no title yet, so it
 *   stays in this state until that parameter exists.
 * @property text the story body typed in the description box. This is [MediaItem.Text.text].
 * @property common the date, place and people shared with the other upload screens. A text story
 *   keeps [CommonMediaUIState.description] empty.
 */
data class UploadTextUIState(
    val title: String = "",
    val text: String = "",
    override val common: CommonMediaUIState = CommonMediaUIState(),
) : MediaUIState<UploadTextUIState> {
  /** Whether the story has a title and a body, so the upload button can be pressed. */
  override val canUpload: Boolean
    get() = title.isNotBlank() && text.isNotBlank()

  override fun withCommon(common: CommonMediaUIState): UploadTextUIState = copy(common = common)
}

/**
 * Holds the text upload screen state.
 *
 * The date, place, people and upload are [MediaViewModel]. This class only adds the title and the
 * story body. Leaving the screen stays in the composable: this class does not navigate.
 *
 * @param mediaRepo The repository used to create and upload media items.
 * @param authRepo The repository used to access the currently authenticated user.
 * @param profileRepo The repository used to load the authenticated user's profile.
 * @param circleRepo The repository used to load the user's care circle.
 * @param activeCircleRepo The repository used to follow the user's active care circle.
 * @param placeRepo The repository used to find real places from what the user types.
 * @param cleanupScope Where a canceled upload finishes its cleanup.
 */
open class UploadTextViewModel(
    mediaRepo: MediaRepository = MediaRepositoryProvider.repository,
    authRepo: AuthRepository = AuthRepositoryProvider.repository,
    profileRepo: ProfileRepository = ProfileRepositoryProvider.default.repository,
    circleRepo: CareCircleRepository = CareCircleRepositoryProvider.default.repository,
    activeCircleRepo: ActiveCircleRepository = ActiveCircleRepositoryProvider.default.repository,
    placeRepo: PlaceSearchRepository = PlaceSearchRepositoryProvider.default.repository,
    cleanupScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) :
    MediaViewModel<UploadTextUIState>(
        initialState =
            UploadTextUIState(common = CommonMediaUIState(date = MemoryDate(LocalDate.now().year))),
        mediaRepo = mediaRepo,
        authRepo = authRepo,
        profileRepo = profileRepo,
        circleRepo = circleRepo,
        activeCircleRepo = activeCircleRepo,
        placeRepo = placeRepo,
        cleanupScope = cleanupScope,
    ) {

  /**
   * A text story keeps [CommonMediaUIState.description] empty. Its body lives in
   * [UploadTextUIState.text], so the shared empty-description check must not block upload.
   */
  override val requiresCommonDescription: Boolean
    get() = false

  // FIXME: MediaItem.Text needs a title parameter. Save this field there once that parameter
  // exists.
  /** Stores [title] as the story title, cut at [InputValidation.SHORT_TEXT]'s maximum length. */
  fun setTitle(title: String) {
    val cleaned = InputValidation.SHORT_TEXT.clean(title)
    _uiState.update { it.copy(title = cleaned) }
  }

  /**
   * The description saved on a text story.
   *
   * A text story has no shared description: [MediaItem.description] stays empty. The words typed in
   * the description box are [UploadTextUIState.text].
   */
  fun mediaDescription(): String = ""

  /**
   * Stores [text] as the story body from the description box, cut at
   * [InputValidation.DESCRIPTION]'s maximum length.
   */
  fun setText(text: String) {
    val cleaned = InputValidation.DESCRIPTION.clean(text)
    _uiState.update { it.copy(text = cleaned) }
  }

  /**
   * Checks that the story has a title and a body.
   *
   * @param state The current text upload UI state.
   * @return The error to show if the title or the body is missing, as a string resource, or null if
   *   both are there.
   */
  @StringRes
  override fun checkContent(state: UploadTextUIState): Int? =
      when {
        !InputValidation.SHORT_TEXT.isValid(state.title) ->
            UploadTextViewModelErrorMessage.TITLE_EMPTY.messageRes
        !InputValidation.DESCRIPTION.isValid(state.text) ->
            UploadTextViewModelErrorMessage.TEXT_EMPTY.messageRes
        else -> null
      }

  /** A text story has no file. [state] is not read. */
  override fun fileOf(state: UploadTextUIState): File? = null

  /**
   * Builds the text story from the body typed on the screen. The title stays in the UI state until
   * [MediaItem.Text] has a title parameter.
   *
   * @param id The new media ID, from [MediaRepository.newMediaId].
   * @param state The screen state to upload.
   * @param authorId The uid of the signed-in user.
   * @param circle The care circle the media is added to.
   * @param date The date of the media.
   * @param shownIds The people shown, already restricted to those still in the circle.
   * @return The text media item to save and upload.
   */
  override fun createItem(
      id: String,
      state: UploadTextUIState,
      authorId: String,
      circle: CareCircle,
      date: MemoryDate,
      shownIds: List<String>,
  ): MediaItem =
      MediaItem.Text(
          id = id,
          authorId = authorId,
          createdAt = System.currentTimeMillis(),
          description = mediaDescription(),
          date = date,
          location = state.common.location.pickedPlace,
          shownIds = shownIds,
          authorizedIds = emptyList(),
          status = MediaStatus.APPROVED,
          text = state.text.trim(),
      )
}
