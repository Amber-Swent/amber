// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment

import com.github.se.amber.model.appointment.Appointment
import com.github.se.amber.model.appointment.AppointmentValidationError
import com.github.se.amber.model.location.GeoLocation
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseAppointmentRepositoryTest {
  private val circleId = "circle-1"
  private val appointmentId = "appointment-1"
  private val uid = "caregiver-1"
  private val now = 1_750_000_000_000L
  private val start = 1_800_000_000_000L

  // Written out from the 45 minutes of the fixture, so a wrong computation fails.
  private val end = start + 45.minutes.inWholeMilliseconds

  private val appointment =
      Appointment(
          id = appointmentId,
          title = "Physio",
          notes = "Bring the X-rays",
          startAt = start,
          durationMinutes = 45,
          location = GeoLocation(46.52, 6.57, "Clinic"),
          attendeeIds = listOf(uid),
      )

  private lateinit var firestore: FirebaseFirestore
  private lateinit var auth: FirebaseAuth
  private lateinit var circles: CollectionReference
  private lateinit var document: DocumentReference
  private lateinit var repository: FirebaseAppointmentRepository

  @Before
  fun setUp() {
    firestore = mockk()
    auth = mockk()
    circles = mockk()
    document = mockk()
    val circleDocument = mockk<DocumentReference>()
    val appointments = mockk<CollectionReference>()

    every { firestore.collection("careCircles") } returns circles
    every { circles.document(circleId) } returns circleDocument
    every { circleDocument.collection("appointments") } returns appointments
    every { appointments.document(appointmentId) } returns document
    signIn(uid)
    stubWrites(Tasks.forResult(null))

    repository = FirebaseAppointmentRepository(firestore, auth, now = { now })
  }

  @Test
  fun newIdComesFromFirestoreWithoutWriting() {
    every { circles.document() } returns mockk { every { id } returns "generated-id" }

    assertEquals("generated-id", repository.newId())
    verifyNoWrite()
  }

  @Test
  fun addChecksItsInputInOrderBeforeWriting() = runTest {
    signIn(null)
    val invalid = appointment.copy(id = "a/b", title = "")

    // Each call has every problem left, so only the first one in the order is reported.
    assertError(AppointmentError.InvalidCircleId(), repository.addAppointment("", invalid))
    assertError(
        AppointmentError.InvalidAppointmentId(),
        repository.addAppointment(circleId, invalid),
    )
    assertError(
        AppointmentError.InvalidAppointment(AppointmentValidationError.BLANK_TITLE),
        repository.addAppointment(circleId, invalid.copy(id = appointmentId)),
    )
    assertError(
        AppointmentError.NotAuthenticated(),
        repository.addAppointment(circleId, appointment),
    )

    verify(exactly = 0) { firestore.collection(any()) }
  }

  @Test
  fun addWritesTheFieldsTheRepositorySetsAndReturnsWhatItWrote() = runTest {
    val written = slot<Any>()
    every { document.set(capture(written)) } returns Tasks.forResult(null)
    // The spaces are trimmed, and the caller's values for the other fields are ignored.
    val fromCaller =
        appointment.copy(
            title = "  Physio ",
            notes = " Bring the X-rays  ",
            createdBy = "someone-else",
            createdAt = 1L,
            updatedBy = "someone-else",
            updatedAt = 1L,
            lastEndAt = 2L,
        )

    val saved = repository.addAppointment(circleId, fromCaller).getOrThrow()

    val expected =
        appointment.copy(
            createdBy = uid,
            createdAt = now,
            updatedBy = uid,
            updatedAt = now,
            lastEndAt = end,
        )
    assertEquals(expected, saved)
    assertEquals(expected, written.captured)
  }

  @Test
  fun aWriteReturnsBeforeTheServerAnswersAndALaterRefusalIsReported() = runTest {
    val failures = collectFailures()
    // This task completes only when the test says so, like a write made offline.
    val pending = TaskCompletionSource<Void>()
    every { document.set(any()) } returns pending.task

    assertEquals(appointmentId, repository.addAppointment(circleId, appointment).getOrThrow().id)
    assertEquals(emptyList<AppointmentWriteFailure>(), failures)

    pending.setException(firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED))
    assertEquals(
        listOf(
            AppointmentWriteFailure(
                appointmentId,
                AppointmentWrite.ADD,
                AppointmentError.PermissionDenied(),
            )
        ),
        failures,
    )
  }

  @Test
  fun updateWritesOnlyTheEditableFieldsAndTheUpdateFields() = runTest {
    val written = slot<Map<String, Any>>()
    every { document.update(capture(written)) } returns Tasks.forResult(null)
    val stored =
        appointment.copy(createdBy = "creator", createdAt = 5L, updatedAt = 6L, lastEndAt = end)
    val newStart = start + 2.hours.inWholeMilliseconds
    // Written out from the new start and the 90 minutes, so the end is computed again.
    val newEnd = newStart + 90.minutes.inWholeMilliseconds

    val saved =
        repository
            .updateAppointment(
                circleId,
                stored.copy(title = " Dentist ", startAt = newStart, durationMinutes = 90),
            )
            .getOrThrow()

    val expectedFields =
        mapOf(
            "title" to "Dentist",
            "notes" to appointment.notes,
            "startAt" to newStart,
            "durationMinutes" to 90,
            "location" to appointment.location,
            "attendeeIds" to appointment.attendeeIds,
            "lastEndAt" to newEnd,
            "updatedBy" to uid,
            "updatedAt" to now,
        )
    assertEquals(expectedFields, written.captured)
    // The creator fields are not written, and the returned appointment keeps them.
    assertEquals(
        stored.copy(
            title = "Dentist",
            startAt = newStart,
            durationMinutes = 90,
            lastEndAt = newEnd,
            updatedBy = uid,
            updatedAt = now,
        ),
        saved,
    )
    verify(exactly = 0) { document.set(any()) }
  }

  @Test
  fun updateWritesNullToClearTheDurationAndTheLocation() = runTest {
    val written = slot<Map<String, Any>>()
    every { document.update(capture(written)) } returns Tasks.forResult(null)

    repository.updateAppointment(
        circleId,
        appointment.copy(durationMinutes = null, location = null),
    )

    assertTrue(written.captured.containsKey("durationMinutes"))
    assertEquals(null, written.captured["durationMinutes"])
    assertTrue(written.captured.containsKey("location"))
    assertEquals(null, written.captured["location"])
    // With no duration, the default of 60 minutes gives the end.
    assertEquals(start + 60.minutes.inWholeMilliseconds, written.captured["lastEndAt"])
  }

  @Test
  fun updateChecksItsInputLikeAdd() = runTest {
    assertError(
        AppointmentError.InvalidCircleId(),
        repository.updateAppointment("", appointment),
    )
    assertError(
        AppointmentError.InvalidAppointmentId(),
        repository.updateAppointment(circleId, appointment.copy(id = "a/b")),
    )
    assertError(
        AppointmentError.InvalidAppointment(AppointmentValidationError.BLANK_TITLE),
        repository.updateAppointment(circleId, appointment.copy(title = "")),
    )
    signIn(null)
    assertError(
        AppointmentError.NotAuthenticated(),
        repository.updateAppointment(circleId, appointment),
    )

    verifyNoWrite()
  }

  @Test
  fun deleteChecksItsInputThenDeletesTheRightDocument() = runTest {
    assertError(
        AppointmentError.InvalidCircleId(),
        repository.deleteAppointment("a/b", appointmentId),
    )
    assertError(
        AppointmentError.InvalidAppointmentId(),
        repository.deleteAppointment(circleId, " "),
    )
    signIn(null)
    assertError(
        AppointmentError.NotAuthenticated(),
        repository.deleteAppointment(circleId, appointmentId),
    )
    verifyNoWrite()

    signIn(uid)
    assertTrue(repository.deleteAppointment(circleId, appointmentId).isSuccess)
    verify(exactly = 1) { document.delete() }
  }

  @Test
  fun eachRefusedWriteIsReportedWithItsKind() = runTest {
    val failures = collectFailures()
    stubWrites(Tasks.forException(firestoreError(FirebaseFirestoreException.Code.NOT_FOUND)))

    repository.addAppointment(circleId, appointment).getOrThrow()
    repository.updateAppointment(circleId, appointment).getOrThrow()
    repository.deleteAppointment(circleId, appointmentId).getOrThrow()

    assertEquals(
        listOf(AppointmentWrite.ADD, AppointmentWrite.UPDATE, AppointmentWrite.DELETE).map {
          AppointmentWriteFailure(appointmentId, it, AppointmentError.NotFound())
        },
        failures,
    )
  }

  @Test
  fun theSameRefusalTwiceInARowIsReportedTwice() = runTest {
    val failures = collectFailures()
    stubWrites(Tasks.forException(firestoreError(FirebaseFirestoreException.Code.NOT_FOUND)))

    repository.deleteAppointment(circleId, appointmentId).getOrThrow()
    repository.deleteAppointment(circleId, appointmentId).getOrThrow()

    val refusal =
        AppointmentWriteFailure(appointmentId, AppointmentWrite.DELETE, AppointmentError.NotFound())
    assertEquals(listOf(refusal, refusal), failures)
  }

  @Test
  fun aSlowCollectorStillGetsABurstOfRefusals() = runTest {
    // Unlike collectFailures(), this collector only runs when the test lets it, so the
    // refusals wait in the buffer meanwhile.
    val failures = mutableListOf<AppointmentWriteFailure>()
    backgroundScope.launch(StandardTestDispatcher(testScheduler)) {
      repository.writeFailures.toList(failures)
    }
    testScheduler.runCurrent()
    stubWrites(Tasks.forException(firestoreError(FirebaseFirestoreException.Code.NOT_FOUND)))

    repository.addAppointment(circleId, appointment).getOrThrow()
    repository.updateAppointment(circleId, appointment).getOrThrow()
    repository.deleteAppointment(circleId, appointmentId).getOrThrow()
    testScheduler.runCurrent()

    assertEquals(
        listOf(AppointmentWrite.ADD, AppointmentWrite.UPDATE, AppointmentWrite.DELETE),
        failures.map { it.write },
    )
  }

  @Test
  fun serverErrorsAreMappedToAppointmentErrors() = runTest {
    val failures = collectFailures()
    val unavailable = firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE)
    val unexpected = IllegalStateException("boom")
    val cases =
        listOf(
            firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED) to
                AppointmentError.PermissionDenied(),
            firestoreError(FirebaseFirestoreException.Code.NOT_FOUND) to
                AppointmentError.NotFound(),
            firestoreError(FirebaseFirestoreException.Code.UNAUTHENTICATED) to
                AppointmentError.NotAuthenticated(),
            // Firestore retries a write while offline, so this one is not a network error.
            unavailable to AppointmentError.Unknown(unavailable),
            unexpected to AppointmentError.Unknown(unexpected),
        )

    for ((error, _) in cases) {
      every { document.delete() } returns Tasks.forException(error)
      repository.deleteAppointment(circleId, appointmentId)
    }

    assertEquals(cases.map { it.second }, failures.map { it.error })
  }

  @Test
  fun aSuccessfulWriteReportsNothing() = runTest {
    val failures = collectFailures()

    repository.addAppointment(circleId, appointment).getOrThrow()
    repository.updateAppointment(circleId, appointment).getOrThrow()
    repository.deleteAppointment(circleId, appointmentId).getOrThrow()

    assertEquals(emptyList<AppointmentWriteFailure>(), failures)
  }

  @Test
  fun aFailureNobodyCollectsIsNotReplayed() = runTest {
    every { document.delete() } returns
        Tasks.forException(firestoreError(FirebaseFirestoreException.Code.NOT_FOUND))
    repository.deleteAppointment(circleId, appointmentId).getOrThrow()

    val failures = collectFailures()

    assertEquals(emptyList<AppointmentWriteFailure>(), failures)
  }

  @Test
  fun hasUnsyncedWritesIsTrueWhenTheWritesAreNotSentInTime() = runTest {
    // Never completes, like a write waiting offline
    every { firestore.waitForPendingWrites() } returns TaskCompletionSource<Void>().task

    assertTrue(repository.hasUnsyncedWrites())
    // Written out: the repository waits 2 seconds, then gives up
    assertEquals(2_000L, currentTime)

    // The wait fails when the signed-in user changes, so nothing confirms the writes were sent
    every { firestore.waitForPendingWrites() } returns
        Tasks.forException(firestoreError(FirebaseFirestoreException.Code.ABORTED))
    assertTrue(repository.hasUnsyncedWrites())
  }

  @Test
  fun hasUnsyncedWritesIsFalseOnceTheServerHasEveryWrite() = runTest {
    every { firestore.waitForPendingWrites() } returns Tasks.forResult(null)

    assertFalse(repository.hasUnsyncedWrites())
  }

  @Test
  fun aWriteThatCannotStartFailsTheCall() = runTest {
    val source = IllegalArgumentException("Unsupported field value")
    every { document.set(any()) } throws source

    assertError(AppointmentError.Unknown(source), repository.addAppointment(circleId, appointment))
  }

  @Test
  fun cancellationIsRethrown() = runTest {
    every { document.delete() } throws CancellationException("cancelled")

    val thrown = runCatching { repository.deleteAppointment(circleId, appointmentId) }

    assertTrue(thrown.exceptionOrNull() is CancellationException)
  }

  /** Collects [FirebaseAppointmentRepository.writeFailures] from now on, without delay. */
  private fun TestScope.collectFailures(): List<AppointmentWriteFailure> {
    val failures = mutableListOf<AppointmentWriteFailure>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.writeFailures.toList(failures)
    }
    return failures
  }

  /** Makes [uid] the signed-in user, or signs out with null. */
  private fun signIn(uid: String?) {
    every { auth.currentUser } returns
        uid?.let { id -> mockk<FirebaseUser> { every { this@mockk.uid } returns id } }
  }

  /** Makes every write of the appointment document return [task]. */
  private fun stubWrites(task: Task<Void>) {
    every { document.set(any()) } returns task
    every { document.update(any<Map<String, Any>>()) } returns task
    every { document.delete() } returns task
  }

  /** Checks that the appointment document was neither set, updated nor deleted. */
  private fun verifyNoWrite() {
    verify(exactly = 0) { document.set(any()) }
    verify(exactly = 0) { document.update(any<Map<String, Any>>()) }
    verify(exactly = 0) { document.delete() }
  }

  /** @return a Firestore failure with [code], as the server would send it. */
  private fun firestoreError(code: FirebaseFirestoreException.Code) =
      FirebaseFirestoreException(code.name, code)

  /** Checks that [result] failed with [expected], compared by subtype and fields. */
  private fun assertError(expected: AppointmentError, result: Result<*>) {
    assertEquals(expected, result.exceptionOrNull())
  }
}
