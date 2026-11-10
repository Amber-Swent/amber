// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by MeiliV, with assistance from OpenAI Codex.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.upload.media

import com.github.se.amber.R
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.location.PlaceSearchRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.location.Place
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.model.validation.InputValidation
import com.github.se.amber.ui.utils.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import java.io.File
import java.io.IOException
import java.time.Month
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
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
 * Unit tests for [MediaViewModel], through [FakeMediaViewModel], a minimal concrete subclass. All
 * repositories are mocked, nothing touches the network. The care circle is loaded in `init`, so
 * each test configures the repositories BEFORE calling [createViewModel]. What is specific to a
 * kind of media is tested in that ViewModel's own test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaViewModelTest {
  private val testDispatcher = UnconfinedTestDispatcher()
  private val SLOW_SEARCH = 1_000.milliseconds
  private val AFTER_DEBOUNCE = 400L // ms: past the 300 ms pause, before SLOW_SEARCH ends

  private lateinit var mediaRepo: MediaRepository
  private lateinit var authRepo: AuthRepository
  private lateinit var profileRepo: ProfileRepository
  private lateinit var circleRepo: CareCircleRepository
  private lateinit var placeRepo: PlaceSearchRepository

  private lateinit var circle: CareCircle
  private val contentFile = File("content_test.bin") // never touched on disk, addMedia is mocked

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    mediaRepo = mockk(relaxed = true)
    authRepo = mockk()
    profileRepo = mockk()
    circleRepo = mockk()
    placeRepo = mockk()

    circle = fakeCircle()

    signIn("user1")
    stubProfile(listOf("circle1"))
    coEvery { circleRepo.getCircle("circle1") } returns Result.success(circle)
    coEvery { mediaRepo.newMediaId(any()) } returns "media1"
    coEvery { placeRepo.search(any()) } returns Result.success(listOf(lausanne))
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkAll()
  }

  // ---------- helpers ----------

  private fun createViewModel(requiresContent: Boolean = true) =
      FakeMediaViewModel(
          mediaRepo = mediaRepo,
          authRepo = authRepo,
          profileRepo = profileRepo,
          circleRepo = circleRepo,
          requiresContent = requiresContent,
          placeRepo = placeRepo,
      )

  private val lausanne = GeoLocation(lat = 46.52, lng = 6.63, placeName = "Lausanne, Switzerland")

  /** Types [query] in the place field and lets its search run. */
  private fun FakeMediaViewModel.searchPlace(query: String) {
    setPlaceQuery(query)
    testDispatcher.scheduler.advanceUntilIdle()
  }

  private fun readyViewModel(
      description: String = "A description",
      date: MemoryDate? = MemoryDate(2024, Month.MAY, 12),
  ): FakeMediaViewModel {
    val vm = createViewModel() // init loads the circle (Unconfined dispatcher: runs immediately)
    vm.setContent(contentFile)
    vm.setDescription(description)
    vm.setDate(date)
    return vm
  }

  private fun captureUploadedItem(): MediaItem.Text {
    val slot = slot<MediaItem>()
    coVerify { mediaRepo.addMedia(any(), capture(slot), any()) }
    return slot.captured as MediaItem.Text
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
      places: Map<String, Place> = emptyMap(),
      viewerNicknames: Map<String, Map<String, String>> = emptyMap(),
  ): CareCircle = mockk {
    every { id } returns circleId
    every { this@mockk.people } returns people
    every { memberIds } returns members
    every { this@mockk.places } returns places
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
    assertEquals("", createViewModel().uiState.value.common.description)
  }

  @Test
  fun initialStateIsNotUploading() {
    assertFalse(createViewModel().uiState.value.common.isUploading)
  }

  @Test
  fun initialStateHasNoErrorWhenCircleLoads() {
    assertNull(createViewModel().uiState.value.common.errorMsgRes)
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
        MediaViewModelErrorMessage.UNVERIFIED.messageRes,
        createViewModel().uiState.value.common.errorMsgRes,
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
        MediaViewModelErrorMessage.COULD_NOT_LOAD_PROFILE_ID.messageRes,
        createViewModel().uiState.value.common.errorMsgRes,
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
        MediaViewModelErrorMessage.NOT_SET_CARE_CIRCLE.messageRes,
        createViewModel().uiState.value.common.errorMsgRes,
    )
  }

  @Test
  fun profileWithoutCircleIdSetsCircleNotSetUpError() {
    stubProfile(emptyList())
    assertEquals(
        MediaViewModelErrorMessage.NOT_SET_CARE_CIRCLE.messageRes,
        createViewModel().uiState.value.common.errorMsgRes,
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
        MediaViewModelErrorMessage.COULD_NOT_LOAD_CARE_CIRCLE.messageRes,
        createViewModel().uiState.value.common.errorMsgRes,
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
    assertEquals("Grandma's birthday", vm.uiState.value.common.description)
  }

  @Test
  fun setDateUpdatesState() {
    val vm = createViewModel()
    val date = MemoryDate(2026, Month.OCTOBER, 14)
    vm.setDate(date)
    assertEquals(date, vm.uiState.value.common.date)
  }

  @Test
  fun setErrorMsgUpdatesState() {
    val vm = createViewModel()
    vm.setErrorMsg(R.string.uploadMediaErrorAddPerson)
    assertEquals(R.string.uploadMediaErrorAddPerson, vm.uiState.value.common.errorMsgRes)
  }

  @Test
  fun clearErrorMsgResetsError() {
    val vm = createViewModel()
    vm.setErrorMsg(R.string.uploadMediaErrorAddPerson)
    vm.clearErrorMsg()
    assertNull(vm.uiState.value.common.errorMsgRes)
  }

  // ---------- toggleShownId ----------

  @Test
  fun toggleShownIdAddsUnknownId() {
    val vm = createViewModel()
    vm.toggleShownId("new_id")
    assertTrue("new_id" in vm.uiState.value.common.shownIds)
  }

  @Test
  fun toggleShownIdRemovesExistingId() {
    val vm = createViewModel()
    vm.toggleShownId("new_id")
    vm.toggleShownId("new_id")
    assertFalse("new_id" in vm.uiState.value.common.shownIds)
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
    assertTrue("bob" in vm.uiState.value.common.shownIds)
  }

  @Test
  fun addedPersonKeepsTheOtherTags() {
    loadCircle(CareCircle(id = "circle1", people = mapOf("p1" to Person(id = "p1"))))
    val vm = createViewModel()
    vm.toggleShownId("p1")
    vm.onPersonAdded(bob)
    assertEquals(setOf("p1", "bob"), vm.uiState.value.common.shownIds)
  }

  @Test
  fun addedPersonAppearsInTheChipsByName() {
    loadCircle(CareCircle(id = "circle1"))
    val vm = createViewModel()
    vm.onPersonAdded(bob)
    assertEquals(UiText.Dynamic("Bob"), vm.involvedOptions.value["bob"])
  }

  @Test
  fun addedPersonIsSavedWithTheMedia() {
    loadCircle(CareCircle(id = "circle1"))
    val vm = readyViewModel()
    vm.onPersonAdded(bob)
    vm.uploadMedia()
    assertEquals(listOf("bob"), captureUploadedItem().shownIds)
  }

  @Test
  fun personWithoutIdIsRefused() {
    loadCircle(CareCircle(id = "circle1"))
    val vm = createViewModel()
    val result = vm.onPersonAdded(bob.copy(id = ""))
    assertEquals(
        MediaViewModelErrorMessage.PERSON_WITHOUT_ID,
        (result.exceptionOrNull() as? PersonNotTaggedException)?.error,
    )
    assertTrue(vm.uiState.value.common.shownIds.isEmpty())
    assertTrue(vm.careCircle.value?.people.orEmpty().isEmpty())
  }

  @Test
  fun addedPersonIsRefusedWhenTheCircleFailedToLoad() {
    coEvery { circleRepo.getCircle(any()) } returns Result.failure(IOException("boom"))
    val vm = createViewModel()
    val result = vm.onPersonAdded(bob)
    assertEquals(
        MediaViewModelErrorMessage.CANNOT_TAG_PERSON,
        (result.exceptionOrNull() as? PersonNotTaggedException)?.error,
    )
    assertTrue(vm.uiState.value.common.shownIds.isEmpty())
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
    assertEquals(UiText.Dynamic("Bob"), vm.involvedOptions.value["bob"])
    vm.uploadMedia()
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
    assertEquals(UiText.Dynamic("Nana"), createViewModel().involvedOptions.value["p1"])
  }

  @Test
  fun involvedOptionsFallsBackToFirstNameWhenNicknameBlank() {
    val person = fakePerson("  ", "Anne", "Dupont")
    coEvery { circleRepo.getCircle("circle1") } returns
        Result.success(fakeCircle(people = mapOf("p1" to person)))
    assertEquals(UiText.Dynamic("Anne"), createViewModel().involvedOptions.value["p1"])
  }

  @Test
  fun involvedOptionsFallsBackToFirstNameWhenNicknameNull() {
    val person = fakePerson(null, "Anne", "Dupont")
    coEvery { circleRepo.getCircle("circle1") } returns
        Result.success(fakeCircle(people = mapOf("p1" to person)))
    assertEquals(UiText.Dynamic("Anne"), createViewModel().involvedOptions.value["p1"])
  }

  @Test
  fun involvedOptionsLabelsMembersWithTheirProfileName() {
    stubMemberProfile("member1", Person(id = "member1", firstName = "Marie", lastName = "Curie"))
    loadCircle(fakeCircle(members = listOf("member1")))
    assertEquals(UiText.Dynamic("Marie"), createViewModel().involvedOptions.value["member1"])
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
        mapOf(
            "member1" to UiText.Dynamic("Marie"),
            "member2" to unknownMemberLabel(1),
            "p1" to UiText.Dynamic("Anne"),
        ),
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
    assertEquals(UiText.Dynamic("Dad"), createViewModel().involvedOptions.value["member1"])
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
    assertEquals(UiText.Dynamic("Granny"), createViewModel().involvedOptions.value["p1"])
  }

  @Test
  fun involvedOptionsIgnoresOtherViewersNicknames() {
    loadCircle(
        fakeCircle(
            people = mapOf("p1" to fakePerson("Nana", "Anne", "Dupont")),
            viewerNicknames = mapOf("someoneElse" to mapOf("p1" to "Granny")),
        )
    )
    assertEquals(UiText.Dynamic("Nana"), createViewModel().involvedOptions.value["p1"])
  }

  // ---------- upload: guards ----------

  @Test
  fun uploadWithoutUserSetsIdentityError() {
    val vm = readyViewModel()
    signIn(null)
    vm.uploadMedia()
    assertEquals(
        MediaViewModelErrorMessage.UNVERIFIED.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
  }

  @Test
  fun uploadWithoutLoadedCircleAsksToRetry() {
    coEvery { circleRepo.getCircle(any()) } returns Result.failure(IOException("boom"))
    val vm = readyViewModel()
    vm.uploadMedia()
    assertEquals(
        MediaViewModelErrorMessage.STILL_LOADING.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
  }

  @Test
  fun uploadWithoutContentReportsTheContentError() {
    val vm = createViewModel()
    vm.uploadMedia()
    assertEquals(FakeMediaViewModel.CONTENT_MISSING, vm.uiState.value.common.errorMsgRes)
  }

  @Test
  fun uploadWithoutContentNeverCallsRepository() {
    createViewModel().uploadMedia()
    coVerify(exactly = 0) { mediaRepo.addMedia(any(), any(), any()) }
  }

  // ---------- upload: success ----------

  @Test
  fun successfulUploadEmitsUploadedEvent() = runTest {
    val vm = readyViewModel()

    vm.uploadMedia()

    assertEquals(MediaEvent.Uploaded, vm.events.first())
  }

  @Test
  fun successfulUploadClearsUploadingFlag() {
    val vm = readyViewModel()
    vm.uploadMedia()
    assertFalse(vm.uiState.value.common.isUploading)
  }

  @Test
  fun successfulUploadHasNoError() {
    val vm = readyViewModel()
    vm.uploadMedia()
    assertNull(vm.uiState.value.common.errorMsgRes)
  }

  @Test
  fun successfulUploadCallsRepositoryOnce() {
    readyViewModel().uploadMedia()
    coVerify(exactly = 1) { mediaRepo.addMedia(any(), any(), any()) }
  }

  @Test
  fun successfulUploadSendsTheLoadedCircle() {
    readyViewModel().uploadMedia()
    coVerify { mediaRepo.addMedia(circle, any(), any()) }
  }

  @Test
  fun successfulUploadSendsTheFileOfTheContent() {
    readyViewModel().uploadMedia()
    coVerify { mediaRepo.addMedia(any(), any(), contentFile) }
  }

  @Test
  fun uploadedItemHasAuthorId() {
    readyViewModel().uploadMedia()
    assertEquals("user1", captureUploadedItem().authorId)
  }

  @Test
  fun uploadedItemHasDescription() {
    val vm = readyViewModel()
    vm.setDescription("Beach day")
    vm.uploadMedia()
    assertEquals("Beach day", captureUploadedItem().description)
  }

  @Test
  fun uploadedItemHasTaggedPeople() {
    loadCircle(fakeCircle(people = mapOf("p1" to fakePerson(null, "Anne", "Dupont"))))
    val vm = readyViewModel()
    vm.toggleShownId("p1")
    vm.uploadMedia()
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
    vm.uploadMedia()
    assertEquals(setOf("member1", "p1"), captureUploadedItem().shownIds.toSet())
  }

  @Test
  fun uploadedItemDropsIdsNotInTheCircle() {
    loadCircle(fakeCircle(people = mapOf("p1" to fakePerson(null, "Anne", "Dupont"))))
    val vm = readyViewModel()
    vm.toggleShownId("p1")
    vm.toggleShownId("removed")
    vm.uploadMedia()
    assertEquals(listOf("p1"), captureUploadedItem().shownIds)
  }

  @Test
  fun deselectedPersonIsNotUploaded() {
    loadCircle(fakeCircle(people = mapOf("p1" to fakePerson(null, "Anne", "Dupont"))))
    val vm = readyViewModel()
    vm.toggleShownId("p1")
    vm.toggleShownId("p1")
    vm.uploadMedia()
    assertTrue(captureUploadedItem().shownIds.isEmpty())
  }

  // ---------- upload: failure ----------

  @Test
  fun failedUploadReportsErrorMessage() {
    coEvery { mediaRepo.addMedia(any(), any(), any()) } throws IOException("boom")
    val vm = readyViewModel()
    vm.uploadMedia()
    assertEquals(
        MediaViewModelErrorMessage.UNKNOW_ERROR.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
  }

  @Test
  fun failedUploadWithoutMessageReportsUnknownError() {
    coEvery { mediaRepo.addMedia(any(), any(), any()) } throws IOException()
    val vm = readyViewModel()
    vm.uploadMedia()
    assertEquals(
        MediaViewModelErrorMessage.UNKNOW_ERROR.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
  }

  @Test
  fun failedUploadClearsUploadingFlag() {
    coEvery { mediaRepo.addMedia(any(), any(), any()) } throws IOException("boom")
    val vm = readyViewModel()
    vm.uploadMedia()
    assertFalse(vm.uiState.value.common.isUploading)
  }

  @Test
  fun failedUploadDoesNotEmitUploadedEvent() = runTest {
    coEvery { mediaRepo.addMedia(any(), any(), any()) } throws IOException("boom")
    val vm = readyViewModel()

    vm.uploadMedia()

    assertNull(withTimeoutOrNull(100.milliseconds) { vm.events.first() })
  }

  // ---------- upload: validation ----------

  @Test
  fun blankDescriptionReportsDescriptionEmptyError() {
    val vm = readyViewModel(description = "   ")
    vm.uploadMedia()
    assertEquals(
        MediaViewModelErrorMessage.DESCRIPTION_EMPTY.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
  }

  @Test
  fun blankDescriptionDoesNotCallRepository() {
    readyViewModel(description = "   ").uploadMedia()
    coVerify(exactly = 0) { mediaRepo.addMedia(any(), any(), any()) }
  }

  @Test
  fun missingDateReportsSelectDateError() {
    val vm = readyViewModel(date = null)
    vm.uploadMedia()
    assertEquals(
        MediaViewModelErrorMessage.SELECT_DATE.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
  }

  @Test
  fun missingDateDoesNotCallRepository() {
    readyViewModel(date = null).uploadMedia()
    coVerify(exactly = 0) { mediaRepo.addMedia(any(), any(), any()) }
  }

  @Test
  fun invalidUploadDoesNotStartUploading() {
    val vm = readyViewModel(description = "   ")
    vm.uploadMedia()
    assertFalse(vm.uiState.value.common.isUploading)
  }

  // ---------- upload: item content ----------

  @Test
  fun uploadedItemHasDate() {
    val date = MemoryDate(2024, Month.MAY, 12)
    readyViewModel(date = date).uploadMedia()
    assertEquals(date, captureUploadedItem().date)
  }

  // ---------- place search ----------

  @Test
  fun initialStateHasNoPlace() {
    val state = createViewModel().uiState.value.common
    assertEquals("", state.location.query)
    assertTrue(state.location.suggestions.isEmpty())
    assertNull(state.location.pickedPlace)
  }

  @Test
  fun typedPlaceIsSearchedAfterAPause() {
    val vm = createViewModel()
    vm.setPlaceQuery("Lausanne")
    coVerify(exactly = 0) { placeRepo.search(any()) }

    testDispatcher.scheduler.advanceUntilIdle()

    coVerify(exactly = 1) { placeRepo.search("Lausanne") }
    assertEquals(listOf(lausanne), vm.uiState.value.common.location.suggestions)
  }

  @Test
  fun fastTypingSearchesOnlyTheLastText() {
    val vm = createViewModel()
    vm.setPlaceQuery("Lau")
    vm.setPlaceQuery("Lausa")
    vm.searchPlace("Lausanne")

    coVerify(exactly = 1) { placeRepo.search(any()) }
    coVerify { placeRepo.search("Lausanne") }
  }

  @Test
  fun blankPlaceQueryClearsSuggestionsWithoutSearching() {
    val vm = createViewModel()
    vm.searchPlace("Lausanne")
    vm.searchPlace("  ")

    assertTrue(vm.uiState.value.common.location.suggestions.isEmpty())
    coVerify(exactly = 1) { placeRepo.search(any()) }
  }

  @Test
  fun failedPlaceSearchShowsNoSuggestionsAndLeavesTheErrorMessageAlone() {
    coEvery { placeRepo.search(any()) } returns Result.failure(IOException("offline"))
    val vm = createViewModel()
    vm.searchPlace("Lausanne")

    assertNull(vm.uiState.value.common.errorMsgRes)
    assertTrue(vm.uiState.value.common.location.suggestions.isEmpty())

    // a pending upload error isn't replaced by a search failure
    vm.uploadMedia()
    vm.searchPlace("Geneva")
    assertEquals(FakeMediaViewModel.CONTENT_MISSING, vm.uiState.value.common.errorMsgRes)
  }

  @Test
  fun selectPlaceKeepsTheRealPlaceAndShowsItsName() {
    val vm = createViewModel()
    vm.searchPlace("Lausanne")
    vm.selectPlace(lausanne)

    val state = vm.uiState.value.common
    assertEquals(lausanne, state.location.pickedPlace)
    assertEquals("Lausanne, Switzerland", state.location.query)
    assertTrue(state.location.suggestions.isEmpty())
  }

  @Test
  fun selectPlaceIgnoresAPlaceWithoutCoordinates() {
    val vm = createViewModel()
    vm.selectPlace(GeoLocation(placeName = "Grandma's house"))
    assertNull(vm.uiState.value.common.location.pickedPlace)
  }

  @Test
  fun selectPlaceIgnoresAPlaceWithOnlyALatitude() {
    val vm = createViewModel()
    vm.selectPlace(GeoLocation(lat = 46.52, placeName = "Lausanne"))
    assertNull(vm.uiState.value.common.location.pickedPlace)
  }

  @Test
  fun aPickedPlaceWithoutNameLeavesTheFieldEmpty() {
    val vm = createViewModel()
    vm.searchPlace("46.52, 6.63")
    vm.selectPlace(GeoLocation(lat = 46.52, lng = 6.63))

    assertEquals("", vm.uiState.value.common.location.query)
    assertTrue(vm.uiState.value.common.location.hasValidPlace)
  }

  @Test
  fun retryAfterAFailedSearchSearchesTheSameTextAgain() {
    coEvery { placeRepo.search(any()) } returns Result.failure(IOException("offline"))
    val vm = createViewModel()
    vm.searchPlace("Lausanne")

    coEvery { placeRepo.search(any()) } returns Result.success(listOf(lausanne))
    vm.retryPlaceSearch()
    assertTrue(vm.uiState.value.common.location.isSearching)
    assertFalse(vm.uiState.value.common.location.searchFailed)

    testDispatcher.scheduler.advanceUntilIdle()

    coVerify(exactly = 2) { placeRepo.search("Lausanne") }
    assertEquals(listOf(lausanne), vm.uiState.value.common.location.suggestions)
    assertFalse(vm.uiState.value.common.location.isSearching)
  }

  @Test
  fun retryThatFailsAgainIsFlaggedAgain() {
    coEvery { placeRepo.search(any()) } returns Result.failure(IOException("offline"))
    val vm = createViewModel()
    vm.searchPlace("Lausanne")
    vm.retryPlaceSearch()
    testDispatcher.scheduler.advanceUntilIdle()

    coVerify(exactly = 2) { placeRepo.search("Lausanne") }
    assertTrue(vm.uiState.value.common.location.searchFailed)
    assertFalse(vm.uiState.value.common.location.isSearching)
  }

  @Test
  fun retryWithoutAFailedSearchDoesNothing() {
    val vm = createViewModel()
    vm.searchPlace("Lausanne")
    vm.retryPlaceSearch()
    testDispatcher.scheduler.advanceUntilIdle()

    coVerify(exactly = 1) { placeRepo.search(any()) }
    assertFalse(vm.uiState.value.common.location.isSearching)
  }

  @Test
  fun typingAfterPickingAPlaceDropsIt() {
    val vm = createViewModel()
    vm.selectPlace(lausanne)
    vm.setPlaceQuery("Grandma's house")
    assertNull(vm.uiState.value.common.location.pickedPlace)
  }

  @Test
  fun placeSearchIsInProgressUntilItsResultsArrive() {
    val vm = createViewModel()
    vm.setPlaceQuery("Lausanne")
    assertTrue(vm.uiState.value.common.location.isSearching)

    testDispatcher.scheduler.advanceUntilIdle()

    assertFalse(vm.uiState.value.common.location.isSearching)
  }

  @Test
  fun aSearchWithNoResultsIsNotInProgress() {
    coEvery { placeRepo.search(any()) } returns Result.success(emptyList())
    val vm = createViewModel()
    vm.searchPlace("Grandma's house")

    assertFalse(vm.uiState.value.common.location.isSearching)
    assertTrue(vm.uiState.value.common.location.suggestions.isEmpty())
  }

  @Test
  fun failedOrClearedOrPickedSearchIsNotInProgress() {
    val vm = createViewModel()
    vm.setPlaceQuery("Lausanne")
    vm.setPlaceQuery("")
    assertFalse(vm.uiState.value.common.location.isSearching)

    vm.setPlaceQuery("Lausanne")
    vm.selectPlace(lausanne)
    assertFalse(vm.uiState.value.common.location.isSearching)

    coEvery { placeRepo.search(any()) } returns Result.failure(IOException("offline"))
    vm.searchPlace("Geneva")
    assertFalse(vm.uiState.value.common.location.isSearching)
  }

  @Test
  fun uploadedItemHasThePickedPlaceWithItsCoordinates() {
    val vm = readyViewModel()
    vm.searchPlace("Lausanne")
    vm.selectPlace(lausanne)
    vm.uploadMedia()
    assertEquals(lausanne, captureUploadedItem().location)
  }

  @Test
  fun uploadedItemHasNoLocationWhenNoPlaceIsTyped() {
    readyViewModel().uploadMedia()
    assertNull(captureUploadedItem().location)
  }

  @Test
  fun typedButUnpickedPlaceBlocksTheUpload() {
    val vm = readyViewModel()
    vm.searchPlace("Grandma's house")

    vm.uploadMedia()

    assertEquals(
        MediaViewModelErrorMessage.PICK_A_PLACE.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
    coVerify(exactly = 0) { mediaRepo.addMedia(any(), any(), any()) }
  }

  private val geneva = GeoLocation(lat = 46.2, lng = 6.14, placeName = "Geneva, Switzerland")

  @Test
  fun aQueryTooShortIsNotSearched() {
    val vm = createViewModel()
    vm.searchPlace("La")

    coVerify(exactly = 0) { placeRepo.search(any()) }
    assertFalse(vm.uiState.value.common.location.isSearching)
    assertTrue(vm.uiState.value.common.location.suggestions.isEmpty())
  }

  @Test
  fun shorteningAQueryBelowTheMinimumClearsItsSuggestions() {
    val vm = createViewModel()
    vm.searchPlace("Lausanne")
    vm.searchPlace("La")

    assertTrue(vm.uiState.value.common.location.suggestions.isEmpty())
    coVerify(exactly = 1) { placeRepo.search(any()) }
  }

  @Test
  fun aNewerQueryReplacesTheSearchStillRunning() {
    coEvery { placeRepo.search("Lausanne") } coAnswers
        {
          delay(SLOW_SEARCH)
          Result.success(listOf(lausanne))
        }
    coEvery { placeRepo.search("Geneva") } returns Result.success(listOf(geneva))
    val vm = createViewModel()
    vm.setPlaceQuery("Lausanne")
    testDispatcher.scheduler.advanceTimeBy(AFTER_DEBOUNCE) // Lausanne's search is running

    vm.searchPlace("Geneva")

    assertEquals(listOf(geneva), vm.uiState.value.common.location.suggestions)
  }

  @Test
  fun aResultArrivingAfterAPlaceIsPickedIsIgnored() {
    coEvery { placeRepo.search(any()) } coAnswers
        {
          delay(SLOW_SEARCH)
          Result.success(listOf(lausanne))
        }
    val vm = createViewModel()
    vm.setPlaceQuery("Lausanne")
    testDispatcher.scheduler.advanceTimeBy(AFTER_DEBOUNCE) // the search is running
    vm.selectPlace(geneva)

    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(geneva, vm.uiState.value.common.location.pickedPlace)
    assertTrue(vm.uiState.value.common.location.suggestions.isEmpty())
  }

  @Test
  fun typingTheSameTextAgainAfterPickingSearchesAgain() {
    val vm = createViewModel()
    vm.searchPlace("Lausanne")
    vm.selectPlace(lausanne)
    vm.searchPlace("Lausanne")

    coVerify(exactly = 2) { placeRepo.search("Lausanne") }
    assertFalse(vm.uiState.value.common.location.isSearching)
    assertEquals(listOf(lausanne), vm.uiState.value.common.location.suggestions)
  }

  @Test
  fun anUnchangedQueryDoesNotLookLikeASearch() {
    val vm = createViewModel()
    vm.searchPlace("Lausanne")
    vm.setPlaceQuery("Lausanne")

    assertFalse(vm.uiState.value.common.location.isSearching)
  }

  @Test
  fun failedPlaceSearchIsFlaggedSoTheScreenCanTellItFromNoResults() {
    coEvery { placeRepo.search(any()) } returns Result.failure(IOException("offline"))
    val vm = createViewModel()
    vm.searchPlace("Lausanne")

    assertTrue(vm.uiState.value.common.location.searchFailed)
  }

  @Test
  fun aNewQueryClearsThePreviousSearchFailure() {
    coEvery { placeRepo.search(any()) } returns Result.failure(IOException("offline"))
    val vm = createViewModel()
    vm.searchPlace("Lausanne")

    coEvery { placeRepo.search(any()) } returns Result.success(emptyList())
    vm.searchPlace("Grandma's house")

    assertFalse(vm.uiState.value.common.location.searchFailed)
  }

  @Test
  fun pickingAPlaceClearsThePreviousSearchFailure() {
    coEvery { placeRepo.search(any()) } returns Result.failure(IOException("offline"))
    val vm = createViewModel()
    vm.searchPlace("Lausanne")
    vm.selectPlace(lausanne)

    assertFalse(vm.uiState.value.common.location.searchFailed)
  }

  @Test
  fun uploadReportsTheFirstMissingThing() {
    val vm = createViewModel() // no content, no description, no date
    vm.uploadMedia()
    assertEquals(FakeMediaViewModel.CONTENT_MISSING, vm.uiState.value.common.errorMsgRes)
  }

  // ---------- saved places ----------

  private val home = Place(id = "home", name = "Home", location = GeoLocation(46.5, 6.6))
  private val doctor = Place(id = "doctor", name = "Dr. Martin", location = GeoLocation(46.2, 6.1))
  private val unmapped = Place(id = "old", name = "Old house") // no coordinates

  private fun circleWithPlaces(vararg places: Place) {
    circle = fakeCircle(places = places.associateBy { it.id })
    coEvery { circleRepo.getCircle("circle1") } returns Result.success(circle)
  }

  @Test
  fun placeOptionsAreEmptyWithoutCircle() {
    coEvery { circleRepo.getCircle("circle1") } returns Result.failure(IOException("offline"))
    assertTrue(createViewModel().placeOptions.value.isEmpty())
  }

  @Test
  fun placeOptionsListSavedPlacesByName() {
    circleWithPlaces(home, doctor)
    assertEquals(
        mapOf("home" to "Home", "doctor" to "Dr. Martin"),
        createViewModel().placeOptions.value,
    )
  }

  @Test
  fun placeOptionsLeaveOutPlacesWithoutCoordinates() {
    circleWithPlaces(home, unmapped)
    assertEquals(mapOf("home" to "Home"), createViewModel().placeOptions.value)
  }

  @Test
  fun selectingASavedPlaceSetsItsLocationAndName() {
    circleWithPlaces(home)
    val vm = createViewModel()
    vm.selectPlace("home")

    val state = vm.uiState.value.common
    assertEquals(GeoLocation(46.5, 6.6, placeName = "Home"), state.location.pickedPlace)
    assertEquals("home", state.location.selectedPlaceId)
    assertEquals("Home", state.location.query)
    assertTrue(state.location.hasValidPlace)
  }

  @Test
  fun selectingTheSelectedSavedPlaceAgainDropsIt() {
    circleWithPlaces(home)
    val vm = createViewModel()
    vm.selectPlace("home")
    vm.selectPlace("home")

    val state = vm.uiState.value.common
    assertNull(state.location.pickedPlace)
    assertNull(state.location.selectedPlaceId)
    assertEquals("", state.location.query)
  }

  @Test
  fun selectingAnotherSavedPlaceReplacesTheFirst() {
    circleWithPlaces(home, doctor)
    val vm = createViewModel()
    vm.selectPlace("home")
    vm.selectPlace("doctor")

    assertEquals("doctor", vm.uiState.value.common.location.selectedPlaceId)
    assertEquals("Dr. Martin", vm.uiState.value.common.location.pickedPlace?.placeName)
  }

  @Test
  fun selectingAnUnknownOrUnmappedSavedPlaceDoesNothing() {
    circleWithPlaces(unmapped)
    val vm = createViewModel()
    val initialState = vm.uiState.value
    vm.selectPlace("missing")
    vm.selectPlace("old")
    assertEquals(initialState, vm.uiState.value)
  }

  @Test
  fun typingAfterPickingASavedPlaceDropsIt() {
    circleWithPlaces(home)
    val vm = createViewModel()
    vm.selectPlace("home")
    vm.setPlaceQuery("Lau")

    assertNull(vm.uiState.value.common.location.pickedPlace)
    assertNull(vm.uiState.value.common.location.selectedPlaceId)
  }

  @Test
  fun pickingASuggestionAfterASavedPlaceUnselectsTheChip() {
    circleWithPlaces(home)
    val vm = createViewModel()
    vm.selectPlace("home")
    vm.selectPlace(lausanne)

    assertNull(vm.uiState.value.common.location.selectedPlaceId)
    assertEquals(lausanne, vm.uiState.value.common.location.pickedPlace)
  }

  @Test
  fun uploadedItemHasThePickedSavedPlace() {
    circleWithPlaces(home)
    val vm = readyViewModel()
    vm.selectPlace("home")
    vm.uploadMedia()
    assertEquals(GeoLocation(46.5, 6.6, placeName = "Home"), captureUploadedItem().location)
  }

  @Test
  fun uploadInProgressDisablesFormAndIgnoresSecondSubmission() {
    val uploadReply = CompletableDeferred<Unit>()
    coEvery { mediaRepo.addMedia(any(), any(), any()) } coAnswers { uploadReply.await() }
    val vm = readyViewModel()

    vm.uploadMedia()
    assertTrue(vm.uiState.value.common.isUploading)
    vm.uploadMedia()
    coVerify(exactly = 1) { mediaRepo.addMedia(any(), any(), any()) }

    uploadReply.complete(Unit)
    assertFalse(vm.uiState.value.common.isUploading)
  }

  // ---------- add person ----------

  @Test
  fun onAddPersonOpensDialog() {
    val vm = createViewModel()

    vm.onAddPerson()

    assertTrue(vm.uiState.value.common.isAddPersonOpen)
  }

  @Test
  fun dismissAddPersonClosesDialogAndClearsDraft() {
    val vm = createViewModel()
    vm.onAddPerson()
    vm.setPersonFirstName("Anne")
    vm.setPersonLastName("Dupont")
    vm.setPersonNickname("Nana")

    vm.dismissAddPerson()

    assertFalse(vm.uiState.value.common.isAddPersonOpen)
    assertEquals("", vm.uiState.value.common.personFirstName)
    assertEquals("", vm.uiState.value.common.personLastName)
    assertEquals("", vm.uiState.value.common.personNickname)
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
    assertEquals(UiText.Dynamic("Anne"), vm.involvedOptions.value["person1"])
    assertTrue("person1" in vm.uiState.value.common.shownIds)
    assertFalse(vm.uiState.value.common.isAddPersonOpen)
    assertFalse(vm.uiState.value.common.isAddingPerson)
  }

  @Test
  fun blankPersonNameDoesNotCallRepository() {
    val vm = createViewModel()
    vm.onAddPerson()
    vm.setPersonFirstName("  ")

    vm.addPerson()

    coVerify(exactly = 0) { circleRepo.addPerson(any(), any()) }
    assertEquals(
        MediaViewModelErrorMessage.FIRST_NAME_REQUIRED.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
    assertTrue(vm.uiState.value.common.isAddPersonOpen)
  }

  @Test
  fun personNameWithDigitsIsRefusedWithErrorUnderTheFieldAndNotSaved() {
    loadCircle(CareCircle(id = "circle1"))
    val vm = createViewModel()
    vm.onAddPerson()
    vm.setPersonFirstName("Anne")
    vm.setPersonNickname("Nana2")

    val common = vm.uiState.value.common
    assertEquals(UiText.Resource(R.string.inputErrorNameFormat), common.personNicknameError)
    assertNull(common.personFirstNameError)
    assertFalse(common.canAddPerson)

    vm.addPerson()

    coVerify(exactly = 0) { circleRepo.addPerson(any(), any()) }
    assertEquals(
        MediaViewModelErrorMessage.INVALID_PERSON_NAME.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
  }

  @Test
  fun unfinishedPersonNameShowsItsErrorOnlyOnceAddIsTapped() {
    loadCircle(CareCircle(id = "circle1"))
    val vm = createViewModel()
    vm.onAddPerson()
    vm.setPersonFirstName("Anne-")

    assertNull(vm.uiState.value.common.personFirstNameError)
    assertTrue(vm.uiState.value.common.canAddPerson)

    vm.addPerson()

    val common = vm.uiState.value.common
    coVerify(exactly = 0) { circleRepo.addPerson(any(), any()) }
    assertEquals(UiText.Resource(R.string.inputErrorNameFormat), common.personFirstNameError)
    assertFalse(common.canAddPerson)
    assertTrue(common.isAddPersonOpen)

    vm.dismissAddPerson()
    vm.onAddPerson()
    vm.setPersonFirstName("Anne-")

    assertNull(vm.uiState.value.common.personFirstNameError)
  }

  @Test
  fun typedTextStopsAtEachFieldsMaxLength() {
    val vm = createViewModel()

    vm.setDescription("a".repeat(InputValidation.DESCRIPTION_MAX_LENGTH + 1))
    vm.setPlaceQuery("b".repeat(InputValidation.PLACE_QUERY_MAX_LENGTH + 1))
    vm.setPersonFirstName("c".repeat(InputValidation.NAME_MAX_LENGTH + 1))

    val common = vm.uiState.value.common
    assertEquals(InputValidation.DESCRIPTION_MAX_LENGTH, common.description.length)
    assertEquals(InputValidation.PLACE_QUERY_MAX_LENGTH, common.location.query.length)
    assertEquals(InputValidation.NAME_MAX_LENGTH, common.personFirstName.length)
  }

  @Test
  fun descriptionIsSavedTrimmedAndWithoutControlCharacters() {
    loadCircle(CareCircle(id = "circle1"))
    val vm = readyViewModel(description = "  Beach\u0000 day\r\nwith Nana \n")

    vm.uploadMedia()

    assertEquals("Beach day\nwith Nana", captureUploadedItem().description)
  }

  @Test
  fun failedAddPersonKeepsDraftAndReportsError() {
    coEvery { circleRepo.addPerson(any(), any()) } returns Result.failure(IOException("offline"))
    val vm = createViewModel()
    vm.onAddPerson()
    vm.setPersonFirstName("Anne")

    vm.addPerson()

    assertEquals(
        MediaViewModelErrorMessage.COULD_NOT_ADD_PERSON.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
    assertEquals("Anne", vm.uiState.value.common.personFirstName)
    assertTrue(vm.uiState.value.common.isAddPersonOpen)
    assertFalse(vm.uiState.value.common.isAddingPerson)
  }

  @Test
  fun addPersonWithoutLoadedCircleReportsRetryError() {
    coEvery { circleRepo.getCircle(any()) } returns Result.failure(IOException("offline"))
    val vm = createViewModel()
    vm.setPersonFirstName("Anne")

    vm.addPerson()

    assertEquals(
        MediaViewModelErrorMessage.STILL_LOADING.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
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
    assertEquals("", vm.uiState.value.common.personFirstName)
    assertEquals("", vm.uiState.value.common.personLastName)
    assertEquals("", vm.uiState.value.common.personNickname)
    assertTrue("person1" in vm.uiState.value.common.shownIds)
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
    assertTrue(vm.uiState.value.common.isAddingPerson)
    vm.dismissAddPerson()
    assertTrue(vm.uiState.value.common.isAddPersonOpen)
    assertEquals("Anne", vm.uiState.value.common.personFirstName)
    vm.addPerson()
    coVerify(exactly = 1) { circleRepo.addPerson(any(), any()) }

    personReply.complete(Result.success(Person(id = "person1", firstName = "Anne")))
    assertFalse(vm.uiState.value.common.isAddingPerson)
    assertFalse(vm.uiState.value.common.isAddPersonOpen)
  }

  @Test
  fun addPersonFailureWithoutMessageUsesFallbackError() {
    coEvery { circleRepo.addPerson(any(), any()) } returns Result.failure(IOException())
    val vm = createViewModel()
    vm.setPersonFirstName("Anne")

    vm.addPerson()

    assertEquals(
        MediaViewModelErrorMessage.COULD_NOT_ADD_PERSON.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
    assertFalse(vm.uiState.value.common.isAddingPerson)
  }

  @Test
  fun thrownAddPersonFailureClearsSavingStateAndKeepsDraft() {
    coEvery { circleRepo.addPerson(any(), any()) } throws IOException("broken")
    val vm = createViewModel()
    vm.onAddPerson()
    vm.setPersonFirstName("Anne")

    vm.addPerson()

    assertEquals(
        MediaViewModelErrorMessage.COULD_NOT_ADD_PERSON.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
    assertEquals("Anne", vm.uiState.value.common.personFirstName)
    assertTrue(vm.uiState.value.common.isAddPersonOpen)
    assertFalse(vm.uiState.value.common.isAddingPerson)
  }

  // ---------- hooks supplied by the subclass ----------

  // NEW TEST
  @Test
  fun uploadedItemHasTheGeneratedId() {
    readyViewModel().uploadMedia()
    assertEquals("media1", captureUploadedItem().id)
  }

  // NEW TEST
  @Test
  fun stillLoadingCircleIsReportedBeforeMissingContent() {
    coEvery { circleRepo.getCircle(any()) } returns Result.failure(IOException("boom"))
    val vm = createViewModel()
    vm.uploadMedia()
    assertEquals(
        MediaViewModelErrorMessage.STILL_LOADING.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
  }

  // NEW TEST
  @Test
  fun missingUserIsReportedBeforeMissingContent() {
    val vm = createViewModel()
    signIn(null)
    vm.uploadMedia()
    assertEquals(
        MediaViewModelErrorMessage.UNVERIFIED.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
  }

  // NEW TEST
  @Test
  fun missingContentIsReportedBeforeBlankDescription() {
    val vm = createViewModel()
    vm.setDate(MemoryDate(2024, Month.MAY, 12))
    vm.uploadMedia()
    assertEquals(FakeMediaViewModel.CONTENT_MISSING, vm.uiState.value.common.errorMsgRes)
  }

  // NEW TEST
  @Test
  fun mediaWithoutFileIsUploadedWithoutFile() {
    val vm = createViewModel(requiresContent = false)
    vm.setDescription("A description")
    vm.setDate(MemoryDate(2024, Month.MAY, 12))
    vm.uploadMedia()
    coVerify(exactly = 1) { mediaRepo.addMedia(any(), any(), null) }
  }
}

/** The state of [FakeMediaViewModel]: the common fields, and a file standing for the content. */
private data class FakeMediaUIState(
    override val common: CommonMediaUIState = CommonMediaUIState(),
    val contentFile: File? = null,
) : MediaUIState<FakeMediaUIState> {
  override val canUpload: Boolean
    get() = contentFile != null

  override fun withCommon(common: CommonMediaUIState): FakeMediaUIState = copy(common = common)
}

/**
 * The smallest concrete [MediaViewModel], for testing what all upload ViewModels share. It makes a
 * [MediaItem.Text] and, unless [requiresContent] is false, needs a file before it uploads.
 */
private class FakeMediaViewModel(
    mediaRepo: MediaRepository,
    authRepo: AuthRepository,
    profileRepo: ProfileRepository,
    circleRepo: CareCircleRepository,
    placeRepo: PlaceSearchRepository,
    private val requiresContent: Boolean = true,
) :
    MediaViewModel<FakeMediaUIState>(
        initialState = FakeMediaUIState(),
        mediaRepo = mediaRepo,
        authRepo = authRepo,
        profileRepo = profileRepo,
        circleRepo = circleRepo,
        placeRepo = placeRepo,
    ) {

  fun setContent(file: File) {
    _uiState.update { it.copy(contentFile = file) }
  }

  override fun checkContent(state: FakeMediaUIState): Int? =
      if (requiresContent && state.contentFile == null) CONTENT_MISSING else null

  override fun fileOf(state: FakeMediaUIState): File? = state.contentFile

  override fun createItem(
      id: String,
      state: FakeMediaUIState,
      authorId: String,
      circle: CareCircle,
      date: MemoryDate,
      shownIds: List<String>,
  ): MediaItem =
      MediaItem.Text(
          id = id,
          authorId = authorId,
          description = state.common.description,
          date = date,
          location = state.common.location.pickedPlace,
          shownIds = shownIds,
      )

  companion object {
    val CONTENT_MISSING = R.string.uploadPictureErrorSelect
  }
}
