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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseAppointmentRepositoryTest {
  private val circleId = "circle-1"
  private val appointmentId = "appointment-1"
  private val uid = "caregiver-1"
  private val now = 1_750_000_000_000L
  private val start = 1_800_000_000_000L

  // 45 minutes after the start, written out so a wrong computation fails.
  private val end = start + 45 * 60_000L

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

    repository = FirebaseAppointmentRepository(firestore, auth) { now }
  }

  @Test
  fun newIdComesFromFirestoreWithoutWriting() {
    every { circles.document() } returns mockk { every { id } returns "generated-id" }

    assertEquals("generated-id", repository.newId())
  }

  @Test
  fun addRefusesInvalidIdsBeforeWriting() = runTest {
    for (badId in listOf("", "  ", " $circleId ", "$circleId/appointments/x")) {
      assertError(AppointmentError.InvalidCircleId(), repository.addAppointment(badId, appointment))
    }
    for (badId in listOf("", " $appointmentId", "$appointmentId/x")) {
      assertError(
          AppointmentError.InvalidAppointmentId(),
          repository.addAppointment(circleId, appointment.copy(id = badId)),
      )
    }

    verify(exactly = 0) { firestore.collection(any()) }
  }

  @Test
  fun addRefusesEachInvalidAppointmentBeforeWriting() = runTest {
    val cases =
        mapOf(
            appointment.copy(title = "   ") to AppointmentValidationError.BLANK_TITLE,
            appointment.copy(startAt = 0L) to AppointmentValidationError.MISSING_START,
            appointment.copy(durationMinutes = 24 * 60 + 1) to
                AppointmentValidationError.INVALID_DURATION,
        )

    for ((invalid, reason) in cases) {
      assertError(
          AppointmentError.InvalidAppointment(reason),
          repository.addAppointment(circleId, invalid),
      )
    }
    verify(exactly = 0) { firestore.collection(any()) }
  }

  @Test
  fun addFailsWhenNobodyIsSignedIn() = runTest {
    signIn(null)

    assertError(
        AppointmentError.NotAuthenticated(),
        repository.addAppointment(circleId, appointment),
    )
    verify(exactly = 0) { firestore.collection(any()) }
  }

  @Test
  fun checksRunInTheDocumentedOrder() = runTest {
    signIn(null)
    val invalid = appointment.copy(id = "", title = "")

    // Each call has several problems. Only the first one in the order is reported.
    assertError(AppointmentError.InvalidCircleId(), repository.addAppointment("", invalid))
    assertError(
        AppointmentError.InvalidAppointmentId(),
        repository.addAppointment(circleId, invalid),
    )
    assertError(
        AppointmentError.InvalidAppointment(AppointmentValidationError.BLANK_TITLE),
        repository.addAppointment(circleId, invalid.copy(id = appointmentId)),
    )
  }

  @Test
  fun addWritesTheFieldsTheRepositorySetsAndReturnsWhatItWrote() = runTest {
    val written = slot<Any>()
    every { document.set(capture(written)) } returns Tasks.forResult(null)
    // The caller's values for these fields are ignored.
    val fromCaller =
        appointment.copy(
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
  fun addTrimsTheTitleAndTheNotes() = runTest {
    val written = slot<Any>()
    every { document.set(capture(written)) } returns Tasks.forResult(null)

    val saved =
        repository
            .addAppointment(circleId, appointment.copy(title = "  Physio ", notes = " Bring  "))
            .getOrThrow()

    assertEquals("Physio", saved.title)
    assertEquals("Bring", saved.notes)
    assertEquals(saved, written.captured)
  }

  @Test
  fun addReturnsBeforeTheServerAnswers() = runTest {
    // This task never completes, like a write made offline.
    every { document.set(any()) } returns TaskCompletionSource<Void>().task

    val result = repository.addAppointment(circleId, appointment)

    assertEquals(appointmentId, result.getOrThrow().id)
  }

  @Test
  fun updateWritesOnlyTheEditableFieldsAndTheUpdateFields() = runTest {
    val written = slot<Map<String, Any>>()
    every { document.update(capture(written)) } returns Tasks.forResult(null)
    val stored = appointment.copy(createdBy = "creator", createdAt = 5L, updatedAt = 6L)

    val saved =
        repository.updateAppointment(circleId, stored.copy(title = " Dentist ")).getOrThrow()

    val expectedFields =
        mapOf(
            "title" to "Dentist",
            "notes" to "Bring the X-rays",
            "startAt" to start,
            "durationMinutes" to 45,
            "location" to GeoLocation(46.52, 6.57, "Clinic"),
            "attendeeIds" to listOf(uid),
            "lastEndAt" to end,
            "updatedBy" to uid,
            "updatedAt" to now,
        )
    assertEquals(expectedFields, written.captured)
    // The creator fields are not written, and the returned appointment keeps them.
    assertEquals(
        stored.copy(title = "Dentist", updatedBy = uid, updatedAt = now, lastEndAt = end),
        saved,
    )
    verify(exactly = 0) { document.set(any()) }
  }

  @Test
  fun updateComputesLastEndAtAgainFromTheNewTime() = runTest {
    val written = slot<Map<String, Any>>()
    every { document.update(capture(written)) } returns Tasks.forResult(null)
    val newStart = start + 2 * 60 * 60_000L

    val saved =
        repository
            .updateAppointment(
                circleId,
                appointment.copy(startAt = newStart, durationMinutes = 90, lastEndAt = end),
            )
            .getOrThrow()

    val newEnd = newStart + 90 * 60_000L
    assertEquals(newEnd, saved.lastEndAt)
    assertEquals(newEnd, written.captured["lastEndAt"])
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
    assertEquals(start + 60 * 60_000L, written.captured["lastEndAt"])
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

    verify(exactly = 0) { document.update(any<Map<String, Any>>()) }
  }

  @Test
  fun deleteDeletesTheRightDocument() = runTest {
    val result = repository.deleteAppointment(circleId, appointmentId)

    assertTrue(result.isSuccess)
    verify(exactly = 1) { document.delete() }
  }

  @Test
  fun deleteRefusesInvalidIdsAndASignedOutUser() = runTest {
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

    verify(exactly = 0) { document.delete() }
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
  fun aRefusalArrivingLaterIsReported() = runTest {
    val failures = collectFailures()
    val pending = TaskCompletionSource<Void>()
    every { document.delete() } returns pending.task

    repository.deleteAppointment(circleId, appointmentId).getOrThrow()
    assertEquals(emptyList<AppointmentWriteFailure>(), failures)
    pending.setException(firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED))

    assertEquals(
        listOf(
            AppointmentWriteFailure(
                appointmentId,
                AppointmentWrite.DELETE,
                AppointmentError.PermissionDenied(),
            )
        ),
        failures,
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
  fun aWriteThatCannotStartFailsTheCall() = runTest {
    val source = IllegalArgumentException("Unsupported field value")
    every { document.set(any()) } throws source

    assertError(AppointmentError.Unknown(source), repository.addAppointment(circleId, appointment))
  }

  @Test
  fun cancellationIsRethrown() = runTest {
    every { document.delete() } throws CancellationException("cancelled")

    try {
      repository.deleteAppointment(circleId, appointmentId)
      fail("Cancellation should be rethrown")
    } catch (_: CancellationException) {}
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

  /** @return a Firestore failure with [code], as the server would send it. */
  private fun firestoreError(code: FirebaseFirestoreException.Code) =
      FirebaseFirestoreException(code.name, code)

  /** Checks that [result] failed with [expected], compared by subtype and fields. */
  private fun assertError(expected: AppointmentError, result: Result<*>) {
    assertEquals(expected, result.exceptionOrNull())
  }
}
