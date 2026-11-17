// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.calendar

import java.time.LocalDate
import java.time.ZoneId

/**
 * A span of whole days on the calendar, such as a day or a week. Times are in epoch milliseconds.
 *
 * @property startMillis the start of the first day. It is part of the window.
 * @property endMillis the start of the day after the last one. It is not part of the window.
 */
data class DayWindow(val startMillis: Long, val endMillis: Long)

/**
 * The window of [days] days that start with [firstDay], in [zone]. [firstDay] is included: the
 * window runs from midnight at its start to midnight at the end of the last day.
 *
 * The days follow the calendar of [zone], so a day of a daylight saving change lasts 23 or 25
 * hours.
 *
 * @param firstDay the first day of the window. It is included.
 * @param days how many days the window holds. It must be at least 1.
 * @param zone the time zone the days are counted in, usually the phone's.
 * @return the window.
 * @throws IllegalArgumentException if [days] is 0 or less.
 */
fun dayWindow(firstDay: LocalDate, days: Int, zone: ZoneId): DayWindow {
  require(days >= 1) { "A window holds at least 1 day, not $days" }
  return DayWindow(
      startMillis = firstDay.atStartOfDay(zone).toInstant().toEpochMilli(),
      endMillis = firstDay.plusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli(),
  )
}

/**
 * Whether this event happens, at least in part, during [window]. An event that ends right at the
 * start of the window, or starts right at its end, doesn't overlap it.
 *
 * @param window the days to check.
 * @return true if the event overlaps [window].
 */
fun CalendarEvent.overlaps(window: DayWindow): Boolean =
    startAt < window.endMillis && endAt() > window.startMillis
