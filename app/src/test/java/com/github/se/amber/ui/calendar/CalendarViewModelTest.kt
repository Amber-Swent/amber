// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.calendar

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests [CalendarViewModel]: the month navigation. */
class CalendarViewModelTest {

  private val november = YearMonth.of(2026, 11)

  @Test
  fun startsOnTheInitialMonth() {
    assertEquals(november, CalendarViewModel(november).uiState.value.month)
  }

  @Test
  fun showNextMonthMovesToTheFollowingMonth() {
    val viewModel = CalendarViewModel(november)
    viewModel.showNextMonth()
    assertEquals(YearMonth.of(2026, 12), viewModel.uiState.value.month)
  }

  @Test
  fun showPreviousMonthMovesToThePrecedingMonth() {
    val viewModel = CalendarViewModel(november)
    viewModel.showPreviousMonth()
    assertEquals(YearMonth.of(2026, 10), viewModel.uiState.value.month)
  }

  @Test
  fun showNextMonthAfterDecemberChangesYear() {
    val viewModel = CalendarViewModel(YearMonth.of(2026, 12))
    viewModel.showNextMonth()
    assertEquals(YearMonth.of(2027, 1), viewModel.uiState.value.month)
  }

  @Test
  fun showPreviousMonthBeforeJanuaryChangesYear() {
    val viewModel = CalendarViewModel(YearMonth.of(2026, 1))
    viewModel.showPreviousMonth()
    assertEquals(YearMonth.of(2025, 12), viewModel.uiState.value.month)
  }

  @Test
  fun nextThenPreviousComesBackToTheInitialMonth() {
    val viewModel = CalendarViewModel(november)
    viewModel.showNextMonth()
    viewModel.showPreviousMonth()
    assertEquals(november, viewModel.uiState.value.month)
  }
}
