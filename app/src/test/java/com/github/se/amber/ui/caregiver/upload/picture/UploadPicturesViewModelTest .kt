/* Written by LaMaier, with assistance from
 * Claude (Anthropic) via Claude Code.
 * Modified by MeiliV, with assistance from OpenAI Codex.
 * Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload.picture

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
import io.mockk.unmockkAll
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.Month
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
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
  private val testDispatcher = UnconfinedTestDispatcher()
  private val ERROR_MESSAGE = "Boom"

  private lateinit var mediaRepo: MediaRepository
  private lateinit var authRepo: AuthRepository
  private lateinit var profileRepo: ProfileRepository
  private lateinit var circleRepo: CareCircleRepository
  private lateinit var pictureReader: SelectedPictureReader

  private lateinit var circle: CareCircle
  private val pictureFile = File("picked_test.jpg") // never touched on disk, addMedia is mocked

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    mediaRepo = mockk(relaxed = true)
    authRepo = mockk()
    profileRepo = mockk()
    circleRepo = mockk()
    pictureReader = mockk()

    circle = fakeCircle()

    signIn("user1") // <-- was missing
    stubProfile(listOf("circle1")) // <-- was missing
    coEvery { circleRepo.getCircle("circle1") } returns Result.success(circle)
    coEvery { mediaRepo.newMediaId(any()) } returns "media1"
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
      )

  private fun readyViewModel(
      description: String = "A description",
      date: MemoryDate? = MemoryDate(2024, Month.MAY, 12),
  ): UploadPictureViewModel {
    val vm = createViewModel() // init loads the circle (Unconfined dispatcher: runs immediately)
    vm.setPicture(pictureFile, 640, 480)
    vm.setDescription(description)
    vm.setDate(date)
    return vm
  }

  private fun captureUploadedItem(): MediaItem.Picture {
    val slot = slot<MediaItem>()
    coVerify { mediaRepo.addMedia(any(), capture(slot), any()) }
    return slot.captured as MediaItem.Picture
  }

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

  private fun fakeCircle(
      circleId: String = "circle1",
      people: Map<String, Person> = emptyMap(),
      members: List<String> = emptyList(),
      viewerNicknames: Map<String, Map<String, String>> = emptyMap(),
  ): CareCircle = mockk {
    every { id } returns circleId
    every { this@mockk.people } returns people
    every { memberIds } returns members
    every { nicknames } returns viewerNicknames
  }

  private fun stubMemberProfile(uid: String, person: Person) {
    coEvery { profileRepo.getProfile(uid) } returns
        Result.success(UserProfile(uid = uid, person = person))
  }

  private fun loadCircle(circle: CareCircle) {
    coEvery { circleRepo.getCircle("circle1") } returns Result.success(circle)
  }

  private fun fakePerson(nick: String?, first: String, last: String): Person = mockk {
    every { nickname } returns nick
    every { firstName } returns first
    every { lastName } returns last
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
    val date = MemoryDate(2026, Month.OCTOBER, 14)
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

  // ---------- onPersonAdded ----------

  private val bob = Person(id = "bob", firstName = "Bob", lastName = "Martin")

  @Test
  fun addedPersonIsInsertedIntoTheCircle() {
    loadCircle(CareCircle(id = "circle1"))
    val vm = createViewModel()
    vm.onPersonAdded(bob)
    assertEquals(bob, vm.careCircle.value?.people?.get("bob"))
  }

  @Test
  fun addedPersonIsTagged() {
    loadCircle(CareCircle(id = "circle1"))
    val vm = createViewModel()
    vm.onPersonAdded(bob)
    assertTrue("bob" in vm.uiState.value.shownIds)
  }

  @Test
  fun addedPersonKeepsTheOtherTags() {
    loadCircle(CareCircle(id = "circle1", people = mapOf("p1" to Person(id = "p1"))))
    val vm = createViewModel()
    vm.toggleShownId("p1")
    vm.onPersonAdded(bob)
    assertEquals(setOf("p1", "bob"), vm.uiState.value.shownIds)
  }

  @Test
  fun addedPersonAppearsInTheChipsByName() {
    loadCircle(CareCircle(id = "circle1"))
    val vm = createViewModel()
    vm.onPersonAdded(bob)
    assertEquals("Bob", vm.involvedOptions.value["bob"])
  }

  @Test
  fun addedPersonIsSavedWithThePicture() {
    loadCircle(CareCircle(id = "circle1"))
    val vm = readyViewModel()
    vm.onPersonAdded(bob)
    vm.uploadPicture()
    assertEquals(listOf("bob"), captureUploadedItem().shownIds)
  }

  @Test
  fun personWithoutIdIsRefused() {
    loadCircle(CareCircle(id = "circle1"))
    val vm = createViewModel()
    val result = vm.onPersonAdded(bob.copy(id = ""))
    assertEquals(
        UploadPictureViewModelErrorMessage.PERSON_WITHOUT_ID.mess,
        result.exceptionOrNull()?.message,
    )
    assertTrue(vm.uiState.value.shownIds.isEmpty())
    assertTrue(vm.careCircle.value?.people.orEmpty().isEmpty())
  }

  @Test
  fun addedPersonIsRefusedWhenTheCircleFailedToLoad() {
    coEvery { circleRepo.getCircle(any()) } returns Result.failure(IOException("boom"))
    val vm = createViewModel()
    val result = vm.onPersonAdded(bob)
    assertEquals(
        UploadPictureViewModelErrorMessage.CANNOT_TAG_PERSON.mess,
        result.exceptionOrNull()?.message,
    )
    assertTrue(vm.uiState.value.shownIds.isEmpty())
  }

  @Test
  fun personAddedWhileTheCircleLoadsIsKeptWhenItArrives() {
    val circleReply = CompletableDeferred<Result<CareCircle>>()
    coEvery { circleRepo.getCircle("circle1") } coAnswers { circleReply.await() }
    val vm = readyViewModel()

    assertTrue(vm.onPersonAdded(bob).isSuccess)
    // the server's reply was read before bob was saved, so it does not contain him
    circleReply.complete(Result.success(CareCircle(id = "circle1")))

    assertEquals(bob, vm.careCircle.value?.people?.get("bob"))
    assertEquals("Bob", vm.involvedOptions.value["bob"])
    vm.uploadPicture()
    assertEquals(listOf("bob"), captureUploadedItem().shownIds)
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
  fun involvedOptionsFallsBackToFirstNameWhenNicknameBlank() {
    val person = fakePerson("  ", "Anne", "Dupont")
    coEvery { circleRepo.getCircle("circle1") } returns
        Result.success(fakeCircle(people = mapOf("p1" to person)))
    assertEquals("Anne", createViewModel().involvedOptions.value["p1"])
  }

  @Test
  fun involvedOptionsFallsBackToFirstNameWhenNicknameNull() {
    val person = fakePerson(null, "Anne", "Dupont")
    coEvery { circleRepo.getCircle("circle1") } returns
        Result.success(fakeCircle(people = mapOf("p1" to person)))
    assertEquals("Anne", createViewModel().involvedOptions.value["p1"])
  }

  @Test
  fun involvedOptionsLabelsMembersWithTheirProfileName() {
    stubMemberProfile("member1", Person(id = "member1", firstName = "Marie", lastName = "Curie"))
    loadCircle(fakeCircle(members = listOf("member1")))
    assertEquals("Marie", createViewModel().involvedOptions.value["member1"])
  }

  @Test
  fun involvedOptionsWaitsForEveryMemberProfile() {
    val profileReply = CompletableDeferred<Result<UserProfile?>>()
    stubMemberProfile("member1", Person(firstName = "Marie"))
    coEvery { profileRepo.getProfile("member2") } coAnswers { profileReply.await() }
    loadCircle(
        fakeCircle(
            members = listOf("member1", "member2"),
            people = mapOf("p1" to fakePerson(null, "Anne", "Dupont")),
        )
    )
    val vm = createViewModel()

    assertTrue(vm.involvedOptions.value.isEmpty())
    profileReply.complete(Result.failure(IOException("boom")))
    assertEquals(
        mapOf("member1" to "Marie", "member2" to unknownMemberLabel(1), "p1" to "Anne"),
        vm.involvedOptions.value,
    )
  }

  @Test
  fun involvedOptionsRequestsEachMemberProfile() {
    stubMemberProfile("member1", Person(firstName = "Marie"))
    stubMemberProfile("member2", Person(firstName = "Pierre"))
    loadCircle(fakeCircle(members = listOf("member1", "member2")))
    createViewModel()
    coVerify(exactly = 1) { profileRepo.getProfile("member1") }
    coVerify(exactly = 1) { profileRepo.getProfile("member2") }
  }

  @Test
  fun involvedOptionsNumbersUnreadableMembers() {
    coEvery { profileRepo.getProfile("member1") } returns Result.failure(IOException("boom"))
    coEvery { profileRepo.getProfile("member2") } returns Result.failure(IOException("boom"))
    loadCircle(fakeCircle(members = listOf("member1", "member2")))
    assertEquals(
        mapOf("member1" to unknownMemberLabel(1), "member2" to unknownMemberLabel(2)),
        createViewModel().involvedOptions.value,
    )
  }

  @Test
  fun involvedOptionsUsesTheViewerNicknameOfAnUnreadableMember() {
    coEvery { profileRepo.getProfile("member1") } returns Result.failure(IOException("boom"))
    loadCircle(
        fakeCircle(
            members = listOf("member1"),
            viewerNicknames = mapOf("user1" to mapOf("member1" to "Dad")),
        )
    )
    assertEquals("Dad", createViewModel().involvedOptions.value["member1"])
  }

  @Test
  fun involvedOptionsListsMembersAndPeopleTogether() {
    stubMemberProfile("member1", Person(firstName = "Marie"))
    loadCircle(
        fakeCircle(
            members = listOf("member1"),
            people = mapOf("p1" to fakePerson(null, "Anne", "Dupont")),
        )
    )
    assertEquals(setOf("member1", "p1"), createViewModel().involvedOptions.value.keys)
  }

  @Test
  fun involvedOptionsPrefersTheViewerOwnNickname() {
    loadCircle(
        fakeCircle(
            people = mapOf("p1" to fakePerson("Nana", "Anne", "Dupont")),
            viewerNicknames = mapOf("user1" to mapOf("p1" to "Granny")),
        )
    )
    assertEquals("Granny", createViewModel().involvedOptions.value["p1"])
  }

  @Test
  fun involvedOptionsIgnoresOtherViewersNicknames() {
    loadCircle(
        fakeCircle(
            people = mapOf("p1" to fakePerson("Nana", "Anne", "Dupont")),
            viewerNicknames = mapOf("someoneElse" to mapOf("p1" to "Granny")),
        )
    )
    assertEquals("Nana", createViewModel().involvedOptions.value["p1"])
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
  fun successfulUploadEmitsUploadedEvent() = runTest {
    val vm = readyViewModel()

    vm.uploadPicture()

    assertEquals(UploadPictureEvent.Uploaded, vm.events.first())
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
    loadCircle(fakeCircle(people = mapOf("p1" to fakePerson(null, "Anne", "Dupont"))))
    val vm = readyViewModel()
    vm.toggleShownId("p1")
    vm.uploadPicture()
    assertEquals(listOf("p1"), captureUploadedItem().shownIds)
  }

  @Test
  fun uploadedItemHasTaggedMembersAndPeople() {
    stubMemberProfile("member1", Person(firstName = "Marie"))
    loadCircle(
        fakeCircle(
            members = listOf("member1"),
            people = mapOf("p1" to fakePerson(null, "Anne", "Dupont")),
        )
    )
    val vm = readyViewModel()
    vm.toggleShownId("member1")
    vm.toggleShownId("p1")
    vm.uploadPicture()
    assertEquals(setOf("member1", "p1"), captureUploadedItem().shownIds.toSet())
  }

  @Test
  fun uploadedItemDropsIdsNotInTheCircle() {
    loadCircle(fakeCircle(people = mapOf("p1" to fakePerson(null, "Anne", "Dupont"))))
    val vm = readyViewModel()
    vm.toggleShownId("p1")
    vm.toggleShownId("removed")
    vm.uploadPicture()
    assertEquals(listOf("p1"), captureUploadedItem().shownIds)
  }

  @Test
  fun deselectedPersonIsNotUploaded() {
    loadCircle(fakeCircle(people = mapOf("p1" to fakePerson(null, "Anne", "Dupont"))))
    val vm = readyViewModel()
    vm.toggleShownId("p1")
    vm.toggleShownId("p1")
    vm.uploadPicture()
    assertTrue(captureUploadedItem().shownIds.isEmpty())
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
  fun failedUploadDoesNotEmitUploadedEvent() = runTest {
    coEvery { mediaRepo.addMedia(any(), any(), any()) } throws IOException("boom")
    val vm = readyViewModel()

    vm.uploadPicture()

    assertNull(withTimeoutOrNull(100.milliseconds) { vm.events.first() })
  }

  // ______________________________________

  @Test
  fun selectPictureRequestsSelectedUriFromReader() {
    val uri = "content://pictures/123"
    val vm = createViewModel()

    coEvery { pictureReader.read(uri) } returns
        PickedImage(
            file = pictureFile,
            width = 640,
            height = 480,
        )

    vm.selectPicture(uri)

    coVerify(exactly = 1) { pictureReader.read(uri) }
  }

  @Test
  fun selectPictureUpdatesPictureState() {
    val uri = "content://pictures/123"
    val vm = createViewModel()

    coEvery { pictureReader.read(uri) } returns
        PickedImage(
            file = pictureFile,
            width = 640,
            height = 480,
        )

    vm.selectPicture(uri)

    assertSame(pictureFile, vm.uiState.value.pictureFile)
    assertEquals(640, vm.uiState.value.pictureWidth)
    assertEquals(480, vm.uiState.value.pictureHeight)
  }

  @Test
  fun selectPictureFailureSetsReadError() {
    val uri = "content://pictures/123"
    val vm = createViewModel()

    coEvery { pictureReader.read(uri) } throws IOException(ERROR_MESSAGE)

    vm.selectPicture(uri)

    assertEquals(
        UploadPictureViewModelErrorMessage.COULD_NOT_READ_PICTURE.mess + ERROR_MESSAGE,
        vm.uiState.value.errorMsg,
    )
  }

  // ---------- uploadPicture: validation ----------

  @Test
  fun blankDescriptionReportsDescriptionEmptyError() {
    val vm = readyViewModel(description = "   ")
    vm.uploadPicture()
    assertEquals(
        UploadPictureViewModelErrorMessage.DESCRIPTION_EMPTY.mess,
        vm.uiState.value.errorMsg,
    )
  }

  @Test
  fun blankDescriptionDoesNotCallRepository() {
    readyViewModel(description = "   ").uploadPicture()
    coVerify(exactly = 0) { mediaRepo.addMedia(any(), any(), any()) }
  }

  @Test
  fun missingDateReportsSelectDateError() {
    val vm = readyViewModel(date = null)
    vm.uploadPicture()
    assertEquals(UploadPictureViewModelErrorMessage.SELECT_DATE.mess, vm.uiState.value.errorMsg)
  }

  @Test
  fun missingDateDoesNotCallRepository() {
    readyViewModel(date = null).uploadPicture()
    coVerify(exactly = 0) { mediaRepo.addMedia(any(), any(), any()) }
  }

  @Test
  fun invalidUploadDoesNotStartUploading() {
    val vm = readyViewModel(description = "   ")
    vm.uploadPicture()
    assertFalse(vm.uiState.value.isUploading)
  }

  // ---------- uploadPicture: item content ----------

  @Test
  fun uploadedItemHasDate() {
    val date = MemoryDate(2024, Month.MAY, 12)
    readyViewModel(date = date).uploadPicture()
    assertEquals(date, captureUploadedItem().date)
  }

  @Test
  fun initialYearOnlyDateAllowsUploadAfterPictureAndDescription() {
    val currentYear = LocalDate.now().year
    val vm = createViewModel()
    assertEquals(MemoryDate(currentYear), vm.uiState.value.date)
    vm.setPicture(pictureFile, 640, 480)
    vm.setDescription("A description")
    assertTrue(vm.uiState.value.canUpload)
  }

  // ---------- canUpload ----------

  @Test
  fun canUploadIsTrueWhenFormIsComplete() {
    assertTrue(readyViewModel().uiState.value.canUpload)
  }

  @Test
  fun canUploadIsFalseWithoutDate() {
    assertFalse(readyViewModel(date = null).uiState.value.canUpload)
  }

  @Test
  fun uploadInProgressDisablesFormAndIgnoresSecondSubmission() {
    val uploadReply = CompletableDeferred<Unit>()
    coEvery { mediaRepo.addMedia(any(), any(), any()) } coAnswers { uploadReply.await() }
    val vm = readyViewModel()

    vm.uploadPicture()
    assertTrue(vm.uiState.value.isUploading)
    assertFalse(vm.uiState.value.canUpload)
    vm.uploadPicture()
    coVerify(exactly = 1) { mediaRepo.addMedia(any(), any(), any()) }

    uploadReply.complete(Unit)
    assertFalse(vm.uiState.value.isUploading)
  }

  // ---------- add person ----------

  @Test
  fun onAddPersonOpensDialog() {
    val vm = createViewModel()

    vm.onAddPerson()

    assertTrue(vm.uiState.value.isAddPersonOpen)
  }

  @Test
  fun dismissAddPersonClosesDialogAndClearsDraft() {
    val vm = createViewModel()
    vm.onAddPerson()
    vm.setPersonFirstName("Anne")
    vm.setPersonLastName("Dupont")
    vm.setPersonNickname("Nana")

    vm.dismissAddPerson()

    assertFalse(vm.uiState.value.isAddPersonOpen)
    assertEquals("", vm.uiState.value.personFirstName)
    assertEquals("", vm.uiState.value.personLastName)
    assertEquals("", vm.uiState.value.personNickname)
  }

  @Test
  fun addPersonSavesInCircleAndSelectsNewPerson() {
    coEvery { circleRepo.getCircle("circle1") } returns Result.success(CareCircle(id = "circle1"))
    val saved = Person(id = "person1", firstName = "Anne", lastName = "Dupont")
    coEvery { circleRepo.addPerson(any(), any()) } returns Result.success(saved)
    val vm = createViewModel()
    vm.onAddPerson()
    vm.setPersonFirstName("Anne")
    vm.setPersonLastName("Dupont")

    vm.addPerson()

    coVerify(exactly = 1) {
      circleRepo.addPerson("circle1", match { it.firstName == "Anne" && it.lastName == "Dupont" })
    }
    assertEquals(saved, vm.careCircle.value?.people?.get("person1"))
    assertEquals("Anne", vm.involvedOptions.value["person1"])
    assertTrue("person1" in vm.uiState.value.shownIds)
    assertFalse(vm.uiState.value.isAddPersonOpen)
    assertFalse(vm.uiState.value.isAddingPerson)
  }

  @Test
  fun blankPersonNameDoesNotCallRepository() {
    val vm = createViewModel()
    vm.onAddPerson()
    vm.setPersonFirstName("  ")

    vm.addPerson()

    coVerify(exactly = 0) { circleRepo.addPerson(any(), any()) }
    assertEquals("A first name is required", vm.uiState.value.errorMsg)
    assertTrue(vm.uiState.value.isAddPersonOpen)
  }

  @Test
  fun failedAddPersonKeepsDraftAndReportsError() {
    coEvery { circleRepo.addPerson(any(), any()) } returns Result.failure(IOException("offline"))
    val vm = createViewModel()
    vm.onAddPerson()
    vm.setPersonFirstName("Anne")

    vm.addPerson()

    assertEquals("offline", vm.uiState.value.errorMsg)
    assertEquals("Anne", vm.uiState.value.personFirstName)
    assertTrue(vm.uiState.value.isAddPersonOpen)
    assertFalse(vm.uiState.value.isAddingPerson)
  }

  @Test
  fun addPersonWithoutLoadedCircleReportsRetryError() {
    coEvery { circleRepo.getCircle(any()) } returns Result.failure(IOException("offline"))
    val vm = createViewModel()
    vm.setPersonFirstName("Anne")

    vm.addPerson()

    assertEquals(UploadPictureViewModelErrorMessage.STILL_LOADING.mess, vm.uiState.value.errorMsg)
    coVerify(exactly = 0) { circleRepo.addPerson(any(), any()) }
  }

  @Test
  fun successfulAddPersonSendsAndClearsAllDraftFields() {
    loadCircle(CareCircle(id = "circle1"))
    val saved = Person(id = "person1", firstName = "Anne", lastName = "Dupont", nickname = "Nana")
    coEvery { circleRepo.addPerson(any(), any()) } returns Result.success(saved)
    val vm = createViewModel()
    vm.onAddPerson()
    vm.setPersonFirstName("Anne")
    vm.setPersonLastName("Dupont")
    vm.setPersonNickname("Nana")

    vm.addPerson()

    coVerify(exactly = 1) {
      circleRepo.addPerson(
          "circle1",
          match { it.firstName == "Anne" && it.lastName == "Dupont" && it.nickname == "Nana" },
      )
    }
    assertEquals("", vm.uiState.value.personFirstName)
    assertEquals("", vm.uiState.value.personLastName)
    assertEquals("", vm.uiState.value.personNickname)
    assertTrue("person1" in vm.uiState.value.shownIds)
  }

  @Test
  fun addPersonInProgressKeepsDialogOpenAndIgnoresSecondSubmission() {
    loadCircle(CareCircle(id = "circle1"))
    val personReply = CompletableDeferred<Result<Person>>()
    coEvery { circleRepo.addPerson(any(), any()) } coAnswers { personReply.await() }
    val vm = createViewModel()
    vm.onAddPerson()
    vm.setPersonFirstName("Anne")

    vm.addPerson()
    assertTrue(vm.uiState.value.isAddingPerson)
    vm.dismissAddPerson()
    assertTrue(vm.uiState.value.isAddPersonOpen)
    assertEquals("Anne", vm.uiState.value.personFirstName)
    vm.addPerson()
    coVerify(exactly = 1) { circleRepo.addPerson(any(), any()) }

    personReply.complete(Result.success(Person(id = "person1", firstName = "Anne")))
    assertFalse(vm.uiState.value.isAddingPerson)
    assertFalse(vm.uiState.value.isAddPersonOpen)
  }

  @Test
  fun addPersonFailureWithoutMessageUsesFallbackError() {
    coEvery { circleRepo.addPerson(any(), any()) } returns Result.failure(IOException())
    val vm = createViewModel()
    vm.setPersonFirstName("Anne")

    vm.addPerson()

    assertEquals(
        UploadPictureViewModelErrorMessage.COULD_NOT_ADD_PERSON.mess,
        vm.uiState.value.errorMsg,
    )
    assertFalse(vm.uiState.value.isAddingPerson)
  }

  @Test
  fun thrownAddPersonFailureClearsSavingStateAndKeepsDraft() {
    coEvery { circleRepo.addPerson(any(), any()) } throws IOException("broken")
    val vm = createViewModel()
    vm.onAddPerson()
    vm.setPersonFirstName("Anne")

    vm.addPerson()

    assertEquals("broken", vm.uiState.value.errorMsg)
    assertEquals("Anne", vm.uiState.value.personFirstName)
    assertTrue(vm.uiState.value.isAddPersonOpen)
    assertFalse(vm.uiState.value.isAddingPerson)
  }
}
