// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment

import com.github.se.amber.model.appointment.Appointment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * An [AppointmentRepository] that keeps the appointments in memory, for ViewModel tests.
 *
 * It saves what it is given as is: it doesn't check, trim or fill in any field. Set [failWith] to
 * make every write fail with that error, without saving anything. Call [emitWriteFailure] to act as
 * if the server refused a write.
 *
 * Like the real repository, an update of an appointment that isn't saved succeeds but changes
 * nothing, and is reported in [writeFailures] as [AppointmentError.NotFound].
 */
class FakeAppointmentRepository : AppointmentRepository {

  /** When not null, every write fails with this error and changes nothing. */
  var failWith: AppointmentError? = null

  private val circles = mutableMapOf<String, MutableMap<String, Appointment>>()
  private val failures =
      MutableSharedFlow<AppointmentWriteFailure>(extraBufferCapacity = FAILURE_BUFFER)
  private var lastId = 0

  /** The failures sent with [emitWriteFailure], and the updates of missing appointments. */
  override val writeFailures: Flow<AppointmentWriteFailure> = failures.asSharedFlow()

  /** The appointments saved in [circleId], in the order they were first added. */
  fun appointmentsOf(circleId: String): List<Appointment> =
      circles[circleId]?.values?.toList().orEmpty()

  /** Sends [failure] to the collectors of [writeFailures]. */
  suspend fun emitWriteFailure(failure: AppointmentWriteFailure) = failures.emit(failure)

  /** @return "appointment-1", then "appointment-2", and so on. */
  override fun newId(): String = "appointment-${++lastId}"

  /**
   * Saves [appointment] as given, unless [failWith] is set.
   *
   * @return the saved appointment, or a failure with [failWith].
   */
  override suspend fun addAppointment(
      circleId: String,
      appointment: Appointment,
  ): Result<Appointment> {
    failWith?.let {
      return Result.failure(it)
    }
    circles.getOrPut(circleId) { linkedMapOf() }[appointment.id] = appointment
    return Result.success(appointment)
  }

  /**
   * Replaces the saved appointment with [appointment], unless [failWith] is set. If it isn't saved,
   * nothing changes and [AppointmentError.NotFound] is reported in [writeFailures].
   *
   * @return [appointment], or a failure with [failWith].
   */
  override suspend fun updateAppointment(
      circleId: String,
      appointment: Appointment,
  ): Result<Appointment> {
    failWith?.let {
      return Result.failure(it)
    }
    val saved = circles[circleId]
    if (saved?.containsKey(appointment.id) == true) {
      saved[appointment.id] = appointment
    } else {
      failures.tryEmit(
          AppointmentWriteFailure(
              appointment.id,
              AppointmentWrite.UPDATE,
              AppointmentError.NotFound(),
          )
      )
    }
    return Result.success(appointment)
  }

  /**
   * Removes the appointment [appointmentId] from [circleId], unless [failWith] is set.
   *
   * @return success, even if there was no such appointment, or a failure with [failWith].
   */
  override suspend fun deleteAppointment(circleId: String, appointmentId: String): Result<Unit> {
    failWith?.let {
      return Result.failure(it)
    }
    circles[circleId]?.remove(appointmentId)
    return Result.success(Unit)
  }

  private companion object {
    /** How many failures wait for a slow collector, as in the real repository. */
    const val FAILURE_BUFFER = 16
  }
}
