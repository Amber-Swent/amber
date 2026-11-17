// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.patient.seeMedias.medias

import com.github.se.amber.R
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
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

private val testErrors =
    SeeGeneralMediasErrors(
        loadProfile = R.string.seePicturesErrorLoadProfile,
        noProfile = R.string.seePicturesErrorNoProfile,
        noCircle = R.string.seePicturesErrorNoCircle,
        loadCircle = R.string.seePicturesErrorLoadCircle,
        loadMedia = R.string.seePicturesErrorLoadMedia,
    )

/** Minimal subclass: keeps the pictures and records every list given to [onItemsChanged]. */
private class FakeMediasViewModel(
    mediaRepo: MediaRepository,
    circleRepo: CareCircleRepository,
    profileRepo: ProfileRepository,
) : SeeGeneralMediasViewModel<MediaItem.Picture>(mediaRepo, circleRepo, profileRepo, testErrors) {
  val receivedItems = mutableListOf<List<MediaItem.Picture>>()

  // record the UI state when the callback executes
  val statesSeenInHook = mutableListOf<SeeGeneralMediasUIState<MediaItem.Picture>>()

  init {
    load()
  }

  override fun filterMedia(media: List<MediaItem>): List<MediaItem.Picture> =
      media.filterIsInstance<MediaItem.Picture>()

  override fun onItemsChanged(items: List<MediaItem.Picture>) {
    statesSeenInHook.add(uiState.value)
    receivedItems.add(items)
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SeeGeneralMediasViewModelTest {

  private val mediaRepository = mockk<MediaRepository>()
  private val circleRepository = mockk<CareCircleRepository>()
  private val profileRepository = mockk<ProfileRepository>()
  private val testDispatcher = UnconfinedTestDispatcher()

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
    // Unconfined: the coroutines launched by load() run immediately. The scheduler is shared, so
    // the retry delay can be advanced with testDispatcher.scheduler.
    Dispatchers.setMain(testDispatcher)
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(profile)
    coEvery { circleRepository.getCircle("circle1") } returns Result.success(circle)
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(emptyList())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel() =
      FakeMediasViewModel(mediaRepository, circleRepository, profileRepository)

  private fun advanceBy(millis: Long) {
    testDispatcher.scheduler.advanceTimeBy(millis)
    testDispatcher.scheduler.runCurrent()
  }

  // ---------------------------------------------------------------------------------------------
  // Content / Empty / Loading
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `state is Loading until the first media list arrives`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flow { awaitCancellation() }

    assertTrue(createViewModel().uiState.value is SeeGeneralMediasUIState.Loading)
  }

  @Test
  fun `content holds the profile, the circle and the filtered items in order`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flowOf(listOf(picture1, audio, picture2, text))

    val state = createViewModel().uiState.value as SeeGeneralMediasUIState.Content

    assertEquals(profile, state.patientProfile)
    assertEquals(circle, state.careCircle)
    assertEquals(listOf(picture1, picture2), state.items)
    assertNull(state.errorMsgRes)
  }

  @Test
  fun `state is Empty when there is no media`() {
    val state = createViewModel().uiState.value as SeeGeneralMediasUIState.Empty

    assertEquals(profile, state.patientProfile)
    assertEquals(circle, state.careCircle)
    assertNull(state.errorMsgRes)
  }

  @Test
  fun `state is Empty when no media matches the filter`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(audio, text))

    assertTrue(createViewModel().uiState.value is SeeGeneralMediasUIState.Empty)
  }

  @Test
  fun `media updates are reflected live`() {
    val media = MutableStateFlow<List<MediaItem>>(emptyList())
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns media
    val viewModel = createViewModel()
    assertTrue(viewModel.uiState.value is SeeGeneralMediasUIState.Empty)

    media.value = listOf(picture1, audio)
    assertEquals(
        listOf(picture1),
        (viewModel.uiState.value as SeeGeneralMediasUIState.Content).items,
    )

    media.value = listOf(picture2, picture1)
    assertEquals(
        listOf(picture2, picture1),
        (viewModel.uiState.value as SeeGeneralMediasUIState.Content).items,
    )

    media.value = listOf(audio)
    assertTrue(viewModel.uiState.value is SeeGeneralMediasUIState.Empty)
  }

  @Test
  fun `onItemsChanged receives each filtered list, including empty ones`() {
    val media = MutableStateFlow<List<MediaItem>>(listOf(picture1, audio))
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns media
    val viewModel = createViewModel()

    media.value = listOf(audio)
    media.value = listOf(picture1, picture2, text)

    assertEquals(
        listOf(listOf(picture1), emptyList(), listOf(picture1, picture2)),
        viewModel.receivedItems,
    )
  }

  @Test
  fun `onItemsChanged executes before publishing the new UI state`() {
    val media = MutableStateFlow<List<MediaItem>>(listOf(picture1))
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns media

    val viewModel = createViewModel()

    // Initially, picture1 is displayed
    assertEquals(
        listOf(picture1),
        (viewModel.uiState.value as SeeGeneralMediasUIState.Content).items,
    )

    // Repository publishes a different picture
    media.value = listOf(picture2)

    // During the callback, the oldstate should still be visible
    val stateDuringHook = viewModel.statesSeenInHook.last() as SeeGeneralMediasUIState.Content

    assertEquals(listOf(picture1), stateDuringHook.items)

    // After the callback, the newstate should be published
    val finalState = viewModel.uiState.value as SeeGeneralMediasUIState.Content

    assertEquals(listOf(picture2), finalState.items)
  }

  // ---------------------------------------------------------------------------------------------
  // Blocking errors
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `blocking error when no profile is found`() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(null)

    val state = createViewModel().uiState.value as SeeGeneralMediasUIState.BlockingError

    assertEquals(testErrors.noProfile, state.errorMsgRes)
  }

  @Test
  fun `blocking error when the profile fails to load`() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.failure(Exception("boom"))

    val state = createViewModel().uiState.value as SeeGeneralMediasUIState.BlockingError

    assertEquals(testErrors.loadProfile, state.errorMsgRes)
  }

  @Test
  fun `blocking error when the profile only has blank circle ids`() {
    coEvery { profileRepository.getCurrentProfile() } returns
        Result.success(profile.copy(circleIds = listOf("", "  ")))

    val state = createViewModel().uiState.value as SeeGeneralMediasUIState.BlockingError

    assertEquals(testErrors.noCircle, state.errorMsgRes)
    coVerify(exactly = 0) { circleRepository.getCircle(any()) }
    coVerify(exactly = 0) { mediaRepository.observeApprovedMedia(any()) }
  }

  @Test
  fun `blocking error when the care circle fails to load`() {
    coEvery { circleRepository.getCircle("circle1") } returns Result.failure(Exception("boom"))

    val state = createViewModel().uiState.value as SeeGeneralMediasUIState.BlockingError

    assertEquals(testErrors.loadCircle, state.errorMsgRes)
    coVerify(exactly = 0) { mediaRepository.observeApprovedMedia(any()) }
  }

  @Test
  fun `blocking error when the media flow fails before anything is displayed`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow { throw RuntimeException("offline") }

    val state = createViewModel().uiState.value as SeeGeneralMediasUIState.BlockingError

    assertEquals(testErrors.loadMedia, state.errorMsgRes)
  }

  @Test
  fun `uses the first non-blank circle id`() {
    coEvery { profileRepository.getCurrentProfile() } returns
        Result.success(profile.copy(circleIds = listOf("", "circle1")))

    assertTrue(createViewModel().uiState.value is SeeGeneralMediasUIState.Empty)
    coVerify(exactly = 1) { circleRepository.getCircle("circle1") }
    coVerify(exactly = 1) { mediaRepository.observeApprovedMedia("circle1") }
  }

  // ---------------------------------------------------------------------------------------------
  // Non-blocking errors
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `media failure after content keeps the items until the error is cleared`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow {
          emit(listOf(picture1))
          throw RuntimeException("offline")
        }
    val viewModel = createViewModel()

    val withError = viewModel.uiState.value as SeeGeneralMediasUIState.Content
    assertEquals(listOf(picture1), withError.items)
    assertEquals(testErrors.loadMedia, withError.errorMsgRes)

    viewModel.clearErrorMsg()

    val cleared = viewModel.uiState.value as SeeGeneralMediasUIState.Content
    assertEquals(listOf(picture1), cleared.items)
    assertNull(cleared.errorMsgRes)
  }

  @Test
  fun `media failure after an empty list keeps Empty until the error is cleared`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow {
          emit(emptyList())
          throw RuntimeException("offline")
        }
    val viewModel = createViewModel()
    assertEquals(
        testErrors.loadMedia,
        (viewModel.uiState.value as SeeGeneralMediasUIState.Empty).errorMsgRes,
    )

    viewModel.clearErrorMsg()

    assertNull((viewModel.uiState.value as SeeGeneralMediasUIState.Empty).errorMsgRes)
  }

  @Test
  fun `clearErrorMsg does not change a blocking error`() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(null)
    val viewModel = createViewModel()

    viewModel.clearErrorMsg()

    val state = viewModel.uiState.value as SeeGeneralMediasUIState.BlockingError
    assertEquals(testErrors.noProfile, state.errorMsgRes)
  }

  // ---------------------------------------------------------------------------------------------
  // Automatic reconnection (retryWhen)
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `media flow reconnects after 3 seconds and clears the error`() {
    var subscriptions = 0
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow {
          subscriptions++
          if (subscriptions == 1) {
            emit(listOf(picture1))
            throw IOException("offline")
          }
          emit(listOf(picture1, picture2))
          awaitCancellation()
        }
    val viewModel = createViewModel()
    assertEquals(
        testErrors.loadMedia,
        (viewModel.uiState.value as SeeGeneralMediasUIState.Content).errorMsgRes,
    )

    advanceBy(2_999L)
    assertEquals(1, subscriptions)

    advanceBy(1L)
    assertEquals(2, subscriptions)
    val state = viewModel.uiState.value as SeeGeneralMediasUIState.Content
    assertEquals(listOf(picture1, picture2), state.items)
    assertNull(state.errorMsgRes)
  }

  @Test
  fun `media flow recovers after a failure before the first emission`() {
    var subscriptions = 0
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow {
          if (++subscriptions == 1) throw IOException("offline")
          emit(listOf(picture1))
          awaitCancellation()
        }
    val viewModel = createViewModel()
    assertTrue(viewModel.uiState.value is SeeGeneralMediasUIState.BlockingError)

    advanceBy(3_000L)

    assertEquals(
        listOf(picture1),
        (viewModel.uiState.value as SeeGeneralMediasUIState.Content).items,
    )
    assertEquals(2, subscriptions)
  }

  @Test
  fun `repeated media failures show the error only once`() {
    var subscriptions = 0
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow {
          subscriptions++
          if (subscriptions == 1) emit(listOf(picture1))
          if (subscriptions < 3) throw IOException("offline")
          emit(listOf(picture1, picture2))
          awaitCancellation()
        }
    val viewModel = createViewModel()
    viewModel.clearErrorMsg()

    advanceBy(3_000L) // second subscription fails again
    val stillWaiting = viewModel.uiState.value as SeeGeneralMediasUIState.Content
    assertEquals(listOf(picture1), stillWaiting.items)
    assertNull(stillWaiting.errorMsgRes)

    advanceBy(3_000L)
    assertEquals(
        listOf(picture1, picture2),
        (viewModel.uiState.value as SeeGeneralMediasUIState.Content).items,
    )
    assertEquals(3, subscriptions)
  }

  // ---------------------------------------------------------------------------------------------
  // retry / refreshUIState
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `retry reloads everything after a blocking error`() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.failure(Exception("boom"))
    val viewModel = createViewModel()
    assertTrue(viewModel.uiState.value is SeeGeneralMediasUIState.BlockingError)

    coEvery { profileRepository.getCurrentProfile() } returns Result.success(profile)
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(picture1))
    viewModel.retry()

    assertEquals(
        listOf(picture1),
        (viewModel.uiState.value as SeeGeneralMediasUIState.Content).items,
    )
  }

  @Test
  fun `retry shows Loading and stops observing the previous media flow`() {
    val oldMedia = MutableStateFlow<List<MediaItem>>(listOf(picture1))
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns oldMedia
    val viewModel = createViewModel()

    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flow { awaitCancellation() }
    viewModel.retry()
    oldMedia.value = listOf(picture1, picture2) // must be ignored

    assertTrue(viewModel.uiState.value is SeeGeneralMediasUIState.Loading)
  }

  @Test
  fun `refreshUIState keeps the current content while reloading`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(picture1))
    val viewModel = createViewModel()

    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flow { awaitCancellation() }
    viewModel.refreshUIState()

    // No flash of the loading screen
    assertEquals(
        listOf(picture1),
        (viewModel.uiState.value as SeeGeneralMediasUIState.Content).items,
    )
  }

  @Test
  fun `refreshUIState picks up the new media`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(picture1))
    val viewModel = createViewModel()

    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flowOf(listOf(picture1, picture2))
    viewModel.refreshUIState()

    assertEquals(
        listOf(picture1, picture2),
        (viewModel.uiState.value as SeeGeneralMediasUIState.Content).items,
    )
  }
}
