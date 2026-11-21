// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.calendar

import androidx.lifecycle.ViewModel
import java.time.DayOfWeek
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI state of the calendar.
 *
 * @property month the month currently displayed.
 */
data class CalendarUIState(val month: YearMonth = YearMonth.now())

/**
 * ViewModel of [DisplayCalendar]: it owns the displayed month and the month navigation.
 *
 * @param initialMonth the month displayed first. Defaults to the current month.
 */
class CalendarViewModel(initialMonth: YearMonth = YearMonth.now()) : ViewModel() {

  private val _uiState = MutableStateFlow(CalendarUIState(month = initialMonth))
  val uiState: StateFlow<CalendarUIState> = _uiState.asStateFlow()

  /** Displays the month before the current one. */
  fun showPreviousMonth() {
    _uiState.value = _uiState.value.copy(month = _uiState.value.month.minusMonths(1))
  }

  /** Displays the month after the current one. */
  fun showNextMonth() {
    _uiState.value = _uiState.value.copy(month = _uiState.value.month.plusMonths(1))
  }
}

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
  val leadingBlanks =
      (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + daysInWeek) % daysInWeek
  val days: List<Int?> = List(month.lengthOfMonth()) { it + 1 }
  val beforeFirstDay: List<Int?> = List(leadingBlanks) { null }
  val trailingBlanks = (daysInWeek - (leadingBlanks + days.size) % daysInWeek) % daysInWeek
  val afterLastDay: List<Int?> = List(trailingBlanks) { null }
  return beforeFirstDay + days + afterLastDay
}
