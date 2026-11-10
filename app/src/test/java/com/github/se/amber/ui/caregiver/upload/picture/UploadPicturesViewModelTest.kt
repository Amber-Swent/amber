// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by MeiliV, with assistance from OpenAI Codex.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code
// Modified by LaMaier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.upload.picture

import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.location.PlaceSearchRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MediaStatus
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.caregiver.upload.media.CommonMediaUIState
import com.github.se.amber.ui.caregiver.upload.pictures.UploadPictureUIState
import com.github.se.amber.ui.caregiver.upload.pictures.UploadPictureViewModel
import com.github.se.amber.ui.caregiver.upload.pictures.UploadPictureViewModelErrorMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.Month
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for what is specific to [UploadPictureViewModel]: the picture, and the item made from
 * it. What all upload ViewModels share (circle, place, people, upload flow) is tested in
 * [com.github.se.amber.ui.caregiver.upload.media.MediaViewModelTest]. All repositories are mocked,
 * nothing touches the network. The care circle is loaded in `init`, so each test configures the
 * repositories BEFORE calling [createViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UploadPictureViewModelTest {
  private val testDispatcher = UnconfinedTestDispatcher()
  private val ERROR_MESSAGE = "Boom"

  private lateinit var mediaRepo: MediaRepository
  private lateinit var authRepo: AuthRepository
  private lateinit var profileRepo: ProfileRepository
  private lateinit var circleRepo: CareCircleRepository
  private lateinit var pictureReader: SelectedPictureReader
  private lateinit var placeRepo: PlaceSearchRepository

  private val circle = CareCircle(id = "circle1")
  private val pictureFile = File("picked_test.jpg") // never touched on disk, addMedia is mocked
  private val pickedImage = PickedImage(file = pictureFile, width = 640, height = 480)
  private val pictureUri = "content://pictures/123"

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    mediaRepo = mockk(relaxed = true)
    authRepo = mockk()
    profileRepo = mockk()
    circleRepo = mockk()
    pictureReader = mockk()
    placeRepo = mockk()

    every { authRepo.currentUser } returns mockk { every { uid } returns "user1" }
    coEvery { profileRepo.getProfile(any()) } returns
        Result.success(mockk<UserProfile> { every { circleIds } returns listOf("circle1") })
    coEvery { circleRepo.getCircle("circle1") } returns Result.success(circle)
    coEvery { mediaRepo.newMediaId(any()) } returns "media1"
    coEvery { placeRepo.search(any()) } returns Result.success(emptyList())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkAll()
  }

  // ---------- helpers ----------

  private fun createViewModel() =
      UploadPictureViewModel(
          mediaRepo = mediaRepo,
          authRepo = authRepo,
          profileRepo = profileRepo,
          circleRepo = circleRepo,
          pictureReader = pictureReader,
          placeRepo = placeRepo,
      )

  private fun readyViewModel(): UploadPictureViewModel {
    val vm = createViewModel()
    vm.setPicture(pictureFile, 640, 480)
    vm.setDescription("A description")
    vm.setDate(MemoryDate(2024, Month.MAY, 12))
    return vm
  }

  private fun captureUploadedItem(): MediaItem.Picture {
    val slot = slot<MediaItem>()
    coVerify { mediaRepo.addMedia(any(), capture(slot), any()) }
    return slot.captured as MediaItem.Picture
  }

  private val completeState =
      UploadPictureUIState(
          common =
              CommonMediaUIState(
                  description = "A description",
                  date = MemoryDate(2024, Month.MAY, 12),
              ),
          pictureFile = File("picture.jpg"),
      )

  // ---------- initial state ----------

  // NEW TEST
  @Test
  fun initialStateAsNullFile_andCurrentYear() {
    assertNull(createViewModel().uiState.value.pictureFile)
    assertEquals(MemoryDate(LocalDate.now().year), createViewModel().uiState.value.common.date)
  }

  // ---------- setPicture ----------

  @Test
  fun setPictureStoresPicture() {
    val vm = createViewModel()
    vm.setPicture(pictureFile, 640, 480)
    assertSame(pictureFile, vm.uiState.value.pictureFile)
    assertEquals(640, vm.uiState.value.pictureWidth)
    assertEquals(480, vm.uiState.value.pictureHeight)
  }

  // ---------- selectPicture ----------

  @Test
  fun selectPictureRequestsSelectedUriFromReader() {
    coEvery { pictureReader.read(pictureUri) } returns pickedImage

    createViewModel().selectPicture(pictureUri)

    coVerify(exactly = 1) { pictureReader.read(pictureUri) }
  }

  @Test
  fun selectPictureStoresPictures() {
    coEvery { pictureReader.read(pictureUri) } returns pickedImage
    val vm = createViewModel()
    vm.selectPicture(pictureUri)
    assertSame(pictureFile, vm.uiState.value.pictureFile)
    assertEquals(640, vm.uiState.value.pictureWidth)
    assertEquals(480, vm.uiState.value.pictureHeight)
  }

  @Test
  fun selectPictureFailureSetsReadError() {
    coEvery { pictureReader.read(pictureUri) } throws IOException(ERROR_MESSAGE)
    val vm = createViewModel()

    vm.selectPicture(pictureUri)

    assertEquals(
        UploadPictureViewModelErrorMessage.COULD_NOT_READ_PICTURE.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
  }

  // NEW TEST
  @Test
  fun selectPictureFailureKeepsThePictureEmpty() {
    coEvery { pictureReader.read(pictureUri) } throws IOException(ERROR_MESSAGE)
    val vm = createViewModel()
    vm.selectPicture(pictureUri)
    assertNull(vm.uiState.value.pictureFile)
  }

  // ---------- upload: the picture is required ----------

  @Test
  fun uploadWithoutPictureAsksToSelectOne() {
    val vm = createViewModel()
    vm.uploadMedia()
    assertEquals(
        UploadPictureViewModelErrorMessage.SELECT_PICTURE.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
  }

  @Test
  fun uploadWithoutPictureNeverCallsRepository() {
    createViewModel().uploadMedia()
    coVerify(exactly = 0) { mediaRepo.addMedia(any(), any(), any()) }
  }

  // ---------- upload: the item made from the picture ----------

  @Test
  fun successfulUploadSendsTheSelectedFile() {
    readyViewModel().uploadMedia()
    coVerify { mediaRepo.addMedia(any(), any(), pictureFile) }
  }

  @Test
  fun uploadedItemHasCircleScopedStoragePath() {
    readyViewModel().uploadMedia()
    assertEquals("careCircles/circle1/media/media1.jpg", captureUploadedItem().storagePath)
  }

  @Test
  fun uploadedItemHasPictureWidth_andHeight() {
    readyViewModel().uploadMedia()
    assertEquals(640, captureUploadedItem().width)
    assertEquals(480, captureUploadedItem().height)
  }

  @Test
  fun uploadedItemIsApproved_andIsVisibleToWholeCircle() {
    readyViewModel().uploadMedia()
    assertEquals(MediaStatus.APPROVED, captureUploadedItem().status)
    assertTrue(captureUploadedItem().authorizedIds.isEmpty())
  }

  @Test
  fun initialYearOnlyDateAllowsUploadAfterPictureAndDescription() {
    val vm = createViewModel()
    vm.setPicture(pictureFile, 640, 480)
    vm.setDescription("A description")
    assertTrue(vm.uiState.value.canUpload)
  }

  // ---------- canUpload ----------

  @Test
  fun canUploadIsTrueWhenFormIsComplete() {
    assertTrue(completeState.canUpload)
  }

  @Test
  fun canUploadIsFalseWithoutPicture() {
    assertFalse(completeState.copy(pictureFile = null).canUpload)
  }

  // NEW TEST
  @Test
  fun canUploadIsFalseDuringAnUploadInProgress_andTrueWhenComplete() {
    val uploadReply = CompletableDeferred<Unit>()
    coEvery { mediaRepo.addMedia(any(), any(), any()) } coAnswers { uploadReply.await() }

    val vm = readyViewModel()
    vm.uploadMedia()
    assertFalse(vm.uiState.value.canUpload)

    uploadReply.complete(Unit)
    assertTrue(vm.uiState.value.canUpload)
  }

  // ---------- withCommon ----------

  @Test
  fun withCommonPreservesPictureSpecificFields() {
    val state =
        completeState.copy(
            pictureWidth = 640,
            pictureHeight = 480,
        )

    val updated = state.withCommon(state.common.copy(description = "Updated description"))

    assertEquals("Updated description", updated.common.description)

    assertEquals(state.pictureFile, updated.pictureFile)
    assertEquals(state.pictureWidth, updated.pictureWidth)
    assertEquals(state.pictureHeight, updated.pictureHeight)
  }

  // NEW TEST
  @Test
  fun withCommonReplacesTheCommonFields() {
    val common = CommonMediaUIState(description = "New description", isUploading = true)
    assertEquals(common, completeState.withCommon(common).common)
  }

  // NEW TEST
  @Test
  fun canUploadNeedsTheCommonFieldsToo() {
    assertFalse(completeState.withCommon(CommonMediaUIState()).canUpload)
  }
}
