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
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.caregiver.upload.media.CommonMediaUIState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Checks the text upload state and that the title and the body are not saved yet. */
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
  fun titleAndBodyAreNotSavedYet() {
    val viewModel = viewModel()

    viewModel.setTitle("Market day")
    viewModel.setText("We bought bread.")

    assertEquals("", viewModel.uiState.value.title)
    assertEquals("", viewModel.uiState.value.text)
    assertEquals("", viewModel.mediaDescription())
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
  fun uploadSavesATextWithNoFileAndDropsTheUnsavedTitleAndBody() {
    val mediaRepo = mockk<MediaRepository>(relaxed = true)
    val circle = mockk<CareCircle>()
    every { circle.id } returns "circle1"
    every { circle.memberIds } returns emptyList()
    every { circle.people } returns emptyMap()
    every { circle.places } returns emptyMap()
    coEvery { mediaRepo.newMediaId("circle1") } returns "media1"
    val viewModel = readyViewModel(mediaRepo, circle)

    viewModel.setTitle("Market day")
    viewModel.setText("We bought bread.")
    viewModel.setDescription("A caption")
    viewModel.uploadMedia()

    val saved = slot<MediaItem>()
    coVerify { mediaRepo.addMedia(circle, capture(saved), null) }
    val text = saved.captured as MediaItem.Text
    assertEquals("media1", text.id)
    assertEquals("user1", text.authorId)
    assertEquals("", text.description)
    assertEquals("", text.text)
    assertEquals(emptyList<String>(), text.shownIds)
    assertEquals("A caption", viewModel.uiState.value.common.description)
    assertEquals("", viewModel.uiState.value.title)
    assertEquals("", viewModel.uiState.value.text)
  }

  private fun readyViewModel(mediaRepo: MediaRepository, circle: CareCircle): UploadTextViewModel {
    val authRepo = mockk<AuthRepository>()
    val profileRepo = mockk<ProfileRepository>()
    val circleRepo = mockk<CareCircleRepository>()
    val activeCircleRepo = mockk<ActiveCircleRepository>()
    every { authRepo.currentUser } returns mockk { every { uid } returns "user1" }
    coEvery { profileRepo.getProfile(any()) } returns
        Result.success(UserProfile(uid = "user1", circleIds = listOf("circle1")))
    every { activeCircleRepo.activeCircleId(any(), any()) } answers
        {
          flowOf(secondArg<List<String>>().firstOrNull { it.isNotBlank() })
        }
    coEvery { circleRepo.getCircle("circle1") } returns Result.success(circle)
    return keep(
        UploadTextViewModel(
            mediaRepo = mediaRepo,
            authRepo = authRepo,
            profileRepo = profileRepo,
            circleRepo = circleRepo,
            activeCircleRepo = activeCircleRepo,
            placeRepo = mockk<PlaceSearchRepository>(relaxed = true),
        )
    )
  }

  private fun viewModel(): UploadTextViewModel {
    val authRepo = mockk<AuthRepository>()
    every { authRepo.currentUser } returns null
    return keep(
        UploadTextViewModel(
            mediaRepo = mockk<MediaRepository>(relaxed = true),
            authRepo = authRepo,
            profileRepo = mockk<ProfileRepository>(relaxed = true),
            circleRepo = mockk<CareCircleRepository>(relaxed = true),
            activeCircleRepo = mockk<ActiveCircleRepository>(relaxed = true),
            placeRepo = mockk<PlaceSearchRepository>(relaxed = true),
        )
    )
  }

  private fun keep(viewModel: UploadTextViewModel) = viewModel.also { viewModels += it }
}
