// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment

import android.util.Log
import com.github.se.amber.data.Synced
import com.github.se.amber.data.isValidDocumentId
import com.github.se.amber.data.observeDocuments
import com.github.se.amber.model.appointment.Appointment
import com.github.se.amber.model.appointment.validationError
import com.github.se.amber.model.calendar.dayWindow
import com.github.se.amber.model.calendar.endAt
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.firestore
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executor
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Firebase-backed implementation of [AppointmentRepository].
 *
 * Each write is handed to Firestore and not awaited. Firestore saves it on the phone, shows it in
 * the live lists, and sends it once online. A listener on the write's task reports a refusal in
 * [writeFailures].
 *
 * [observeAppointments] filters a range on [Appointment.lastEndAt] and one on
 * [Appointment.startAt]. It needs the composite index on both fields, in
 * firebase/firestore/firestore.indexes.json. Without it, the server refuses the query.
 *
 * This repository must stay the only code that writes appointments. It always sets
 * [Appointment.lastEndAt]. A document without it reads back as 0, so [observeAppointments] would
 * never return it.
 *
 * @param firestore the database the appointments are written to.
 * @param auth gives the signed-in user, who is saved as the creator or the last editor.
 * @param now gives the current time in epoch milliseconds, for the created and updated times. Only
 *   tests change it.
 * @param zone gives the time zone the days of [observeAppointments] are counted in. It is read on
 *   each call, so a change of the phone's zone is followed. Only tests change it.
 */
class FirebaseAppointmentRepository(
    private val firestore: FirebaseFirestore = Firebase.firestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : AppointmentRepository {

  // No replay: a failure nobody collects is dropped. The buffer keeps a slow collector from
  // losing a burst of failures, up to FAILURE_BUFFER of them.
  private val failures =
      MutableSharedFlow<AppointmentWriteFailure>(
          extraBufferCapacity = FAILURE_BUFFER,
          onBufferOverflow = BufferOverflow.DROP_OLDEST,
      )

  /** The refusals reported by the write listeners. See [AppointmentRepository.writeFailures]. */
  override val writeFailures: Flow<AppointmentWriteFailure> = failures.asSharedFlow()

  /**
   * Generates the id with Firestore's `document()`, which works offline.
   *
   * The id comes from the `careCircles` collection, but it is not a circle id. Firestore makes the
   * same kind of random id for any collection, and the appointments collection depends on the
   * circle, which isn't known here.
   *
   * @return a new random id.
   */
  override fun newId(): String = firestore.collection(CARE_CIRCLES_COLLECTION).document().id

  /**
   * Writes the whole appointment with `set()` and doesn't wait for the server.
   *
   * @param circleId the id of the care circle the appointment belongs to.
   * @param appointment the new appointment, with an id from [newId].
   * @return the appointment as written, or a failure with an [AppointmentError].
   */
  override suspend fun addAppointment(
      circleId: String,
      appointment: Appointment,
  ): Result<Appointment> {
    val trimmed = appointment.withTrimmedText()
    val uid =
        checkWrite(circleId, trimmed.id, trimmed).getOrElse {
          return Result.failure(it)
        }
    val time = now()
    val saved =
        trimmed
            .copy(createdBy = uid, createdAt = time, updatedBy = uid, updatedAt = time)
            .withLastEndAt()

    return startWrite(saved.id, AppointmentWrite.ADD) { document(circleId, saved.id).set(saved) }
        .map { saved }
  }

  /**
   * Writes the editable fields and the update fields with `update()`, and doesn't wait for the
   * server.
   *
   * @param circleId the id of the care circle the appointment belongs to.
   * @param appointment the appointment with its new values.
   * @return the appointment as written, with the caller's creator fields, or a failure with an
   *   [AppointmentError].
   */
  override suspend fun updateAppointment(
      circleId: String,
      appointment: Appointment,
  ): Result<Appointment> {
    val trimmed = appointment.withTrimmedText()
    val uid =
        checkWrite(circleId, trimmed.id, trimmed).getOrElse {
          return Result.failure(it)
        }
    val saved = trimmed.copy(updatedBy = uid, updatedAt = now()).withLastEndAt()

    // update() and not set(): it fails on the server when the document is gone, so an edit never
    // brings back a deleted appointment. It also leaves the creator fields alone.
    return startWrite(saved.id, AppointmentWrite.UPDATE) {
          document(circleId, saved.id).update(saved.updatedFields())
        }
        .map { saved }
  }

  /**
   * Deletes the document with `delete()` and doesn't wait for the server.
   *
   * @param circleId the id of the care circle the appointment belongs to.
   * @param appointmentId the id of the appointment to delete.
   * @return success once the deletion is handed to Firestore, or a failure with an
   *   [AppointmentError].
   */
  override suspend fun deleteAppointment(circleId: String, appointmentId: String): Result<Unit> {
    checkWrite(circleId, appointmentId).getOrElse {
      return Result.failure(it)
    }

    return startWrite(appointmentId, AppointmentWrite.DELETE) {
      document(circleId, appointmentId).delete()
    }
  }

  /**
   * Queries `lastEndAt > start of the window` and `startAt < end of the window`, so the server
   * sends only the appointments of the window. Filtering a range on two fields needs the composite
   * index on `lastEndAt` and `startAt`.
   *
   * Firestore doesn't order such a query by start. The list is sorted by start, then by id, so the
   * order never changes between two emissions.
   *
   * @param circleId the id of the care circle.
   * @param firstDay the first day of the window.
   * @param days how many days the window holds. It must be at least 1.
   * @return the live list, which ends with an [AppointmentError].
   */
  override fun observeAppointments(
      circleId: String,
      firstDay: LocalDate,
      days: Int,
  ): Flow<List<Synced<Appointment>>> {
    val window = dayWindow(firstDay, days, zone())
    if (!isValidDocumentId(circleId)) return flow { throw AppointmentError.InvalidCircleId() }

    return appointments(circleId)
        .whereGreaterThan(LAST_END_AT_FIELD, window.startMillis)
        .whereLessThan(START_AT_FIELD, window.endMillis)
        .observeDocuments(map = { it.toAppointment() }, onError = { it.toAppointmentError() })
        .map { list -> list.sortedWith(compareBy({ it.value.startAt }, { it.value.id })) }
  }

  /**
   * Waits up to [UNSYNCED_WAIT] for Firestore's `waitForPendingWrites()`. Firestore can't say how
   * many writes are waiting, so a write still waiting after that time counts as not sent.
   *
   * The wait fails if the signed-in user changes meanwhile. It then counts as not sent too, since
   * nothing confirms the writes were received.
   *
   * @return true if the writes didn't all reach the server within [UNSYNCED_WAIT].
   */
  override suspend fun hasUnsyncedWrites(): Boolean =
      try {
        // await() gives null for a Task<Void>, so true marks that the wait finished in time
        val sent =
            withTimeoutOrNull(UNSYNCED_WAIT) {
              firestore.waitForPendingWrites().await()
              true
            }
        sent == null
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Log.w(TAG, "Couldn't wait for the pending writes", exception)
        true
      }

  /**
   * Checks a write before anything is written, and returns the uid of the signed-in user. The
   * checks run in this order: the circle id, the appointment id, then [appointment] if given, then
   * the user.
   *
   * @param circleId the circle id to check.
   * @param appointmentId the appointment id to check.
   * @param appointment the appointment to validate, or null for a delete.
   * @return the uid of the signed-in user, or a failure with the first [AppointmentError] found.
   */
  private fun checkWrite(
      circleId: String,
      appointmentId: String,
      appointment: Appointment? = null,
  ): Result<String> {
    val error =
        when {
          !isValidDocumentId(circleId) -> AppointmentError.InvalidCircleId()
          !isValidDocumentId(appointmentId) -> AppointmentError.InvalidAppointmentId()
          else -> appointment?.validationError()?.let { AppointmentError.InvalidAppointment(it) }
        }
    if (error != null) return Result.failure(error)

    val uid = auth.currentUser?.uid ?: return Result.failure(AppointmentError.NotAuthenticated())
    return Result.success(uid)
  }

  /**
   * Starts a write without waiting for it. A refusal from the server is reported later in
   * [writeFailures]. Only an exception thrown while starting the write fails the result.
   *
   * @param appointmentId the appointment the write is for, reported with a refusal.
   * @param write the kind of write, reported with a refusal.
   * @param start starts the write and returns its task.
   * @return success once the write is started, or a failure with an [AppointmentError].
   */
  private fun startWrite(
      appointmentId: String,
      write: AppointmentWrite,
      start: () -> Task<Void>,
  ): Result<Unit> =
      try {
        start().addOnFailureListener(DIRECT_EXECUTOR) { error ->
          failures.tryEmit(
              AppointmentWriteFailure(appointmentId, write, error.toAppointmentError())
          )
        }
        Result.success(Unit)
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Result.failure(exception.toAppointmentError())
      }

  /**
   * @param circleId the id of the care circle.
   * @return the appointments of the circle, at careCircles/{circleId}/appointments.
   */
  private fun appointments(circleId: String): CollectionReference =
      firestore
          .collection(CARE_CIRCLES_COLLECTION)
          .document(circleId)
          .collection(APPOINTMENTS_COLLECTION)

  /**
   * @param circleId the id of the care circle.
   * @param appointmentId the id of the appointment.
   * @return the document of the appointment, at careCircles/{circleId}/appointments/{id}.
   */
  private fun document(circleId: String, appointmentId: String): DocumentReference =
      appointments(circleId).document(appointmentId)

  /**
   * Reads one appointment document. A document this version of the app can't read is logged and
   * skipped, so one bad document doesn't break the whole list.
   *
   * @return the appointment, or null if the document can't be read.
   */
  private fun DocumentSnapshot.toAppointment(): Appointment? =
      try {
        toObject(Appointment::class.java)
      } catch (exception: RuntimeException) {
        Log.w(TAG, "Couldn't read appointment ${reference.path}", exception)
        null
      }

  /** @return a copy with the spaces around the title and the notes removed. */
  private fun Appointment.withTrimmedText(): Appointment =
      copy(title = title.trim(), notes = notes.trim())

  /** @return a copy with [Appointment.lastEndAt] computed from the start and the duration. */
  private fun Appointment.withLastEndAt(): Appointment = copy(lastEndAt = endAt())

  /**
   * The fields an edit writes: the editable ones, then the ones the repository sets. A null value
   * is written too, so an edit can clear the duration or the location.
   *
   * @return the field names, mapped to this appointment's values.
   */
  private fun Appointment.updatedFields(): Map<String, Any?> =
      mapOf(
          Appointment::title.name to title,
          Appointment::notes.name to notes,
          Appointment::startAt.name to startAt,
          Appointment::durationMinutes.name to durationMinutes,
          Appointment::location.name to location,
          Appointment::attendeeIds.name to attendeeIds,
          Appointment::lastEndAt.name to lastEndAt,
          Appointment::updatedBy.name to updatedBy,
          Appointment::updatedAt.name to updatedAt,
      )

  /**
   * Maps a Firestore failure to the error the callers expect. An [AppointmentError] is kept as is.
   *
   * @return the matching [AppointmentError], or [AppointmentError.Unknown] with this exception.
   */
  private fun Exception.toAppointmentError(): AppointmentError =
      when (this) {
        is AppointmentError -> this
        is FirebaseFirestoreException ->
            when (code) {
              FirebaseFirestoreException.Code.NOT_FOUND -> AppointmentError.NotFound()
              FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                  AppointmentError.PermissionDenied()
              FirebaseFirestoreException.Code.UNAUTHENTICATED -> AppointmentError.NotAuthenticated()
              else -> AppointmentError.Unknown(this)
            }
        else -> AppointmentError.Unknown(this)
      }

  private companion object {
    const val TAG = "FirebaseAppointmentRepository"
    const val CARE_CIRCLES_COLLECTION = "careCircles"
    const val APPOINTMENTS_COLLECTION = "appointments"
    const val FAILURE_BUFFER = 16
    val LAST_END_AT_FIELD = Appointment::lastEndAt.name
    val START_AT_FIELD = Appointment::startAt.name

    // How long hasUnsyncedWrites waits for the server. Long enough for a write to be sent online,
    // short enough not to keep the user waiting at sign-out.
    val UNSYNCED_WAIT = 2.seconds

    // Runs the failure listener on the thread that completes the task, not on the main thread.
    // tryEmit is safe from any thread, and tests then need no Android looper.
    val DIRECT_EXECUTOR = Executor(Runnable::run)
  }
}
