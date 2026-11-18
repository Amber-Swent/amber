// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment

import com.github.se.amber.data.Synced
import com.github.se.amber.model.appointment.Appointment
import com.github.se.amber.model.calendar.dayWindow
import com.github.se.amber.model.calendar.overlaps
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * An [AppointmentRepository] that keeps the appointments in memory, for ViewModel tests.
 *
 * It saves what it is given as is: it doesn't check, trim or fill in any field. Set [failWith] to
 * make every write fail with that error, without saving anything. Call [emitWriteFailure] to act as
 * if the server refused a write.
 *
 * [observeAppointments] uses `endAt()` where the real repository uses [Appointment.lastEndAt],
 * since the fake doesn't fill it in. Every appointment it lists is synced. Its days are counted in
 * [zone], UTC unless a test passes another one.
 *
 * Like the real repository, an update of an appointment that isn't saved succeeds but changes
 * nothing, and is reported in [writeFailures] as [AppointmentError.NotFound].
 */
class FakeAppointmentRepository(private val zone: ZoneId = ZoneOffset.UTC) : AppointmentRepository {

  /** When not null, every write fails with this error and changes nothing. */
  var failWith: AppointmentError? = null

  /** What [hasUnsyncedWrites] returns. Every write of the fake is synced at once unless set. */
  var unsyncedWrites = false

  // By circle id, then by appointment id, in the order they were first added.
  private val circles = MutableStateFlow<Map<String, Map<String, Appointment>>>(emptyMap())
  private val failures =
      MutableSharedFlow<AppointmentWriteFailure>(extraBufferCapacity = FAILURE_BUFFER)
  private var lastId = 0

  /** The failures sent with [emitWriteFailure], and the updates of missing appointments. */
  override val writeFailures: Flow<AppointmentWriteFailure> = failures.asSharedFlow()

  /** The appointments saved in [circleId], in the order they were first added. */
  fun appointmentsOf(circleId: String): List<Appointment> =
      circles.value[circleId]?.values?.toList().orEmpty()

  /** Sends [failure] to the collectors of [writeFailures]. */
  suspend fun emitWriteFailure(failure: AppointmentWriteFailure) = failures.emit(failure)

  /**
   * Watches the appointments of [circleId] that overlap the [days] days from [firstDay] in [zone],
   * sorted by start. Like the real repository, it emits only when this list changes, not when
   * another circle does.
   *
   * The ids aren't checked, as in the writes. An invalid circle id gives an empty list, not
   * [AppointmentError.InvalidCircleId]. The flow never ends with
   * [AppointmentError.PermissionDenied] either.
   *
   * @return the live list, every appointment synced.
   */
  override fun observeAppointments(
      circleId: String,
      firstDay: LocalDate,
      days: Int,
  ): Flow<List<Synced<Appointment>>> {
    val window = dayWindow(firstDay, days, zone)
    return circles
        .map { all ->
          all[circleId]
              .orEmpty()
              .values
              .filter { it.overlaps(window) }
              .sortedWith(compareBy({ it.startAt }, { it.id }))
              .map { Synced(it, isSynced = true) }
        }
        .distinctUntilChanged()
  }

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
    save(circleId, appointment)
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
    if (appointmentsOf(circleId).any { it.id == appointment.id }) {
      save(circleId, appointment)
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
    circles.update { all -> all + (circleId to all[circleId].orEmpty().minus(appointmentId)) }
    return Result.success(Unit)
  }

  /** @return [unsyncedWrites]. */
  override suspend fun hasUnsyncedWrites(): Boolean = unsyncedWrites

  /** Adds or replaces [appointment] in [circleId], keeping its place if it was already there. */
  private fun save(circleId: String, appointment: Appointment) {
    circles.update { all ->
      all + (circleId to all[circleId].orEmpty() + (appointment.id to appointment))
    }
  }

  private companion object {
    /** How many failures wait for a slow collector, as in the real repository. */
    const val FAILURE_BUFFER = 16
  }
}
