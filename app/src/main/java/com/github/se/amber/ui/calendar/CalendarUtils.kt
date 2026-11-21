// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.calendar

import java.time.DayOfWeek
import java.time.YearMonth

/**
 * Lays out the days of [month] as the cells of a calendar grid, week after week.
 *
 * @param month the month to lay out.
 * @param firstDayOfWeek the day shown in the first column (Monday in France, Sunday in the US...).
 * @return the cells, row by row. A cell is the day of the month, or null for an empty cell before
 *   the first day or after the last one. The size is always a multiple of the number of days in a
 *   week.
 */
internal fun calendarCells(
    month: YearMonth = YearMonth.now(),
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
): List<Int?> {
  val daysInWeek = DayOfWeek.entries.size

  // Represent the empty cells before the 1st.
  // In other words, how many columns the 1st is after the first column.
  // DayOfWeek values go from 1 (Monday) to 7 (Sunday). The difference can be negative
  // (a month starting on a Monday, with Sunday as first column), so daysInWeek is added before
  // the modulo to get a value between 0 and 6.
  val leadingBlanks =
      (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + daysInWeek) % daysInWeek
  val days: List<Int?> = List(month.lengthOfMonth()) { it + 1 }
  val beforeFirstDay: List<Int?> = List(leadingBlanks) { null }

  // Empty cells after the last day, to complete the last week.
  val trailingBlanks = (daysInWeek - (leadingBlanks + days.size) % daysInWeek) % daysInWeek
  val afterLastDay: List<Int?> = List(trailingBlanks) { null }

  return beforeFirstDay + days + afterLastDay
}
