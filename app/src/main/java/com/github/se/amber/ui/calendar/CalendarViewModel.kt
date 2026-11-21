// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.calendar

import androidx.lifecycle.ViewModel
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
 * ViewModel of [Calendar]: it owns the displayed month and the month navigation.
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
