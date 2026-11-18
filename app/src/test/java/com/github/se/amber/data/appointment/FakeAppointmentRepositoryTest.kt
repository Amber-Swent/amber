// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment

import com.github.se.amber.data.Synced
import com.github.se.amber.model.appointment.Appointment
import java.time.LocalDate
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeAppointmentRepositoryTest {
  private val repository = FakeAppointmentRepository()

  @Test
  fun writesChangeTheSavedAppointments() = runTest {
    val first = Appointment(id = repository.newId(), title = "Physio")
    val second = Appointment(id = repository.newId(), title = "Dentist")
    assertNotEquals(first.id, second.id)

    repository.addAppointment("circle", first)
    repository.addAppointment("circle", second)
    repository.updateAppointment("circle", first.copy(title = "Physio at 10"))
    repository.deleteAppointment("circle", second.id)

    assertEquals(listOf(first.copy(title = "Physio at 10")), repository.appointmentsOf("circle"))
    assertEquals(emptyList<Appointment>(), repository.appointmentsOf("other circle"))
  }

  @Test
  fun observeAppointmentsListsTheDayAndFollowsTheChanges() = runTest {
    // Written out: 17 November 2026 at 00:00 UTC, the fake's default zone.
    val dayStart = 1_794_873_600_000L
    val hour = 1.hours.inWholeMilliseconds
    // No duration, so each one lasts the default 60 minutes.
    val acrossMidnight = Appointment(id = "a1", title = "Night care", startAt = dayStart - hour / 2)
    val yesterday = Appointment(id = "a2", title = "Dentist", startAt = dayStart - 2 * hour)
    val later = Appointment(id = "a3", title = "Physio", startAt = dayStart + 10 * hour)
    val tomorrow = Appointment(id = "a4", title = "Hairdresser", startAt = dayStart + 24 * hour)
    for (appointment in listOf(later, yesterday, tomorrow, acrossMidnight)) {
      repository.addAppointment("circle", appointment)
    }
    val lists = mutableListOf<List<Synced<Appointment>>>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observeAppointments("circle", LocalDate.of(2026, 11, 17), days = 1).toList(lists)
    }

    // A change in another circle doesn't make this list emit again.
    repository.addAppointment("other circle", later)
    repository.deleteAppointment("circle", later.id)
    runCurrent()

    assertEquals(
        listOf(listOf(acrossMidnight, later), listOf(acrossMidnight)).map { list ->
          list.map { Synced(it, isSynced = true) }
        },
        lists,
    )
  }

  @Test
  fun anUpdateOfAMissingAppointmentIsReportedAndSavesNothing() = runTest {
    val failures = collectFailures()

    val result = repository.updateAppointment("circle", Appointment(id = "a1", title = "Physio"))

    assertEquals("a1", result.getOrThrow().id)
    assertEquals(emptyList<Appointment>(), repository.appointmentsOf("circle"))
    assertEquals(
        listOf(AppointmentWriteFailure("a1", AppointmentWrite.UPDATE, AppointmentError.NotFound())),
        failures,
    )
  }

  @Test
  fun failWithMakesEveryWriteFailWithoutSaving() = runTest {
    val error = AppointmentError.PermissionDenied()
    repository.failWith = error

    // assertSame: the errors are equal by type, so assertEquals would accept a new instance.
    val appointment = Appointment(id = "a1", title = "Physio")
    assertSame(error, repository.addAppointment("circle", appointment).exceptionOrNull())
    assertSame(error, repository.updateAppointment("circle", appointment).exceptionOrNull())
    assertSame(error, repository.deleteAppointment("circle", "a1").exceptionOrNull())
    assertEquals(emptyList<Appointment>(), repository.appointmentsOf("circle"))
  }

  @Test
  fun anEmittedFailureReachesTheCollectors() = runTest {
    val failures = collectFailures()
    val failure =
        AppointmentWriteFailure("a1", AppointmentWrite.DELETE, AppointmentError.PermissionDenied())

    repository.emitWriteFailure(failure)

    assertEquals(listOf(failure), failures)
  }

  /** Collects [FakeAppointmentRepository.writeFailures] from now on, without delay. */
  private fun TestScope.collectFailures(): List<AppointmentWriteFailure> {
    val failures = mutableListOf<AppointmentWriteFailure>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.writeFailures.toList(failures)
    }
    return failures
  }
}
