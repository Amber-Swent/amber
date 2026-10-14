/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload

import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MediaStatus
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.UserProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.File
import java.io.IOException
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
 * Unit tests for [UploadPictureViewModel]. All repositories are mocked, nothing touches the
 * network. The care circle is loaded in `init`, so each test configures the repositories BEFORE
 * calling [createViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UploadPictureViewModelTest {

  private val dispatcher = UnconfinedTestDispatcher()
  private lateinit var mediaRepo: MediaRepository
  private lateinit var authRepo: AuthRepository
  private lateinit var profileRepo: ProfileRepository
  private lateinit var circleRepo: CareCircleRepository
  private val pictureFile = File("picture.jpg")
  private lateinit var circle: CareCircle

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    mediaRepo = mockk(relaxed = true)
    authRepo = mockk()
    profileRepo = mockk()
    circleRepo = mockk()
    circle = fakeCircle()
    signIn("user1")
    stubProfile(listOf("circle1"))
    coEvery { circleRepo.getCircle("circle1") } returns Result.success(circle)
    coEvery { mediaRepo.newMediaId(any()) } returns "media1"
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // ---------- helpers ----------

  private fun signIn(userId: String?) {
    if (userId == null) {
      every { authRepo.currentUser } returns null
    } else {
      every { authRepo.currentUser } returns mockk { every { uid } returns userId }
    }
  }

  private fun stubProfile(ids: List<String>?) {
    val profile: UserProfile? = if (ids == null) null else mockk { every { circleIds } returns ids }
    coEvery { profileRepo.getProfile(any()) } returns Result.success(profile)
  }

  private fun createViewModel() =
      UploadPictureViewModel(mediaRepo, authRepo, profileRepo, circleRepo)

  private fun fakeCircle(
      circleId: String = "circle1",
      people: Map<String, Person> = emptyMap(),
      members: List<String> = emptyList(),
  ): CareCircle = mockk {
    every { id } returns circleId
    every { this@mockk.people } returns people
    every { memberIds } returns members
  }

  private fun fakePerson(nick: String?, first: String, last: String): Person = mockk {
    every { nickname } returns nick
    every { firstName } returns first
    every { lastName } returns last
  }

  private fun readyViewModel(): UploadPictureViewModel =
      createViewModel().also { it.setPicture(pictureFile, 640, 480) }

  private fun captureUploadedItem(): MediaItem.Picture {
    val slot = slot<MediaItem>()
    coVerify { mediaRepo.addMedia(any(), capture(slot), any()) }
    return slot.captured as MediaItem.Picture
  }

  // ---------- initial state ----------

  @Test
  fun initialDescriptionIsEmpty() {
    assertEquals("", createViewModel().uiState.value.description)
  }

  @Test
  fun initialPictureIsNull() {
    assertNull(createViewModel().uiState.value.pictureFile)
  }

  @Test
  fun initialStateIsNotUploading() {
    assertFalse(createViewModel().uiState.value.isUploading)
  }

  @Test
  fun initialStateIsNotUploaded() {
    assertFalse(createViewModel().uiState.value.isUploaded)
  }

  @Test
  fun initialStateHasNoErrorWhenCircleLoads() {
    assertNull(createViewModel().uiState.value.errorMsg)
  }

  // ---------- circle loading (init) ----------

  @Test
  fun circleIsLoadedAtInit() {
    assertSame(circle, createViewModel().careCircle.value)
  }

  @Test
  fun profileIsRequestedForTheCurrentUser() {
    createViewModel()
    coVerify(exactly = 1) { profileRepo.getProfile("user1") }
  }

  @Test
  fun firstCircleOfTheProfileIsRequested() {
    stubProfile(listOf("circle1", "circle2"))
    createViewModel()
    coVerify(exactly = 1) { circleRepo.getCircle("circle1") }
  }

  @Test
  fun otherCirclesOfTheProfileAreNotRequested() {
    stubProfile(listOf("circle1", "circle2"))
    createViewModel()
    coVerify(exactly = 0) { circleRepo.getCircle("circle2") }
  }

  @Test
  fun missingUserSetsIdentityError() {
    signIn(null)
    assertEquals(
        UploadPictureViewModelErrorMessage.UNVERIFIED.mess,
        createViewModel().uiState.value.errorMsg,
    )
  }

  @Test
  fun missingUserNeverRequestsTheProfile() {
    signIn(null)
    createViewModel()
    coVerify(exactly = 0) { profileRepo.getProfile(any()) }
  }

  @Test
  fun missingUserLeavesCircleNull() {
    signIn(null)
    assertNull(createViewModel().careCircle.value)
  }

  @Test
  fun profileFailureSetsProfileError() {
    coEvery { profileRepo.getProfile(any()) } returns Result.failure(IOException("boom"))
    assertEquals(
        UploadPictureViewModelErrorMessage.COULD_NOT_LOAD_PROFILE_ID.mess + "boom",
        createViewModel().uiState.value.errorMsg,
    )
  }

  @Test
  fun profileFailureNeverRequestsTheCircle() {
    coEvery { profileRepo.getProfile(any()) } returns Result.failure(IOException("boom"))
    createViewModel()
    coVerify(exactly = 0) { circleRepo.getCircle(any()) }
  }

  @Test
  fun missingProfileSetsCircleNotSetUpError() {
    stubProfile(null)
    assertEquals(
        UploadPictureViewModelErrorMessage.NOT_SET_CARE_CIRCLE.mess,
        createViewModel().uiState.value.errorMsg,
    )
  }

  @Test
  fun profileWithoutCircleIdSetsCircleNotSetUpError() {
    stubProfile(emptyList())
    assertEquals(
        UploadPictureViewModelErrorMessage.NOT_SET_CARE_CIRCLE.mess,
        createViewModel().uiState.value.errorMsg,
    )
  }

  @Test
  fun profileWithoutCircleIdNeverRequestsTheCircle() {
    stubProfile(emptyList())
    createViewModel()
    coVerify(exactly = 0) { circleRepo.getCircle(any()) }
  }

  @Test
  fun circleFailureSetsCircleError() {
    coEvery { circleRepo.getCircle(any()) } returns Result.failure(IOException("boom"))
    assertEquals(
        UploadPictureViewModelErrorMessage.COULD_NOT_LOAD_CARE_CIRCLE.mess + "boom",
        createViewModel().uiState.value.errorMsg,
    )
  }

  @Test
  fun circleFailureLeavesCircleNull() {
    coEvery { circleRepo.getCircle(any()) } returns Result.failure(IOException("boom"))
    assertNull(createViewModel().careCircle.value)
  }

  // ---------- simple setters ----------

  @Test
  fun setDescriptionUpdatesState() {
    val vm = createViewModel()
    vm.setDescription("Grandma's birthday")
    assertEquals("Grandma's birthday", vm.uiState.value.description)
  }

  @Test
  fun setDateUpdatesState() {
    val vm = createViewModel()
    val date = MemoryDate(2026, 10, 14)
    vm.setDate(date)
    assertEquals(date, vm.uiState.value.date)
  }

  @Test
  fun setPictureStoresFile() {
    val vm = createViewModel()
    vm.setPicture(pictureFile, 640, 480)
    assertSame(pictureFile, vm.uiState.value.pictureFile)
  }

  @Test
  fun setPictureStoresWidth() {
    val vm = createViewModel()
    vm.setPicture(pictureFile, 640, 480)
    assertEquals(640, vm.uiState.value.pictureWidth)
  }

  @Test
  fun setPictureStoresHeight() {
    val vm = createViewModel()
    vm.setPicture(pictureFile, 640, 480)
    assertEquals(480, vm.uiState.value.pictureHeight)
  }

  @Test
  fun setErrorMsgUpdatesState() {
    val vm = createViewModel()
    vm.setErrorMsg("oops")
    assertEquals("oops", vm.uiState.value.errorMsg)
  }

  @Test
  fun clearErrorMsgResetsError() {
    val vm = createViewModel()
    vm.setErrorMsg("oops")
    vm.clearErrorMsg()
    assertNull(vm.uiState.value.errorMsg)
  }

  // ---------- toggleShownId ----------

  @Test
  fun toggleShownIdAddsUnknownId() {
    val vm = createViewModel()
    vm.toggleShownId("new_id")
    assertTrue("new_id" in vm.uiState.value.shownIds)
  }

  @Test
  fun toggleShownIdRemovesExistingId() {
    val vm = createViewModel()
    vm.toggleShownId("new_id")
    vm.toggleShownId("new_id")
    assertFalse("new_id" in vm.uiState.value.shownIds)
  }

  // ---------- involvedOptions ----------

  @Test
  fun involvedOptionsIsEmptyWhenCircleFailsToLoad() {
    coEvery { circleRepo.getCircle(any()) } returns Result.failure(IOException("boom"))
    assertTrue(createViewModel().involvedOptions.value.isEmpty())
  }

  @Test
  fun involvedOptionsUsesNicknameWhenPresent() {
    val person = fakePerson("Nana", "Anne", "Dupont")
    coEvery { circleRepo.getCircle("circle1") } returns
        Result.success(fakeCircle(people = mapOf("p1" to person)))
    assertEquals("Nana", createViewModel().involvedOptions.value["p1"])
  }

  @Test
  fun involvedOptionsFallsBackToFullNameWhenNicknameBlank() {
    val person = fakePerson("  ", "Anne", "Dupont")
    coEvery { circleRepo.getCircle("circle1") } returns
        Result.success(fakeCircle(people = mapOf("p1" to person)))
    assertEquals("Anne Dupont", createViewModel().involvedOptions.value["p1"])
  }

  @Test
  fun involvedOptionsFallsBackToFullNameWhenNicknameNull() {
    val person = fakePerson(null, "Anne", "Dupont")
    coEvery { circleRepo.getCircle("circle1") } returns
        Result.success(fakeCircle(people = mapOf("p1" to person)))
    assertEquals("Anne Dupont", createViewModel().involvedOptions.value["p1"])
  }

  @Test
  fun involvedOptionsSkipsBlankLastName() {
    val person = fakePerson(null, "Anne", "")
    coEvery { circleRepo.getCircle("circle1") } returns
        Result.success(fakeCircle(people = mapOf("p1" to person)))
    assertEquals("Anne", createViewModel().involvedOptions.value["p1"])
  }

  @Test
  fun involvedOptionsLabelsMembersWithTheirOwnId() {
    coEvery { circleRepo.getCircle("circle1") } returns
        Result.success(fakeCircle(members = listOf("member1")))
    assertEquals("member1", createViewModel().involvedOptions.value["member1"])
  }

  // ---------- uploadPicture: guards ----------

  @Test
  fun uploadWithoutUserSetsIdentityError() {
    val vm = readyViewModel()
    signIn(null)
    vm.uploadPicture()
    assertEquals(UploadPictureViewModelErrorMessage.UNVERIFIED.mess, vm.uiState.value.errorMsg)
  }

  @Test
  fun uploadWithoutLoadedCircleAsksToRetry() {
    coEvery { circleRepo.getCircle(any()) } returns Result.failure(IOException("boom"))
    val vm = readyViewModel()
    vm.uploadPicture()
    assertEquals("Your care circle is still loading, try again", vm.uiState.value.errorMsg)
  }

  @Test
  fun uploadWithoutPictureAsksToSelectOne() {
    val vm = createViewModel()
    vm.uploadPicture()
    assertEquals("Please select a picture first", vm.uiState.value.errorMsg)
  }

  @Test
  fun uploadWithoutPictureNeverCallsRepository() {
    createViewModel().uploadPicture()
    coVerify(exactly = 0) { mediaRepo.addMedia(any(), any(), any()) }
  }

  // ---------- uploadPicture: success ----------

  @Test
  fun successfulUploadSetsUploadedFlag() {
    val vm = readyViewModel()
    vm.uploadPicture()
    assertTrue(vm.uiState.value.isUploaded)
  }

  @Test
  fun successfulUploadClearsUploadingFlag() {
    val vm = readyViewModel()
    vm.uploadPicture()
    assertFalse(vm.uiState.value.isUploading)
  }

  @Test
  fun successfulUploadHasNoError() {
    val vm = readyViewModel()
    vm.uploadPicture()
    assertNull(vm.uiState.value.errorMsg)
  }

  @Test
  fun successfulUploadCallsRepositoryOnce() {
    readyViewModel().uploadPicture()
    coVerify(exactly = 1) { mediaRepo.addMedia(any(), any(), any()) }
  }

  @Test
  fun successfulUploadSendsTheLoadedCircle() {
    readyViewModel().uploadPicture()
    coVerify { mediaRepo.addMedia(circle, any(), any()) }
  }

  @Test
  fun successfulUploadSendsTheSelectedFile() {
    readyViewModel().uploadPicture()
    coVerify { mediaRepo.addMedia(any(), any(), pictureFile) }
  }

  @Test
  fun uploadedItemHasCircleScopedStoragePath() {
    readyViewModel().uploadPicture()
    assertEquals("careCircles/circle1/media/media1.jpg", captureUploadedItem().storagePath)
  }

  @Test
  fun uploadedItemHasAuthorId() {
    readyViewModel().uploadPicture()
    assertEquals("user1", captureUploadedItem().authorId)
  }

  @Test
  fun uploadedItemHasDescription() {
    val vm = readyViewModel()
    vm.setDescription("Beach day")
    vm.uploadPicture()
    assertEquals("Beach day", captureUploadedItem().description)
  }

  @Test
  fun uploadedItemHasTaggedPeople() {
    val vm = readyViewModel()
    vm.toggleShownId("tagged")
    vm.uploadPicture()
    assertTrue("tagged" in captureUploadedItem().shownIds)
  }

  @Test
  fun uploadedItemHasPictureWidth() {
    readyViewModel().uploadPicture()
    assertEquals(640, captureUploadedItem().width)
  }

  @Test
  fun uploadedItemHasPictureHeight() {
    readyViewModel().uploadPicture()
    assertEquals(480, captureUploadedItem().height)
  }

  @Test
  fun uploadedItemIsApproved() {
    readyViewModel().uploadPicture()
    assertEquals(MediaStatus.APPROVED, captureUploadedItem().status)
  }

  @Test
  fun uploadedItemIsVisibleToWholeCircle() {
    readyViewModel().uploadPicture()
    assertTrue(captureUploadedItem().authorizedIds.isEmpty())
  }

  // ---------- uploadPicture: failure ----------

  @Test
  fun failedUploadReportsErrorMessage() {
    coEvery { mediaRepo.addMedia(any(), any(), any()) } throws IOException("boom")
    val vm = readyViewModel()
    vm.uploadPicture()
    assertEquals("Upload failed: boom", vm.uiState.value.errorMsg)
  }

  @Test
  fun failedUploadWithoutMessageReportsUnknownError() {
    coEvery { mediaRepo.addMedia(any(), any(), any()) } throws IOException()
    val vm = readyViewModel()
    vm.uploadPicture()
    assertEquals(UploadPictureViewModelErrorMessage.UNKNOW_ERROR.mess, vm.uiState.value.errorMsg)
  }

  @Test
  fun failedUploadClearsUploadingFlag() {
    coEvery { mediaRepo.addMedia(any(), any(), any()) } throws IOException("boom")
    val vm = readyViewModel()
    vm.uploadPicture()
    assertFalse(vm.uiState.value.isUploading)
  }

  @Test
  fun failedUploadDoesNotSetUploadedFlag() {
    coEvery { mediaRepo.addMedia(any(), any(), any()) } throws IOException("boom")
    val vm = readyViewModel()
    vm.uploadPicture()
    assertFalse(vm.uiState.value.isUploaded)
  }
}
