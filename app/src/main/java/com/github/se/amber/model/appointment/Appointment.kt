// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.appointment

import com.github.se.amber.model.location.GeoLocation

/**
 * An upcoming appointment of the patient (doctor, physio, hairdresser...), shown to them so they
 * know what is planned and who will come along. Stored at careCircles/{circleId}/appointments/{id}.
 *
 * Written: created and edited by caregivers only, for now (subject to change). Created once with
 * set(); later edits update only the fields that changed. A cancelled appointment is deleted.
 * [location] is picked from CareCircle.places (a copy, so later edits to the place don't change it)
 * or entered for a one-off place.
 *
 * Read: the patient's screen queries appointments where startAt >= now, ordered by startAt, and
 * keeps a snapshot listener so changes made by caregivers show up live. Past appointments are left
 * out by that query.
 *
 * Rules: readable by every member of the circle (CareCircle.memberIds); writable only by members
 * whose UserProfile.role is CAREGIVER.
 *
 * Planned, not handled yet:
 * - reminders: a notification on the patient's phone before [startAt], scheduled locally so it
 *   works offline. Would add a field such as `reminderMinutesBefore: List<Int>`.
 * - recurrence (e.g. weekly physio): would add a recurrence rule field; for now each occurrence is
 *   its own appointment.
 *
 * New fields must get a default value, so documents written before they existed can still be read.
 */
data class Appointment(
    val id: String = "", // document id
    val title: String = "", // "Dr. Martin, neurologist"; the doctor goes here, not in attendeeIds
    val notes: String = "", // "bring the blood test results"
    val startAt: Long = 0L, // exact time, epoch milliseconds; shown in the device's time zone
    val durationMinutes: Int? = null, // null = unknown
    // where it takes place, copied from a Place or entered; null = not set
    val location: GeoLocation? = null,
    // uids of the circle members going with the patient (CareCircle.memberIds); the patient always
    // attends and is not listed
    val attendeeIds: List<String> = emptyList(),
    val createdBy: String = "", // uid of the caregiver who created it
    val createdAt: Long = 0L, // Optional; epoch milliseconds
)
