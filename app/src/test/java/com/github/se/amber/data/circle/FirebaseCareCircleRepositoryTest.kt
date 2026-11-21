// Written by Neha Chakraborty and Claude Code
// Modified by MeiliV, with assistance from OpenAI Codex

package com.github.se.amber.data.circle

import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.location.Place
import com.github.se.amber.model.location.TestPlaces.LAUSANNE
import com.github.se.amber.model.user.Person
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class FirebaseCareCircleRepositoryTest {
  private val circleId = "circle-456"
  private val circle =
      CareCircle(
          id = circleId,
          name = "Family",
          memberIds = listOf("caregiver-123"),
          createdBy = "caregiver-123",
          createdAt = 1_700_000_000_000L,
      )

  private lateinit var firestore: FirebaseFirestore
  private lateinit var circles: CollectionReference
  private lateinit var circleDocument: DocumentReference
  private lateinit var repository: FirebaseCareCircleRepository

  @Before
  fun setUp() {
    firestore = mockk()
    circles = mockk()
    circleDocument = mockk()

    every { firestore.collection("careCircles") } returns circles
    every { circles.document(circleId) } returns circleDocument

    repository = FirebaseCareCircleRepository(firestore)
  }

  @Test
  fun getCircle_returnsStoredCircle() = runTest {
    stubSnapshot(circleSnapshot(circle))

    val result = repository.getCircle(circleId)

    assertEquals(circle, result.getOrThrow())
    verify(exactly = 1) { circles.document(circleId) }
  }

  @Test
  fun getCircle_takesIdFromDocumentIdRatherThanStoredFields() = runTest {
    stubSnapshot(circleSnapshot(circle.copy(id = "")))

    val result = repository.getCircle(circleId)

    assertEquals(circleId, result.getOrThrow().id)
  }

  @Test
  fun getCircle_rejectsBlankIdBeforeAccessingFirestore() = runTest {
    val result = repository.getCircle("  ")

    assertFailure<CareCircleError.InvalidCircleId>(result)
    verify(exactly = 0) { firestore.collection(any()) }
  }

  @Test
  fun getCircle_rejectsUntrimmedIdBeforeAccessingFirestore() = runTest {
    val result = repository.getCircle(" $circleId ")

    assertFailure<CareCircleError.InvalidCircleId>(result)
    verify(exactly = 0) { firestore.collection(any()) }
  }

  @Test
  fun getCircle_rejectsIdWithSlashBeforeAccessingFirestore() = runTest {
    val result = repository.getCircle("$circleId/media/m1")

    assertFailure<CareCircleError.InvalidCircleId>(result)
    verify(exactly = 0) { firestore.collection(any()) }
  }

  @Test
  fun getCircle_reportsMissingDocumentAsNotFound() = runTest {
    stubSnapshot(circleSnapshot(circle = null, exists = false))

    val result = repository.getCircle(circleId)

    assertFailure<CareCircleError.NotFound>(result)
  }

  @Test
  fun getCircle_reportsNullMappedObjectAsNotFound() = runTest {
    stubSnapshot(circleSnapshot(circle = null))

    val result = repository.getCircle(circleId)

    assertFailure<CareCircleError.NotFound>(result)
  }

  @Test
  fun getCircle_wrapsDecodingFailure() = runTest {
    val source = RuntimeException("Could not deserialize object")
    stubSnapshot(
        mockk {
          every { exists() } returns true
          every { toObject(CareCircle::class.java) } throws source
        }
    )

    val result = repository.getCircle(circleId)

    assertUnknownFailure(result, source)
  }

  @Test
  fun getCircle_rethrowsCancellationFromCancelledTask() = runTest {
    // A cancelled task, so the cancellation comes from await() rather than from get() itself.
    val cancelled =
        mockk<Task<DocumentSnapshot>> {
          every { isComplete } returns true
          every { isCanceled } returns true
          every { exception } returns null
        }
    every { circleDocument.get() } returns cancelled

    try {
      repository.getCircle(circleId)
      fail("Cancellation should be rethrown")
    } catch (_: CancellationException) {}
  }

  @Test
  fun getCircle_mapsNotFoundFirestoreFailure() = runTest {
    stubFailure(FirebaseFirestoreException("missing", FirebaseFirestoreException.Code.NOT_FOUND))

    val result = repository.getCircle(circleId)

    assertFailure<CareCircleError.NotFound>(result)
  }

  @Test
  fun getCircle_mapsPermissionDeniedFirestoreFailure() = runTest {
    stubFailure(
        FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
    )

    val result = repository.getCircle(circleId)

    assertFailure<CareCircleError.PermissionDenied>(result)
  }

  @Test
  fun getCircle_mapsUnauthenticatedFirestoreFailure() = runTest {
    stubFailure(
        FirebaseFirestoreException("signed out", FirebaseFirestoreException.Code.UNAUTHENTICATED)
    )

    val result = repository.getCircle(circleId)

    assertFailure<CareCircleError.NotAuthenticated>(result)
  }

  @Test
  fun getCircle_mapsUnavailableFirestoreFailureToNetwork() = runTest {
    stubFailure(FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE))

    val result = repository.getCircle(circleId)

    assertFailure<CareCircleError.Network>(result)
  }

  @Test
  fun getCircle_mapsDeadlineExceededFirestoreFailureToNetwork() = runTest {
    stubFailure(
        FirebaseFirestoreException("timed out", FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)
    )

    val result = repository.getCircle(circleId)

    assertFailure<CareCircleError.Network>(result)
  }

  @Test
  fun getCircle_wrapsUnexpectedFirestoreFailure() = runTest {
    val source = FirebaseFirestoreException("failed", FirebaseFirestoreException.Code.INTERNAL)
    stubFailure(source)

    val result = repository.getCircle(circleId)

    assertUnknownFailure(result, source)
  }

  @Test
  fun getCircle_wrapsUnexpectedFailure() = runTest {
    val source = IllegalStateException("failed")
    stubFailure(source)

    val result = repository.getCircle(circleId)

    assertUnknownFailure(result, source)
  }

  @Test
  fun addPerson_writesOneEntryUnderPeopleAndReturnsSavedPerson() = runTest {
    val personDocument = mockk<DocumentReference> { every { id } returns "person-789" }
    val writtenField = slot<FieldPath>()
    val writtenPerson = slot<Person>()
    every { circles.document() } returns personDocument
    every { circleDocument.update(capture(writtenField), capture(writtenPerson)) } returns
        Tasks.forResult(null)

    val result = repository.addPerson(circleId, Person(firstName = "Anne"))

    val savedPerson = result.getOrThrow()
    // Check that the targeted update happened
    assertEquals(FieldPath.of("people", "person-789"), writtenField.captured)
    assertEquals(savedPerson, writtenPerson.captured)
    assertEquals("person-789", savedPerson.id)

    // Check that circleDocument.set(...) was never called
    // It's indeed the case that would wipe the whole map if called
    verify(exactly = 0) { circleDocument.set(any()) }
    verify(exactly = 0) { circleDocument.set(any(), any()) }
  }

  @Test
  fun addPerson_trimsNamesAndClearsBlankNickname() = runTest {
    val writtenPerson = slot<Person>()
    stubPersonWrite(writtenPerson)

    val result =
        repository.addPerson(
            circleId,
            Person(firstName = "  Anne  ", lastName = "  Dupont  ", nickname = "  "),
        )

    assertEquals("Anne", result.getOrThrow().firstName)
    assertEquals("Dupont", writtenPerson.captured.lastName)
    assertNull(writtenPerson.captured.nickname)
  }

  @Test
  fun addPerson_trimsNonBlankNickname() = runTest {
    val writtenPerson = slot<Person>()
    stubPersonWrite(writtenPerson)

    val saved =
        repository
            .addPerson(
                circleId,
                Person(firstName = "Anne", nickname = " Nana "),
            )
            .getOrThrow()

    assertEquals("Nana", saved.nickname)
    assertEquals("Nana", writtenPerson.captured.nickname)
  }

  @Test
  fun addPerson_replacesCallerProvidedIdWithGeneratedId() = runTest {
    val writtenPerson = slot<Person>()
    stubPersonWrite(writtenPerson)

    val result = repository.addPerson(circleId, Person(id = "caller-id", firstName = "Anne"))

    assertEquals("person-789", result.getOrThrow().id)
    assertEquals("person-789", writtenPerson.captured.id)
  }

  @Test
  fun addPerson_rejectsBlankCircleIdBeforeAccessingFirestore() = runTest {
    assertFailure<CareCircleError.InvalidCircleId>(
        repository.addPerson("  ", Person(firstName = "Anne"))
    )
    verify(exactly = 0) { firestore.collection(any()) }
  }

  @Test
  fun addPerson_rejectsUntrimmedCircleIdBeforeAccessingFirestore() = runTest {
    assertFailure<CareCircleError.InvalidCircleId>(
        repository.addPerson(" $circleId ", Person(firstName = "Anne"))
    )
    verify(exactly = 0) { firestore.collection(any()) }
  }

  @Test
  fun addPerson_rejectsCircleIdWithSlashBeforeAccessingFirestore() = runTest {
    assertFailure<CareCircleError.InvalidCircleId>(
        repository.addPerson("$circleId/other", Person(firstName = "Anne"))
    )
    verify(exactly = 0) { firestore.collection(any()) }
  }

  @Test
  fun addPerson_rejectsBlankFirstNameBeforeAccessingFirestore() = runTest {
    assertFailure<CareCircleError.InvalidPersonName>(
        repository.addPerson(circleId, Person(firstName = "  "))
    )
    verify(exactly = 0) { firestore.collection(any()) }
  }

  @Test
  fun addPerson_mapsPermissionDeniedFailure() = runTest {
    stubPersonWriteFailure(
        FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
    )

    assertFailure<CareCircleError.PermissionDenied>(
        repository.addPerson(circleId, Person(firstName = "Anne"))
    )
  }

  @Test
  fun addPerson_mapsUnavailableFailureToNetwork() = runTest {
    // Mocks .update to return an already failed task containing [...].UNAVAILABLE
    // addPerson receives the exception and maps it to a Network error
    // This test does not simulate offline behavior
    stubPersonWriteFailure(
        FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
    )

    assertFailure<CareCircleError.Network>(
        repository.addPerson(circleId, Person(firstName = "Anne"))
    )
  }

  @Test
  fun addPerson_wrapsUnexpectedFailure() = runTest {
    val source = IllegalStateException("write failed")
    stubPersonWriteFailure(source)

    val result = repository.addPerson(circleId, Person(firstName = "Anne"))

    assertFalse(result.isSuccess)
    val error = result.exceptionOrNull()
    assertTrue(error is CareCircleError.Unknown)
    assertSame(source, (error as CareCircleError.Unknown).source)
  }

  @Test
  fun addPerson_rethrowsCancellationFromCancelledWrite() = runTest {
    val cancelled =
        mockk<Task<Void>> {
          every { isComplete } returns true
          every { isCanceled } returns true
          every { exception } returns null
        }
    every { circles.document() } returns mockk { every { id } returns "person-789" }
    every { circleDocument.update(any<FieldPath>(), any()) } returns cancelled

    try {
      repository.addPerson(circleId, Person(firstName = "Anne"))
      fail("Cancellation should be rethrown")
    } catch (_: CancellationException) {}
  }

  private fun stubPersonWrite(writtenPerson: CapturingSlot<Person>) {
    every { circles.document() } returns mockk { every { id } returns "person-789" }
    every { circleDocument.update(any<FieldPath>(), capture(writtenPerson)) } returns
        Tasks.forResult(null)
  }

  private fun stubPersonWriteFailure(error: Exception) {
    every { circles.document() } returns mockk { every { id } returns "person-789" }
    every { circleDocument.update(any<FieldPath>(), any()) } returns Tasks.forException(error)
  }

  @Test
  fun addPlace_writesOneEntryUnderPlacesWithoutWaitingForTheServer() = runTest {
    val writtenField = slot<FieldPath>()
    val writtenPlace = slot<Place>()
    every { circles.document() } returns mockk { every { id } returns "place-1" }
    // The server never answers, as offline: the place must still be saved
    every { circleDocument.update(capture(writtenField), capture(writtenPlace)) } returns
        TaskCompletionSource<Void>().task

    val saved = repository.addPlace(circleId, home.copy(id = "caller-id", name = "  Home  "))

    assertEquals(home.copy(id = "place-1"), saved.getOrThrow())
    assertEquals(FieldPath.of("places", "place-1"), writtenField.captured)
    assertEquals(home.copy(id = "place-1"), writtenPlace.captured)
    verify(exactly = 0) { circleDocument.set(any()) }
  }

  @Test
  fun placeWrites_rejectInvalidInputWithoutWriting() = runTest {
    every { circles.document() } returns mockk { every { id } returns "place-1" }

    assertFailure<CareCircleError.InvalidCircleId>(repository.addPlace(" ", home))
    assertFailure<CareCircleError.InvalidCircleId>(repository.updatePlace("a/b", home))
    assertFailure<CareCircleError.InvalidCircleId>(repository.deletePlace("", home.id))
    assertFailure<CareCircleError.InvalidPlace>(
        repository.addPlace(circleId, home.copy(name = " "))
    )
    // A place known only by its name can't be walked to
    assertFailure<CareCircleError.InvalidPlace>(
        repository.updatePlace(circleId, home.copy(location = GeoLocation(placeName = "Home")))
    )
    assertFailure<CareCircleError.InvalidPlaceId>(
        repository.updatePlace(circleId, home.copy(id = ""))
    )
    assertFailure<CareCircleError.InvalidPlaceId>(repository.deletePlace(circleId, "a/b"))
    verify(exactly = 0) { circleDocument.update(any<FieldPath>(), any()) }
  }

  @Test
  fun updatePlace_writesThePlaceUnderItsOwnIdWithTrimmedName() = runTest {
    val writtenField = slot<FieldPath>()
    val writtenPlace = slot<Place>()
    every { circleDocument.update(capture(writtenField), capture(writtenPlace)) } returns
        TaskCompletionSource<Void>().task

    val saved = repository.updatePlace(circleId, home.copy(name = " My home "))

    assertEquals(home.copy(name = "My home"), saved.getOrThrow())
    assertEquals(FieldPath.of("places", home.id), writtenField.captured)
    assertEquals(saved.getOrThrow(), writtenPlace.captured)
  }

  @Test
  fun deletePlace_removesOneEntryWithoutWaitingForTheServer() = runTest {
    every { circleDocument.update(any<FieldPath>(), any()) } returns
        TaskCompletionSource<Void>().task

    assertTrue(repository.deletePlace(circleId, home.id).isSuccess)

    verify { circleDocument.update(FieldPath.of("places", home.id), FieldValue.delete()) }
  }

  @Test
  fun placeWrites_wrapAWriteThatCannotStart() = runTest {
    val source = IllegalStateException("Firestore was terminated")
    every { circleDocument.update(any<FieldPath>(), any()) } throws source

    val result = repository.deletePlace(circleId, home.id)

    assertSame(source, (result.exceptionOrNull() as CareCircleError.Unknown).source)
  }

  @Test
  fun observePlaces_emitsEachChangeWithIdsFromKeysAndStopsListening() = runTest {
    val listener = slot<EventListener<DocumentSnapshot>>()
    val registration = mockk<ListenerRegistration>(relaxed = true)
    every { circleDocument.addSnapshotListener(capture(listener)) } returns registration
    val emitted = mutableListOf<List<Place>>()
    val collecting =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          repository.observePlaces(circleId).toList(emitted)
        }

    // Stored under "home" with a stale id field: the key wins
    listener.captured.onEvent(placesSnapshot(mapOf(home.id to home.copy(id = "stale"))), null)
    runCurrent()
    listener.captured.onEvent(placesSnapshot(emptyMap()), null)
    runCurrent()
    // The circle no longer exists: no places
    listener.captured.onEvent(placesSnapshot(mapOf(home.id to home)), null)
    runCurrent()
    listener.captured.onEvent(
        mockk { every { toObject(CareCircle::class.java) } returns null },
        null,
    )
    runCurrent()
    collecting.cancel()

    assertEquals(listOf(listOf(home), emptyList(), listOf(home), emptyList()), emitted)
    verify { registration.remove() }
  }

  @Test
  fun observePlaces_endsWithCareCircleErrors() = runTest {
    val listener = slot<EventListener<DocumentSnapshot>>()
    every { circleDocument.addSnapshotListener(capture(listener)) } returns mockk(relaxed = true)
    val unreadable = mockk<DocumentSnapshot>()
    val source = RuntimeException("bad field")
    every { unreadable.toObject(CareCircle::class.java) } throws source

    // How the flow ends once the listener gets this event
    fun endingAfter(event: () -> Unit): Throwable? {
      var ending: Throwable? = null
      backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        ending = runCatching { repository.observePlaces(circleId).toList() }.exceptionOrNull()
      }
      event()
      runCurrent()
      return ending
    }

    val denied =
        FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
    assertSame(
        CareCircleError.PermissionDenied,
        endingAfter { listener.captured.onEvent(null, denied) },
    )
    val unknown = endingAfter { listener.captured.onEvent(unreadable, null) }
    assertSame(source, (unknown as CareCircleError.Unknown).source)
    assertSame(
        CareCircleError.InvalidCircleId,
        runCatching { repository.observePlaces(" ").toList() }.exceptionOrNull(),
    )
  }

  private val home = Place(id = "home", name = "Home", location = LAUSANNE)

  private fun placesSnapshot(places: Map<String, Place>): DocumentSnapshot = mockk {
    every { toObject(CareCircle::class.java) } returns circle.copy(places = places)
  }

  private inline fun <reified T : Throwable> assertFailure(result: Result<*>) {
    assertFalse(result.isSuccess)
    assertTrue(result.exceptionOrNull() is T)
  }

  private fun stubSnapshot(snapshot: DocumentSnapshot) {
    every { circleDocument.get() } returns Tasks.forResult(snapshot)
  }

  private fun stubFailure(error: Exception) {
    every { circleDocument.get() } returns Tasks.forException(error)
  }

  private fun circleSnapshot(circle: CareCircle?, exists: Boolean = true): DocumentSnapshot =
      mockk {
        every { this@mockk.exists() } returns exists
        every { id } returns circleId
        every { toObject(CareCircle::class.java) } returns circle
      }

  private fun assertUnknownFailure(result: Result<CareCircle>, source: Throwable) {
    assertFalse(result.isSuccess)
    val error = result.exceptionOrNull()
    assertTrue(error is CareCircleError.Unknown)
    assertSame(source, (error as CareCircleError.Unknown).source)
  }
}
