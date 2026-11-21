// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment

import com.github.se.amber.data.Synced
import com.github.se.amber.model.appointment.Appointment
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests the reads of [FirebaseAppointmentRepository]. The writes are in its own test. */
@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseAppointmentRepositoryReadTest {
  private val circleId = "circle-1"
  private val day = LocalDate.of(2026, 11, 17)

  // Written out: 17 November 2026 at 00:00 in Zurich, the zone the repository is given.
  private val dayStart = 1_794_870_000_000L
  private val dayEnd = dayStart + 24.hours.inWholeMilliseconds

  // The physio starts last but ends first, so sorting by end or by start gives different orders.
  private val checkUp =
      Appointment(
          id = "check-up",
          title = "Check-up",
          startAt = dayStart + 8.hours.inWholeMilliseconds,
          durationMinutes = 4 * 60,
          lastEndAt = dayStart + 12.hours.inWholeMilliseconds,
      )
  private val physio =
      Appointment(
          id = "physio",
          title = "Physio",
          startAt = dayStart + 10.hours.inWholeMilliseconds,
          durationMinutes = 45,
          lastEndAt = dayStart + 10.hours.inWholeMilliseconds + 45.minutes.inWholeMilliseconds,
      )

  // Started the day before and ends during the day, so it is part of it. Only its lastEndAt lets
  // the query find it.
  private val overnight =
      Appointment(
          id = "overnight",
          title = "Night care",
          startAt = dayStart - 1.hours.inWholeMilliseconds,
          durationMinutes = 2 * 60,
          lastEndAt = dayStart + 1.hours.inWholeMilliseconds,
      )

  private lateinit var firestore: FirebaseFirestore
  private lateinit var appointments: CollectionReference
  private lateinit var repository: FirebaseAppointmentRepository

  @Before
  fun setUp() {
    firestore = mockk()
    appointments = mockk()
    val circles = mockk<CollectionReference>()
    val circleDocument = mockk<DocumentReference>()
    every { firestore.collection("careCircles") } returns circles
    every { circles.document(circleId) } returns circleDocument
    every { circleDocument.collection("appointments") } returns appointments

    repository =
        FirebaseAppointmentRepository(
            firestore,
            auth = mockk(),
            now = { 0L },
            zone = { ZoneId.of("Europe/Zurich") },
        )
  }

  @Test
  fun observeAppointmentsListsTheDaySortedByStart() = runTest {
    val listener = stubWindowQuery()
    val lists = mutableListOf<List<Synced<Appointment>>>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observeAppointments(circleId, day, days = 1).toList(lists)
    }

    // Firestore's order: by end. The physio was edited offline, so it isn't synced yet.
    listener.captured.onEvent(
        queryResult(
            document(overnight),
            document(physio, pending = true),
            unreadableDocument(),
            document(checkUp),
        ),
        null,
    )
    runCurrent()

    assertEquals(
        listOf(
            listOf(
                Synced(overnight, isSynced = true),
                Synced(checkUp, isSynced = true),
                Synced(physio, isSynced = false),
            )
        ),
        lists,
    )
  }

  @Test
  fun observeAppointmentsChecksItsInputAndEndsWithAnAppointmentError() = runTest {
    val invalidId = runCatching { repository.observeAppointments("a/b", day, days = 1).collect() }
    assertEquals(AppointmentError.InvalidCircleId(), invalidId.exceptionOrNull())
    val noDay = runCatching { repository.observeAppointments(circleId, day, days = 0) }
    assertTrue(noDay.exceptionOrNull() is IllegalArgumentException)
    verify(exactly = 0) { firestore.collection(any()) }

    val listener = stubWindowQuery()
    val denied =
        async(UnconfinedTestDispatcher(testScheduler)) {
          runCatching { repository.observeAppointments(circleId, day, days = 1).collect() }
              .exceptionOrNull()
        }
    listener.captured.onEvent(
        null,
        FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED),
    )

    assertEquals(AppointmentError.PermissionDenied(), denied.await())
  }

  /**
   * Stubs the query of the day: `lastEndAt > dayStart` and `startAt < dayEnd`. Only that query is
   * stubbed, so both ends of the window must be sent to the server. Only the call with INCLUDE is
   * stubbed, so the sync flag must be asked for.
   *
   * @return the slot that receives the snapshot listener.
   */
  private fun stubWindowQuery(): CapturingSlot<EventListener<QuerySnapshot>> {
    val listener = slot<EventListener<QuerySnapshot>>()
    val notOver = mockk<Query>()
    val ofTheDay = mockk<Query>()
    every { appointments.whereGreaterThan("lastEndAt", dayStart) } returns notOver
    every { notOver.whereLessThan("startAt", dayEnd) } returns ofTheDay
    every { ofTheDay.addSnapshotListener(MetadataChanges.INCLUDE, capture(listener)) } returns
        mockk<ListenerRegistration>(relaxed = true)
    return listener
  }

  private fun queryResult(vararg documents: DocumentSnapshot): QuerySnapshot = mockk {
    every { this@mockk.documents } returns documents.toList()
  }

  /** @return a document holding [appointment], with a write not confirmed yet if [pending]. */
  private fun document(appointment: Appointment, pending: Boolean = false): DocumentSnapshot =
      mockk {
        every { toObject(Appointment::class.java) } returns appointment
        every { metadata.hasPendingWrites() } returns pending
      }

  /** @return a document this version of the app can't read, for example a field of a wrong type. */
  private fun unreadableDocument(): DocumentSnapshot =
      mockk(relaxed = true) {
        every { toObject(Appointment::class.java) } throws RuntimeException("Could not deserialize")
      }
}
