// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment

import com.github.se.amber.data.Synced
import com.github.se.amber.model.appointment.Appointment
import com.github.se.amber.model.appointment.AppointmentValidationError
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Creates, edits, deletes and watches the appointments of a care circle, stored at
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
 * [observeAppointments] reads them, one day or several days at a time.
 *
 * [hasUnsyncedWrites] tells whether some changes are still waiting to be sent, for example before
 * signing out.
 */
interface AppointmentRepository {

  /**
   * Watches the appointments of the circle [circleId] that happen, at least in part, during the
   * [days] days that start with [firstDay]. Emits the list at once, then again on every change,
   * until the collector stops.
   *
   * [firstDay] is included. The window runs from midnight at the start of [firstDay] to midnight at
   * the end of its last day, in the phone's time zone. For example, a [firstDay] of Monday 17
   * November with 7 [days] covers Monday 17 to Sunday 23 November, both included.
   *
   * An appointment that started the day before and isn't over yet is listed too. The list is sorted
   * by [Appointment.startAt], soonest first.
   *
   * The list holds every status: an appointment earlier today is listed as well as an ongoing one.
   * Use `statusAt` to tell them apart.
   *
   * Each appointment comes with [Synced.isSynced]. It is false while the appointment has a write
   * the server hasn't confirmed yet. A write made on this phone shows up at once, even offline.
   *
   * The flow ends with an [AppointmentError]:
   * - [AppointmentError.InvalidCircleId] right away, for an invalid id, as in [addAppointment];
   * - [AppointmentError.PermissionDenied] when the user can't read the circle, for example after
   *   leaving it.
   *
   * Going offline doesn't end it. The list then comes from what this phone has already loaded.
   *
   * @param circleId the id of the care circle.
   * @param firstDay the first day of the window. It is included.
   * @param days how many days the window holds. It must be at least 1.
   * @return the live list, which ends with an [AppointmentError].
   * @throws IllegalArgumentException if [days] is 0 or less. That is a bug in the caller.
   */
  fun observeAppointments(
      circleId: String,
      firstDay: LocalDate,
      days: Int,
  ): Flow<List<Synced<Appointment>>>

  /**
   * Generates a new appointment id on the phone, without writing anything. It works offline.
   *
   * Pass it as [Appointment.id] to [addAppointment]. The id doesn't depend on the circle.
   *
   * @return a new random id, not used by any appointment yet.
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
   * need the circle and the network. The security rules check it on the server. A refusal is then
   * reported in [writeFailures] as [AppointmentError.PermissionDenied].
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
   *
   * @param circleId the id of the care circle the appointment belongs to.
   * @param appointment the new appointment, with an id from [newId].
   * @return the appointment as saved on the phone, or a failure with an [AppointmentError].
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
   *
   * @param circleId the id of the care circle the appointment belongs to.
   * @param appointment the appointment with its new values. Its id says which one to change.
   * @return the appointment as saved on the phone, or a failure with an [AppointmentError].
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
   *
   * @param circleId the id of the care circle the appointment belongs to.
   * @param appointmentId the id of the appointment to delete.
   * @return success once the deletion is saved on the phone, or a failure with an
   *   [AppointmentError].
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

  /**
   * Tells whether this phone still has changes the server hasn't received. Call it before signing
   * out. The changes not sent yet would otherwise be sent later with the next user's account, and
   * the server would refuse them without a word.
   *
   * It covers every pending Firestore write of the app, not only appointments. The check can't tell
   * them apart, so true doesn't mean an appointment is waiting.
   *
   * It waits up to 2 seconds for the server to receive every write. Offline, a waiting write is
   * only reported once these 2 seconds are over. If the wait fails, for example because the user
   * changed meanwhile, it returns true too, since nothing confirms the writes were received.
   *
   * @return false only if the server received every write within 2 seconds. True if the time ran
   *   out or the wait failed.
   */
  suspend fun hasUnsyncedWrites(): Boolean
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

  /** The security rules refused the write. For example, the user isn't a member of this circle. */
  class PermissionDenied : AppointmentError("You do not have permission to change this appointment")

  /** Any other failure. [source] is the original exception, kept as the cause. */
  data class Unknown(val source: Throwable) :
      AppointmentError("The appointment operation failed", source)
}
