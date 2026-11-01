// This code was written with the aid of AI
package com.github.se.amber.ui.patient.seePictures

import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.patient.seeMedias.pictures.SeePicturesUIState
import com.github.se.amber.ui.patient.seeMedias.pictures.SeePicturesViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeePicturesViewModelTest {

  private val mediaRepository = mockk<MediaRepository>()
  private val circleRepository = mockk<CareCircleRepository>()
  private val profileRepository = mockk<ProfileRepository>()

  private val profile =
      UserProfile(
          uid = "patient",
          role = Role.PATIENT,
          person = Person(),
          circleIds = listOf("circle1"),
      )
  private val circle = CareCircle(id = "circle1")

  private val picture1 = MediaItem.Picture(id = "p1", width = 400, height = 300)
  private val picture2 = MediaItem.Picture(id = "p2", width = 300, height = 400)
  private val audio = MediaItem.Audio(id = "a1")
  private val text = MediaItem.Text(id = "t1")

  @Before
  fun setUp() {
    // Unconfined: the coroutines launched in init run immediately, no need to advance time
    Dispatchers.setMain(UnconfinedTestDispatcher())
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(profile)
    coEvery { circleRepository.getCircle("circle1") } returns Result.success(circle)
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(emptyList())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel() =
      SeePicturesViewModel(mediaRepository, circleRepository, profileRepository)

  // ---------------------------------------------------------------------------------------------
  // Content / Empty
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `loads the profile into the content state`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(picture1))

    val viewModel = createViewModel()

    val state = viewModel.uiState.value as SeePicturesUIState.Content
    assertEquals(profile, state.patientProfile)
    assertNull(state.errorMsg)
  }

  @Test
  fun `loads the care circle into the content state`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(picture1))

    val viewModel = createViewModel()

    val state = viewModel.uiState.value as SeePicturesUIState.Content
    assertEquals(circle, state.careCircle)
  }

  @Test
  fun `keeps all the media and only the pictures separately`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flowOf(listOf(picture1, audio, picture2, text))

    val viewModel = createViewModel()

    val state = viewModel.uiState.value as SeePicturesUIState.Content
    assertEquals(listOf(picture1, audio, picture2, text), state.allMediaItems)
    assertEquals(listOf(picture1, picture2), state.allPictures)
  }

  @Test
  fun `state is Empty when there is no media`() {
    val viewModel = createViewModel()

    val state = viewModel.uiState.value as SeePicturesUIState.Empty
    assertEquals(profile, state.patientProfile)
    assertEquals(circle, state.careCircle)
    assertNull(state.errorMsg)
  }

  @Test
  fun `state is Empty when there is media but no picture`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(audio, text))

    val viewModel = createViewModel()

    assertTrue(viewModel.uiState.value is SeePicturesUIState.Empty)
  }

  @Test
  fun `state is Loading until the first media list arrives`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flow { awaitCancellation() }

    val viewModel = createViewModel()

    assertTrue(viewModel.uiState.value is SeePicturesUIState.Loading)
  }

  // ---------------------------------------------------------------------------------------------
  // Blocking errors
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `blocking error when no profile is found`() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(null)

    val viewModel = createViewModel()

    val state = viewModel.uiState.value as SeePicturesUIState.BlockingError
    assertEquals("No profile found.", state.message)
  }

  @Test
  fun `blocking error when the profile fails to load`() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.failure(Exception("boom"))

    val viewModel = createViewModel()

    val state = viewModel.uiState.value as SeePicturesUIState.BlockingError
    assertEquals("Failed to load the profile: boom", state.message)
  }

  @Test
  fun `blocking error when the profile has no circle`() {
    coEvery { profileRepository.getCurrentProfile() } returns
        Result.success(profile.copy(circleIds = emptyList()))

    val viewModel = createViewModel()

    val state = viewModel.uiState.value as SeePicturesUIState.BlockingError
    assertEquals("No care circle found for this patient.", state.message)
    coVerify(exactly = 0) { circleRepository.getCircle(any()) }
    coVerify(exactly = 0) { mediaRepository.observeApprovedMedia(any()) }
  }

  @Test
  fun `blocking error when the profile only has blank circle ids`() {
    coEvery { profileRepository.getCurrentProfile() } returns
        Result.success(profile.copy(circleIds = listOf("", "  ")))

    val viewModel = createViewModel()

    val state = viewModel.uiState.value as SeePicturesUIState.BlockingError
    assertEquals("No care circle found for this patient.", state.message)
  }

  @Test
  fun `blocking error when the care circle fails to load`() {
    coEvery { circleRepository.getCircle("circle1") } returns
        Result.failure(Exception("circle error"))

    val viewModel = createViewModel()

    val state = viewModel.uiState.value as SeePicturesUIState.BlockingError
    assertEquals("Failed to load the care circle: circle error", state.message)
    coVerify(exactly = 0) { mediaRepository.observeApprovedMedia(any()) }
  }

  @Test
  fun `blocking error when the media flow fails before anything is displayed`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow { throw RuntimeException("media error") }

    val viewModel = createViewModel()

    val state = viewModel.uiState.value as SeePicturesUIState.BlockingError
    assertEquals("Failed to load media: media error", state.message)
  }

  // ---------------------------------------------------------------------------------------------
  // Non-blocking errors
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `non-blocking error keeps the pictures when the media flow fails after content`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow {
          emit(listOf(picture1))
          throw RuntimeException("media error")
        }

    val viewModel = createViewModel()

    val state = viewModel.uiState.value as SeePicturesUIState.Content
    assertEquals(listOf(picture1), state.allPictures)
    assertEquals("Failed to load media: media error", state.errorMsg)
  }

  @Test
  fun `non-blocking error is attached to Empty when the media flow fails after an empty list`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow {
          emit(emptyList())
          throw RuntimeException("media error")
        }

    val viewModel = createViewModel()

    val state = viewModel.uiState.value as SeePicturesUIState.Empty
    assertEquals("Failed to load media: media error", state.errorMsg)
  }

  @Test
  fun `clearErrorMsg resets the error on Content`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow {
          emit(listOf(picture1))
          throw RuntimeException("media error")
        }
    val viewModel = createViewModel()
    assertEquals(
        "Failed to load media: media error",
        (viewModel.uiState.value as SeePicturesUIState.Content).errorMsg,
    )

    viewModel.clearErrorMsg()

    val state = viewModel.uiState.value as SeePicturesUIState.Content
    assertNull(state.errorMsg)
    assertEquals(listOf(picture1), state.allPictures)
  }

  @Test
  fun `clearErrorMsg resets the error on Empty`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow {
          emit(emptyList())
          throw RuntimeException("media error")
        }
    val viewModel = createViewModel()
    assertEquals(
        "Failed to load media: media error",
        (viewModel.uiState.value as SeePicturesUIState.Empty).errorMsg,
    )

    viewModel.clearErrorMsg()

    assertNull((viewModel.uiState.value as SeePicturesUIState.Empty).errorMsg)
  }

  @Test
  fun `clearErrorMsg does not change a blocking error`() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(null)
    val viewModel = createViewModel()

    viewModel.clearErrorMsg()

    val state = viewModel.uiState.value as SeePicturesUIState.BlockingError
    assertEquals("No profile found.", state.message)
  }

  // ---------------------------------------------------------------------------------------------
  // retry / refresh
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `retry reloads everything after a failure`() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.failure(Exception("boom"))
    val viewModel = createViewModel()
    assertTrue(viewModel.uiState.value is SeePicturesUIState.BlockingError)

    coEvery { profileRepository.getCurrentProfile() } returns Result.success(profile)
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(picture1))
    viewModel.retry()

    val state = viewModel.uiState.value as SeePicturesUIState.Content
    assertEquals(circle, state.careCircle)
    assertEquals(listOf(picture1), state.allPictures)
  }

  @Test
  fun `retry goes back to Loading while reloading`() {
    val viewModel = createViewModel()
    assertTrue(viewModel.uiState.value is SeePicturesUIState.Empty)

    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flow { awaitCancellation() }
    viewModel.retry()

    assertTrue(viewModel.uiState.value is SeePicturesUIState.Loading)
  }

  @Test
  fun `refreshUIState keeps the current content while reloading`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(picture1))
    val viewModel = createViewModel()
    assertTrue(viewModel.uiState.value is SeePicturesUIState.Content)

    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flow { awaitCancellation() }
    viewModel.refreshUIState()

    // no flash of the loading screen: the old content is still displayed
    val state = viewModel.uiState.value as SeePicturesUIState.Content
    assertEquals(listOf(picture1), state.allPictures)
  }

  @Test
  fun `refreshUIState updates the pictures with the new media`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(picture1))
    val viewModel = createViewModel()

    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flowOf(listOf(picture1, picture2))
    viewModel.refreshUIState()

    val state = viewModel.uiState.value as SeePicturesUIState.Content
    assertEquals(listOf(picture1, picture2), state.allPictures)
  }
}
