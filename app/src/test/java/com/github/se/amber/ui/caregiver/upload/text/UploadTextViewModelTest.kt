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
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.model.validation.InputValidation
import com.github.se.amber.ui.caregiver.upload.media.CommonMediaUIState
import com.github.se.amber.ui.caregiver.upload.media.MediaEvent
import com.github.se.amber.ui.caregiver.upload.media.MediaLocationUIState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Checks when a text story can be uploaded, that the title and body are stored, that content errors
 * use the text-upload string resources, and that [UploadTextViewModel.createItem] builds a text
 * story with an empty shared description.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UploadTextViewModelTest {
  private val dispatcher = UnconfinedTestDispatcher()
  private val viewModels = mutableListOf<UploadTextViewModel>()

  private val mediaRepo = mockk<MediaRepository>(relaxed = true)
  private val authRepo = mockk<AuthRepository>()
  private val profileRepo = mockk<ProfileRepository>()
  private val circleRepo = mockk<CareCircleRepository>()
  private val activeCircleRepo = mockk<ActiveCircleRepository>()
  private val placeRepo = mockk<PlaceSearchRepository>(relaxed = true)

  private val circle = mockk<CareCircle>()
  private val lausanne = GeoLocation(lat = 46.52, lng = 6.63, placeName = "Lausanne")

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    every { authRepo.currentUser } returns mockk { every { uid } returns "user1" }
    every { circle.id } returns "circle1"
    every { circle.people } returns emptyMap()
    every { circle.memberIds } returns emptyList()
    every { circle.places } returns emptyMap()
    coEvery { profileRepo.getProfile(any()) } returns
        Result.success(mockk<UserProfile> { every { circleIds } returns listOf("circle1") })
    every { activeCircleRepo.activeCircleId(any(), any()) } returns flowOf("circle1")
    coEvery { circleRepo.getCircle("circle1") } returns Result.success(circle)
    coEvery { mediaRepo.newMediaId(any()) } returns "media1"
    coEvery { mediaRepo.addMedia(any(), any(), any()) } returns Unit
    coEvery { placeRepo.search(any()) } returns Result.success(emptyList())
  }

  @After
  fun tearDown() {
    viewModels.forEach { it.viewModelScope.cancel() }
    Dispatchers.resetMain()
    unmockkAll()
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
  fun setTitleStoresTheCleanedTitle() {
    val viewModel = openViewModel()

    viewModel.setTitle("  Market day  ")

    assertEquals("  Market day  ", viewModel.uiState.value.title)
  }

  @Test
  fun setTitleCutsAtTheShortTextLimit() {
    val viewModel = openViewModel()
    val tooLong = "a".repeat(InputValidation.SHORT_TEXT_MAX_LENGTH + 5)

    viewModel.setTitle(tooLong)

    assertEquals(InputValidation.SHORT_TEXT_MAX_LENGTH, viewModel.uiState.value.title.length)
  }

  @Test
  fun setTextStoresTheCleanedBody() {
    val viewModel = openViewModel()

    viewModel.setText("We bought bread.")

    assertEquals("We bought bread.", viewModel.uiState.value.text)
  }

  @Test
  fun setTextCutsAtTheDescriptionLimit() {
    val viewModel = openViewModel()
    val tooLong = "a".repeat(InputValidation.DESCRIPTION_MAX_LENGTH + 5)

    viewModel.setText(tooLong)

    assertEquals(InputValidation.DESCRIPTION_MAX_LENGTH, viewModel.uiState.value.text.length)
  }

  @Test
  fun textStoryHasNoFile() {
    val viewModel = openViewModel()

    assertNull(viewModel.file(UploadTextUIState(title = "Market day", text = "We bought bread.")))
  }

  @Test
  fun contentCheckReportsAMissingTitle() {
    val viewModel = openViewModel()

    assertEquals(
        UploadTextViewModelErrorMessage.TITLE_EMPTY.messageRes,
        viewModel.contentError(UploadTextUIState(text = "We bought bread.")),
    )
  }

  @Test
  fun contentCheckReportsAMissingBody() {
    val viewModel = openViewModel()

    assertEquals(
        UploadTextViewModelErrorMessage.TEXT_EMPTY.messageRes,
        viewModel.contentError(UploadTextUIState(title = "Market day")),
    )
  }

  @Test
  fun contentCheckAcceptsATitleAndABody() {
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
    assertEquals("", viewModel.uiState.value.common.description)
  }

  @Test
  fun createItemBuildsATextStory() {
    val viewModel = openViewModel()
    val item =
        viewModel.item(
            id = "media1",
            state =
                UploadTextUIState(
                    title = "Market day",
                    text = "  We bought bread.  ",
                    common =
                        CommonMediaUIState(
                            location = MediaLocationUIState(pickedPlace = lausanne),
                            shownIds = setOf("person1"),
                        ),
                ),
            authorId = "user1",
            circle = circle,
            date = MemoryDate(year = 2024),
            shownIds = listOf("person1"),
        )

    val text = item as MediaItem.Text
    assertEquals("media1", text.id)
    assertEquals("user1", text.authorId)
    assertEquals("", text.description)
    assertEquals(MemoryDate(year = 2024), text.date)
    assertEquals(lausanne, text.location)
    assertEquals(listOf("person1"), text.shownIds)
    assertEquals("We bought bread.", text.text)
  }

  @Test
  fun uploadSavesTheBodyWithAnEmptySharedDescription() = runTest {
    val viewModel = openViewModel()
    viewModel.setTitle("Market day")
    viewModel.setText("We bought bread.")
    viewModel.setDate(MemoryDate(year = 2024))
    viewModel.selectPlace(lausanne)

    viewModel.uploadMedia()

    val slot = slot<MediaItem>()
    coVerify { mediaRepo.addMedia(circle, capture(slot), null) }
    val text = slot.captured as MediaItem.Text
    assertEquals("media1", text.id)
    assertEquals("user1", text.authorId)
    assertEquals("", text.description)
    assertEquals("We bought bread.", text.text)
    assertEquals(lausanne, text.location)
    assertEquals(MediaEvent.Uploaded, viewModel.events.first())
  }

  private fun openViewModel(): OpenUploadTextViewModel =
      OpenUploadTextViewModel(
              mediaRepo = mediaRepo,
              authRepo = authRepo,
              profileRepo = profileRepo,
              circleRepo = circleRepo,
              activeCircleRepo = activeCircleRepo,
              placeRepo = placeRepo,
          )
          .also { viewModels += it }
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
