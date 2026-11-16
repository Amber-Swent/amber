// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.appointment

import com.github.se.amber.model.calendar.MAX_DURATION_MINUTES

/**
 * Why an appointment can't be saved. The repository checks it before writing anything. The form can
 * check it too, to show the problem next to the field.
 */
enum class AppointmentValidationError {
  /** The title is empty, or only whitespace. */
  BLANK_TITLE,

  /** [Appointment.startAt] isn't set: it is zero or less. */
  MISSING_START,

  /** [Appointment.durationMinutes] is set but not between 1 and [MAX_DURATION_MINUTES]. */
  INVALID_DURATION,
}

/**
 * The first problem that keeps this appointment from being saved, or null when it is valid.
 *
 * The checks run in the order of [AppointmentValidationError]. It doesn't check the id, the
 * attendees, or the fields the repository fills in itself.
 */
fun Appointment.validationError(): AppointmentValidationError? {
  val duration = durationMinutes
  return when {
    title.isBlank() -> AppointmentValidationError.BLANK_TITLE
    startAt <= 0L -> AppointmentValidationError.MISSING_START
    duration != null && duration !in 1..MAX_DURATION_MINUTES ->
        AppointmentValidationError.INVALID_DURATION
    else -> null
  }
}
