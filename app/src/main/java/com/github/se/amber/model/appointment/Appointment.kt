// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.appointment

import com.github.se.amber.model.calendar.CalendarEvent
import com.github.se.amber.model.location.GeoLocation

/**
 * An appointment of the patient (doctor, physio, hairdresser...), shown to them so they know what
 * is planned and who will come along. Stored at careCircles/{circleId}/appointments/{id}.
 *
 * Written: created, edited and deleted by every member of the circle, the patient included. Each
 * member can edit or delete any appointment, not only its creator. Created once with set(). Later
 * edits update only the editable fields. A cancelled appointment is deleted. [location] is picked
 * from CareCircle.places (a copy, so later edits to the place don't change it) or entered for a
 * one-off place. The repository fills in [lastEndAt] and the update fields itself. On create, it
 * also fills in the creator fields. It never trusts the values it is given for them.
 *
 * Read: the calendar queries the appointments of a window of days (lastEndAt > its start and
 * startAt < its end) and keeps a snapshot listener, so changes made by other members show up live.
 * An appointment that started before the window and isn't over is listed too.
 *
 * Status: planned, soon, ongoing and passed are computed on the phone with the helpers of
 * CalendarStatus.kt, such as `statusAt`. They are never stored.
 *
 * Rules: readable and writable by every member of the circle (CareCircle.memberIds). On every save,
 * the rules check that:
 * - the document holds only the fields of this class, and [id] is the document id;
 * - it passes the checks of `validationError`: [title] isn't blank, [startAt] is set, and
 *   [durationMinutes] is null or between 1 and 1440;
 * - [lastEndAt] is [startAt] plus [durationMinutes], or plus 60 minutes when it is null;
 * - every field has the type this class expects, so the app can read it back;
 * - [updatedBy] is the user saving it;
 * - every attendee is a member of the circle.
 *
 * On create, [createdBy] must be the user saving it. An edit can't change [createdBy] or
 * [createdAt].
 *
 * Account deletion: the backend deletes the appointments the user created. On the others, it
 * removes the user from [attendeeIds], and sets [updatedBy] to "" if they saved it last.
 *
 * Planned, not handled yet:
 * - reminders: a notification on the patient's phone before [startAt], scheduled locally so it
 *   works offline. Would add a field such as `reminderMinutesBefore: List<Int>`.
 * - recurrence (e.g. weekly physio): will add a recurrence rule field; for now each occurrence is
 *   its own appointment.
 *
 * New fields must get a default value, so documents written before they existed can still be read.
 * Computed values must be extension functions, like [validationError]. Firestore stores every
 * public getter, so a property would be written to each document.
 */
data class Appointment(
    // Shown on the calendar. Each field is described in CalendarEvent; only what is specific to
    // appointments is noted here. Members edit these.

    // What it is
    override val id: String = "", // the document id
    override val title: String = "", // the doctor goes here, not in attendeeIds
    override val notes: String = "",

    // When it happens
    override val startAt: Long = 0L,
    override val durationMinutes: Int? = null,

    // Where, and who comes along
    override val location: GeoLocation? = null, // copied from a Place, or entered
    override val attendeeIds: List<String> = emptyList(),

    // Stored for queries. Set by the repository.

    // End time of the appointment, in epoch milliseconds. The "not over yet" query filters on it.
    // Repeating appointments will be implemented later. For one of them, it will be the end of its
    // last occurrence. That is why it is stored, and why it is named lastEndAt rather than endAt.
    val lastEndAt: Long = 0L,

    // Record keeping. Set by the repository: the created fields on create, the updated fields on
    // every save. Times are in epoch milliseconds.
    val createdBy: String = "", // the member who created it
    val createdAt: Long = 0L,
    val updatedBy: String = "", // the last member who saved it; "" once their account is deleted
    val updatedAt: Long = 0L,
) : CalendarEvent
