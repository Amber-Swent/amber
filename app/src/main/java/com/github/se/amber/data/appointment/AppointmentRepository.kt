// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment

import com.github.se.amber.model.appointment.Appointment
import com.github.se.amber.model.appointment.AppointmentValidationError
import kotlinx.coroutines.flow.Flow

/**
 * Creates, edits and deletes the appointments of a care circle, stored at
 * careCircles/{circleId}/appointments/{id}.
 *
 * Writes don't wait for the server. Each write is saved on the phone first, and the call then
 * returns. The phone sends it once it is online, even after the app is restarted. So a successful
 * [Result] means "saved on this phone". It doesn't mean the server has it yet.
 *
 * The server can still refuse a write later. It refuses it when the security rules deny it. It also
 * refuses an edit of an appointment someone else deleted meanwhile. The phone then undoes the
 * change, and the repository reports it in [writeFailures].
 *
 * Every failed [Result] contains an [AppointmentError]. Cancellation is rethrown.
 *
 * Reading appointments is not part of this interface yet. It might be added later.
 */
interface AppointmentRepository {

  /**
   * Generates a new appointment id on the phone, without writing anything. It works offline.
   *
   * Pass it as [Appointment.id] to [addAppointment]. The id doesn't depend on the circle.
   */
  fun newId(): String

  /**
   * Saves a new appointment in the circle [circleId] and returns the appointment as saved.
   *
   * [appointment] must have an id from [newId]. Its `validationError()` must return null. The
   * repository then:
   * - trims the title and the notes;
   * - sets [Appointment.createdBy] and [Appointment.updatedBy] to the signed-in user;
   * - sets [Appointment.createdAt] and [Appointment.updatedAt] to the current time;
   * - computes [Appointment.lastEndAt].
   *
   * The values the caller gives for these fields are ignored. The returned appointment holds the
   * values actually saved, so the caller should use it instead of its own copy.
   *
   * The attendees must be members of the circle. The repository doesn't check it, since that would
   * need the circle and the network. The security rules don't check it yet. They might check it on
   * the server later. A refusal would then be reported in [writeFailures].
   *
   * Fails right away, without writing anything, with:
   * - [AppointmentError.InvalidCircleId] or [AppointmentError.InvalidAppointmentId] for an invalid
   *   id. An id is invalid when it is blank. It is also invalid with spaces around it, and with a
   *   slash in it;
   * - [AppointmentError.InvalidAppointment] when `validationError()` doesn't return null;
   * - [AppointmentError.NotAuthenticated] when nobody is signed in.
   *
   * Call it once per appointment. To change an appointment after it is saved, use
   * [updateAppointment].
   */
  suspend fun addAppointment(circleId: String, appointment: Appointment): Result<Appointment>

  /**
   * Saves the changes to an existing appointment and returns the appointment as saved.
   *
   * Only the editable fields are written: the title, the notes, [Appointment.startAt],
   * [Appointment.durationMinutes], [Appointment.location] and [Appointment.attendeeIds]. The
   * repository trims the title and the notes, like [addAppointment] does. It also sets
   * [Appointment.updatedBy] to the signed-in user, sets [Appointment.updatedAt] to the current
   * time, and computes [Appointment.lastEndAt] again.
   *
   * [Appointment.createdBy] and [Appointment.createdAt] are never written. The returned appointment
   * keeps the caller's values for them.
   *
   * An edit never brings back a deleted appointment. If someone deleted it meanwhile, the server
   * refuses the edit. It is reported in [writeFailures] as [AppointmentError.NotFound].
   *
   * Fails right away, without writing anything, for the same reasons as [addAppointment].
   */
  suspend fun updateAppointment(circleId: String, appointment: Appointment): Result<Appointment>

  /**
   * Deletes the appointment [appointmentId] of the circle [circleId].
   *
   * Deleting an appointment that doesn't exist succeeds.
   *
   * Fails right away, without deleting anything, with:
   * - [AppointmentError.InvalidCircleId] or [AppointmentError.InvalidAppointmentId] for an invalid
   *   id, as in [addAppointment];
   * - [AppointmentError.NotAuthenticated] when nobody is signed in.
   */
  suspend fun deleteAppointment(circleId: String, appointmentId: String): Result<Unit>

  /**
   * The writes the server refused after the call that made them had returned.
   *
   * The phone has already undone the change. The screen collects this flow to tell the user that
   * their change wasn't saved.
   *
   * This flow doesn't replay past failures. A failure that happens while nobody collects it is
   * lost. The data on the phone is still correct.
   *
   * Limit: a refusal is reported only while the app runs. A write made offline is still sent after
   * a restart. If the server refuses it then, nothing is reported.
   */
  val writeFailures: Flow<AppointmentWriteFailure>
}

/** The kinds of writes [AppointmentRepository] makes. */
enum class AppointmentWrite {
  /** Made by [AppointmentRepository.addAppointment]. */
  ADD,

  /** Made by [AppointmentRepository.updateAppointment]. */
  UPDATE,

  /** Made by [AppointmentRepository.deleteAppointment]. */
  DELETE,
}

/**
 * A write the server refused after the call had returned. Reported in
 * [AppointmentRepository.writeFailures].
 *
 * @property appointmentId the id of the appointment the write was for.
 * @property write the kind of write that was refused.
 * @property error why the server refused it. Usually [AppointmentError.PermissionDenied] or
 *   [AppointmentError.NotFound].
 */
data class AppointmentWriteFailure(
    val appointmentId: String,
    val write: AppointmentWrite,
    val error: AppointmentError,
)

/**
 * Typed failures returned by [AppointmentRepository].
 *
 * Messages are developer-facing diagnostics. The UI must map each subtype to a string resource
 * instead of displaying [message].
 *
 * The subtypes are classes, not objects. An exception records the stack trace of the place it is
 * created. A single shared object would carry the trace of its first use everywhere. Each failure
 * is therefore a new instance. Check its type with `is`.
 *
 * Two errors are equal when they have the same subtype and equal fields, so tests and
 * [AppointmentWriteFailure] can compare them. For [Unknown], the field is the original exception
 * itself.
 */
sealed class AppointmentError(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

  /** Equal to any error of the same subtype. The data class subtypes replace it. */
  override fun equals(other: Any?): Boolean = other != null && other::class == this::class

  override fun hashCode(): Int = this::class.hashCode()

  /** The care circle id is invalid. See [AppointmentRepository.addAppointment]. */
  class InvalidCircleId : AppointmentError("A valid care circle ID is required")

  /** The appointment id is invalid. See [AppointmentRepository.addAppointment]. */
  class InvalidAppointmentId : AppointmentError("A valid appointment ID is required")

  /** The appointment can't be saved. [reason] says why, so the form can point at the field. */
  data class InvalidAppointment(val reason: AppointmentValidationError) :
      AppointmentError("The appointment is invalid: $reason")

  /** The appointment doesn't exist. Someone else may have deleted it. */
  class NotFound : AppointmentError("The appointment does not exist")

  /** Nobody is signed in. */
  class NotAuthenticated : AppointmentError("No user is signed in")

  /**
   * The security rules refused the write. For example, the user isn't a caregiver of this circle.
   */
  class PermissionDenied : AppointmentError("You do not have permission to change this appointment")

  /** Any other failure. [source] is the original exception, kept as the cause. */
  data class Unknown(val source: Throwable) :
      AppointmentError("The appointment operation failed", source)
}
