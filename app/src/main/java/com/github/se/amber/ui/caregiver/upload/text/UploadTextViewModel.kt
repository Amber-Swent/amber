// Written by Yuhan YANG, with assistance from Cursor.
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.caregiver.upload.text

import androidx.annotation.StringRes
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
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.ui.caregiver.upload.media.CommonMediaUIState
import com.github.se.amber.ui.caregiver.upload.media.MediaUIState
import com.github.se.amber.ui.caregiver.upload.media.MediaViewModel
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * UI state of the text upload screen.
 *
 * @property title the story title typed by the caregiver. It is [MediaItem.Text.title]. This screen
 *   does not save it yet.
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
 * story body. A body that only has a `//Todo:` comment is not implemented yet. Leaving the screen
 * stays in the composable: this class does not navigate.
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

  // FIXME: Save this field in MediaItem.Text.title. The parameter exists; this screen does not
  // write it yet.
  /** Stores [title] as the story title. */
  fun setTitle(title: String) {
    // Todo: save the title in the UI state
  }

  /**
   * The description saved on a text story.
   *
   * A text story has no shared description: [MediaItem.description] stays empty. The words typed in
   * the description box are [UploadTextUIState.text].
   */
  fun mediaDescription(): String = ""

  /** Stores [text] as the story body from the description box. */
  fun setText(text: String) {
    // Todo: save the story body in MediaItem.Text.text
  }

  @StringRes
  override fun checkContent(state: UploadTextUIState): Int? {
    // Todo: check that [state] has a title and a body
    return null
  }

  /** A text story has no file. [state] is not read. */
  override fun fileOf(state: UploadTextUIState): File? = null

  override fun createItem(
      id: String,
      state: UploadTextUIState,
      authorId: String,
      circle: CareCircle,
      date: MemoryDate,
      shownIds: List<String>,
  ): MediaItem {
    // Todo: build the text story from the title and the body
    return MediaItem.Text(id = id, authorId = authorId, date = date, shownIds = shownIds)
  }
}
