// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.calendar

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/** How long before its start an event counts as [CalendarStatus.SOON]: one day. */
val SOON_WINDOW_MILLIS = 1.days.inWholeMilliseconds

/**
 * Where an event stands at a given time. The status is computed on the phone. It is never stored.
 *
 * The statuses follow each other in this order. An event never goes back to an earlier one.
 */
enum class CalendarStatus {
  /** It starts later than the soon window. */
  PLANNED,

  /** It starts within the soon window, which is one day by default. */
  SOON,

  /** It has started and isn't over. */
  ONGOING,

  /** It is over. */
  PASSED,
}

/**
 * When the event ends, in epoch milliseconds: [CalendarEvent.startAt] plus its duration.
 *
 * An unknown duration counts as [DEFAULT_DURATION_MINUTES]. A duration of zero or less does too.
 * For an appointment, such a value can only come from a document written by hand, since the
 * repository refuses it.
 */
fun CalendarEvent.endAt(): Long {
  val duration = durationMinutes?.takeIf { it > 0 } ?: DEFAULT_DURATION_MINUTES
  return startAt + duration.minutes.inWholeMilliseconds
}

/**
 * The status of the event at [nowMillis].
 *
 * The boundaries belong to the later status:
 * - [CalendarStatus.SOON] from `startAt - soonWindowMillis`;
 * - [CalendarStatus.ONGOING] from `startAt`;
 * - [CalendarStatus.PASSED] from [endAt].
 *
 * With a window of zero, the event goes from [CalendarStatus.PLANNED] straight to
 * [CalendarStatus.ONGOING].
 *
 * @param nowMillis the current time, in epoch milliseconds. It is a parameter so that tests and
 *   previews can choose it.
 * @param soonWindowMillis how long before its start the event counts as soon. It can't be negative.
 * @throws IllegalArgumentException if [soonWindowMillis] is negative.
 */
fun CalendarEvent.statusAt(
    nowMillis: Long,
    soonWindowMillis: Long = SOON_WINDOW_MILLIS,
): CalendarStatus {
  require(soonWindowMillis >= 0) { "The soon window can't be negative" }
  return when {
    nowMillis >= endAt() -> CalendarStatus.PASSED
    nowMillis >= startAt -> CalendarStatus.ONGOING
    nowMillis >= startAt - soonWindowMillis -> CalendarStatus.SOON
    else -> CalendarStatus.PLANNED
  }
}

/**
 * The next time the status of the event changes after [nowMillis], in epoch milliseconds.
 *
 * It returns null once the event has [CalendarStatus.PASSED], since no change follows. The screen
 * can wait until this time and then compute the status again, instead of checking it every minute.
 *
 * The parameters are the same as for [statusAt]. Pass the same window to both.
 *
 * @throws IllegalArgumentException if [soonWindowMillis] is negative.
 */
fun CalendarEvent.nextStatusChangeAt(
    nowMillis: Long,
    soonWindowMillis: Long = SOON_WINDOW_MILLIS,
): Long? =
    when (statusAt(nowMillis, soonWindowMillis)) {
      CalendarStatus.PLANNED -> startAt - soonWindowMillis
      CalendarStatus.SOON -> startAt
      CalendarStatus.ONGOING -> endAt()
      CalendarStatus.PASSED -> null
    }
