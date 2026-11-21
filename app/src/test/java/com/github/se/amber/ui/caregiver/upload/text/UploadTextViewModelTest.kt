// Written by Yuhan YANG, with assistance from Cursor.
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.caregiver.upload.text

import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.location.PlaceSearchRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.ui.caregiver.upload.media.CommonMediaUIState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Checks when a text story can be uploaded, that it has no file, and that the content check is not
 * implemented yet. Also checks the shared description and that [UploadTextViewModel.createItem]
 * builds a text story.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UploadTextViewModelTest {
  private val dispatcher = UnconfinedTestDispatcher()
  private val viewModels = mutableListOf<UploadTextViewModel>()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    viewModels.forEach { it.viewModelScope.cancel() }
    Dispatchers.resetMain()
  }

  @Test
  fun uploadNeedsATitleAndTheStoryText() {
    assertFalse(UploadTextUIState().canUpload)
    assertFalse(UploadTextUIState(title = "   ").canUpload)
    assertFalse(UploadTextUIState(text = "   ").canUpload)
    assertFalse(
        UploadTextUIState(
                title = "Market day",
                common = CommonMediaUIState(description = "We bought bread."),
            )
            .canUpload,
    )
    assertTrue(UploadTextUIState(title = "Market day", text = "We bought bread.").canUpload)
  }

  @Test
  fun textStoryHasNoFile() {
    val viewModel = openViewModel()

    assertNull(viewModel.file(UploadTextUIState(title = "Market day", text = "We bought bread.")))
  }

  @Test
  fun contentCheckIsNotImplemented() {
    val viewModel = openViewModel()

    assertNull(
        viewModel.contentError(UploadTextUIState(title = "Market day", text = "We bought bread."))
    )
  }

  @Test
  fun sharedDescriptionStaysEmpty() {
    val viewModel = openViewModel()

    viewModel.setTitle("Market day")
    viewModel.setText("We bought bread.")

    assertEquals("", viewModel.mediaDescription())
  }

  @Test
  fun createItemBuildsATextStory() {
    val viewModel = openViewModel()
    val item =
        viewModel.item(
            id = "media1",
            state = UploadTextUIState(title = "Market day", text = "We bought bread."),
            authorId = "user1",
            circle = mockk<CareCircle>(),
            date = MemoryDate(year = 2024),
            shownIds = emptyList(),
        )

    val text = item as MediaItem.Text
    assertEquals("media1", text.id)
    assertEquals("user1", text.authorId)
  }

  private fun openViewModel(): OpenUploadTextViewModel {
    val authRepo = mockk<AuthRepository>()
    every { authRepo.currentUser } returns null
    return OpenUploadTextViewModel(
            mediaRepo = mockk<MediaRepository>(relaxed = true),
            authRepo = authRepo,
            profileRepo = mockk<ProfileRepository>(relaxed = true),
            circleRepo = mockk<CareCircleRepository>(relaxed = true),
            activeCircleRepo = mockk<ActiveCircleRepository>(relaxed = true),
            placeRepo = mockk<PlaceSearchRepository>(relaxed = true),
        )
        .also { viewModels += it }
  }
}

/**
 * Exposes the protected content hooks. The test is not a subclass of [UploadTextViewModel], and
 * those hooks are not public.
 */
private class OpenUploadTextViewModel(
    mediaRepo: MediaRepository,
    authRepo: AuthRepository,
    profileRepo: ProfileRepository,
    circleRepo: CareCircleRepository,
    activeCircleRepo: ActiveCircleRepository,
    placeRepo: PlaceSearchRepository,
) :
    UploadTextViewModel(
        mediaRepo = mediaRepo,
        authRepo = authRepo,
        profileRepo = profileRepo,
        circleRepo = circleRepo,
        activeCircleRepo = activeCircleRepo,
        placeRepo = placeRepo,
    ) {
  fun contentError(state: UploadTextUIState) = checkContent(state)

  fun file(state: UploadTextUIState) = fileOf(state)

  fun item(
      id: String,
      state: UploadTextUIState,
      authorId: String,
      circle: CareCircle,
      date: MemoryDate,
      shownIds: List<String>,
  ) = createItem(id, state, authorId, circle, date, shownIds)
}
