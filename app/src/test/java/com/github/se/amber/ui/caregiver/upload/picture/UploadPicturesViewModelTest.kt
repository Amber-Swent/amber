// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by MeiliV, with assistance from OpenAI Codex.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
// Modified by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.upload.picture

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.location.PlaceSearchRepository
import com.github.se.amber.data.media.picture.CapturedPictureSource
import com.github.se.amber.data.media.picture.PickedImage
import com.github.se.amber.data.media.picture.SelectedPictureReader
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.location.GeoLocation
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
  private lateinit var capturedPictureSource: CapturedPictureSource
  private lateinit var placeRepo: PlaceSearchRepository

  private val circle = CareCircle(id = "circle1")
  private val pictureFile = File("picked_test.jpg") // never touched on disk, addMedia is mocked
  private val pickedImage = PickedImage(file = pictureFile, width = 640, height = 480)
  private val pictureUri = "content://pictures/123"
  private val CAPTURE_URI = "content://com.github.se.amber.fileprovider/captured_pictures/1.jpg"
  private val lausanne = GeoLocation(lat = 46.52, lng = 6.63, placeName = "Lausanne, Switzerland")
  private val MEMORY_DATE = MemoryDate(2024, Month.MAY, 12)
  private val CAPTURE_WIDTH = 1440
  private val CAPTURE_HEIGHT = 1920

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    mediaRepo = mockk(relaxed = true)
    authRepo = mockk()
    profileRepo = mockk()
    circleRepo = mockk()
    pictureReader = mockk()
    capturedPictureSource = mockk(relaxUnitFun = true)
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

  private fun createViewModel(savedState: SavedStateHandle = SavedStateHandle()) =
      UploadPictureViewModel(
          mediaRepo = mediaRepo,
          authRepo = authRepo,
          profileRepo = profileRepo,
          circleRepo = circleRepo,
          pictureReader = pictureReader,
          capturedPictureSource = capturedPictureSource,
          placeRepo = placeRepo,
          savedState = savedState,
          cleanupScope = CoroutineScope(testDispatcher),
      )

  private fun readyViewModel(): UploadPictureViewModel {
    val vm = createViewModel()
    vm.setPicture(pictureFile, 640, 480)
    vm.setDescription("A description")
    vm.setDate(MEMORY_DATE)
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
                  date = MEMORY_DATE,
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
        UploadPictureViewModelErrorMessage.COULD_NOT_READ_PICTURE.mess + ERROR_MESSAGE,
        vm.uiState.value.common.errorMsg,
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
        UploadPictureViewModelErrorMessage.SELECT_PICTURE.mess,
        vm.uiState.value.common.errorMsg,
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

  // ---------- camera ----------

  @Test
  fun onTakePictureAsksTheScreenToOpenTheCameraOnANewFile() = runTest {
    coEvery { capturedPictureSource.newCaptureUri() } returns CAPTURE_URI
    val vm = createViewModel()

    vm.onTakePicture()

    assertEquals(CAPTURE_URI, vm.cameraRequests.first())
  }

  @Test
  fun onTakePictureIgnoresASecondTapWhileTheFileIsBeingCreated() {
    val uri = CompletableDeferred<String>()
    coEvery { capturedPictureSource.newCaptureUri() } coAnswers { uri.await() }
    val vm = createViewModel()

    vm.onTakePicture()
    vm.onTakePicture()
    uri.complete(CAPTURE_URI)

    coVerify(exactly = 1) { capturedPictureSource.newCaptureUri() }
  }

  @Test
  fun onTakePictureIgnoresASecondTapUntilTheCameraAnswers() {
    coEvery { capturedPictureSource.newCaptureUri() } returns CAPTURE_URI
    coEvery { capturedPictureSource.read(any()) } returns PickedImage(pictureFile, 1, 1)
    val vm = createViewModel()

    vm.onTakePicture()
    vm.onTakePicture()
    coVerify(exactly = 1) { capturedPictureSource.newCaptureUri() }

    vm.onCaptureCancelled(CAPTURE_URI)
    vm.onTakePicture()
    coVerify(exactly = 2) { capturedPictureSource.newCaptureUri() }

    vm.onCameraUnavailable(CAPTURE_URI)
    vm.onTakePicture()
    coVerify(exactly = 3) { capturedPictureSource.newCaptureUri() }

    vm.onPictureCaptured(CAPTURE_URI)
    vm.onTakePicture()
    coVerify(exactly = 1) { capturedPictureSource.newCaptureUri(CAPTURE_URI) }
  }

  @Test
  fun aCaptureIsDeletedOnceReplacedByAnotherCaptureOrAGalleryPicture() {
    val newerCapture = "content://com.github.se.amber.fileprovider/captured_pictures/2.jpg"
    coEvery { capturedPictureSource.read(any()) } returns PickedImage(pictureFile, 1, 1)
    coEvery { pictureReader.read("gallery") } returns PickedImage(pictureFile, 1, 1)
    val vm = createViewModel()

    vm.onPictureCaptured(CAPTURE_URI)
    coVerify(exactly = 0) { capturedPictureSource.discard(any()) }

    vm.onPictureCaptured(newerCapture)
    coVerify(exactly = 1) { capturedPictureSource.discard(CAPTURE_URI) }

    vm.selectPicture("gallery")
    coVerify(exactly = 1) { capturedPictureSource.discard(newerCapture) }
  }

  @Test
  fun theFormAndTheSelectedCaptureSurviveAndroidKillingTheApp() {
    val realPicture = File.createTempFile("capture", ".jpg").apply { deleteOnExit() }
    coEvery { capturedPictureSource.read(CAPTURE_URI) } returns
        PickedImage(realPicture, CAPTURE_WIDTH, CAPTURE_HEIGHT)
    coEvery { capturedPictureSource.newCaptureUri(any()) } returns "content://next"
    val savedState = SavedStateHandle()
    val before = createViewModel(savedState)
    before.setDescription("At the lake")
    before.setDate(MEMORY_DATE)
    before.toggleShownId("user2")
    before.selectPlace(lausanne)
    before.onPictureCaptured(CAPTURE_URI)

    // a new process only gets what was saved
    val after =
        createViewModel(SavedStateHandle(savedState.keys().associateWith { savedState[it] }))

    val state = after.uiState.value
    assertEquals("At the lake", state.common.description)
    assertEquals(MEMORY_DATE, state.common.date)
    assertEquals(setOf("user2"), state.common.shownIds)
    assertEquals(lausanne, state.common.location.pickedPlace)
    assertEquals(realPicture, state.pictureFile)
    assertEquals(CAPTURE_WIDTH, state.pictureWidth)
    assertEquals(CAPTURE_HEIGHT, state.pictureHeight)
    after.onTakePicture()
    coVerify(exactly = 1) { capturedPictureSource.newCaptureUri(CAPTURE_URI) }
  }

  @Test
  fun aRestoredPictureWhoseFileIsGoneIsLeftOutButAClearedDateStaysCleared() {
    val gone = File("/no/such/picture.jpg")
    val savedState = SavedStateHandle()
    val before = createViewModel(savedState)
    before.setPicture(gone, CAPTURE_WIDTH, CAPTURE_HEIGHT)
    before.setDate(null)

    val state = createViewModel(savedState).uiState.value

    assertNull(state.pictureFile)
    assertEquals(0, state.pictureWidth)
    assertNull(state.common.date)
    assertNull(state.common.location.pickedPlace)
  }

  @Test
  fun leavingTheScreenDeletesACaptureThatWasNotUploaded() {
    coEvery { capturedPictureSource.read(any()) } returns PickedImage(pictureFile, 1, 1)
    val store = ViewModelStore()
    val factory = viewModelFactory { initializer { createViewModel() } }
    val vm = ViewModelProvider.create(store, factory)[UploadPictureViewModel::class]
    vm.onPictureCaptured(CAPTURE_URI)

    store.clear()

    coVerify(exactly = 1) { capturedPictureSource.discard(CAPTURE_URI) }
  }

  @Test
  fun anUploadedCaptureIsNotDeletedAndUploadingBlocksChangingThePicture() {
    val mayFinish = CompletableDeferred<Unit>()
    coEvery { mediaRepo.addMedia(any(), any(), any()) } coAnswers { mayFinish.await() }
    coEvery { capturedPictureSource.read(any()) } returns PickedImage(pictureFile, 1, 1)
    val store = ViewModelStore()
    val factory = viewModelFactory { initializer { readyViewModel() } }
    val vm = ViewModelProvider.create(store, factory)[UploadPictureViewModel::class]
    vm.onPictureCaptured(CAPTURE_URI)
    vm.uploadMedia()

    vm.onTakePicture()
    vm.selectPicture("gallery")
    coVerify(exactly = 0) { capturedPictureSource.newCaptureUri(any()) }
    coVerify(exactly = 0) { pictureReader.read(any()) }

    mayFinish.complete(Unit)
    store.clear()
    coVerify(exactly = 0) { capturedPictureSource.discard(any()) }
  }

  @Test
  fun onTakePictureShowsAnErrorWhenTheFileCannotBeCreated() {
    coEvery { capturedPictureSource.newCaptureUri() } throws IOException(ERROR_MESSAGE)
    val vm = createViewModel()

    vm.onTakePicture()

    assertEquals(
        UploadPictureViewModelErrorMessage.COULD_NOT_OPEN_CAMERA.mess,
        vm.uiState.value.common.errorMsg,
    )
  }

  @Test
  fun onPictureCapturedStoresThePictureAndKeepsItsFile() {
    coEvery { capturedPictureSource.read(CAPTURE_URI) } returns
        PickedImage(pictureFile, CAPTURE_WIDTH, CAPTURE_HEIGHT)
    val vm = createViewModel()

    vm.onPictureCaptured(CAPTURE_URI)

    val state = vm.uiState.value
    assertEquals(pictureFile, state.pictureFile)
    assertEquals(CAPTURE_WIDTH, state.pictureWidth)
    assertEquals(CAPTURE_HEIGHT, state.pictureHeight)
    coVerify(exactly = 0) { capturedPictureSource.discard(any()) }
  }

  @Test
  fun onPictureCapturedDiscardsAFileItCannotRead() {
    coEvery { capturedPictureSource.read(CAPTURE_URI) } throws IOException(ERROR_MESSAGE)
    val vm = createViewModel()

    vm.onPictureCaptured(CAPTURE_URI)

    assertNull(vm.uiState.value.pictureFile)
    assertEquals(
        UploadPictureViewModelErrorMessage.COULD_NOT_READ_PICTURE.mess + ERROR_MESSAGE,
        vm.uiState.value.common.errorMsg,
    )
    coVerify(exactly = 1) { capturedPictureSource.discard(CAPTURE_URI) }
  }

  @Test
  fun aCaptureThatIsNotAPhotoSaysSoKeepsTheSelectedPictureAndTheCameraCanOpenAgain() {
    coEvery { capturedPictureSource.read(CAPTURE_URI) } throws IllegalArgumentException("Video")
    coEvery { capturedPictureSource.newCaptureUri(any()) } returns CAPTURE_URI
    val vm = readyViewModel()
    vm.onTakePicture()

    vm.onPictureCaptured(CAPTURE_URI)

    assertEquals(
        UploadPictureViewModelErrorMessage.NOT_A_PHOTO.mess,
        vm.uiState.value.common.errorMsg,
    )
    assertEquals(pictureFile, vm.uiState.value.pictureFile)
    coVerify(exactly = 1) { capturedPictureSource.discard(CAPTURE_URI) }
    vm.onTakePicture()
    coVerify(exactly = 2) { capturedPictureSource.newCaptureUri(any()) }
  }

  @Test
  fun onCaptureCancelledDiscardsTheFileAndKeepsThePictureAlreadySelected() {
    val vm = readyViewModel()

    vm.onCaptureCancelled(CAPTURE_URI)

    coVerify(exactly = 1) { capturedPictureSource.discard(CAPTURE_URI) }
    assertEquals(pictureFile, vm.uiState.value.pictureFile)
    assertNull(vm.uiState.value.common.errorMsg)
  }

  @Test
  fun onCameraUnavailableShowsAnErrorAndDiscardsTheFile() {
    val vm = createViewModel()

    vm.onCameraUnavailable(CAPTURE_URI)

    assertEquals(
        UploadPictureViewModelErrorMessage.COULD_NOT_OPEN_CAMERA.mess,
        vm.uiState.value.common.errorMsg,
    )
    coVerify(exactly = 1) { capturedPictureSource.discard(CAPTURE_URI) }
  }
}
