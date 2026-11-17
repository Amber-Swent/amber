// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment

import com.github.se.amber.model.appointment.Appointment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
