// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.calendar

import java.time.DayOfWeek
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests [calendarCells]: the layout of a month in a grid. */
class CalendarUtilsTest {

  // November 2026 has 30 days and starts on a Sunday.
  private val november = YearMonth.of(2026, 11)

  @Test
  fun sundayFirstMonthStartingOnSundayHasNoLeadingBlank() {
    assertEquals(1, calendarCells(november, DayOfWeek.SUNDAY).first())
  }

  @Test
  fun mondayFirstMonthStartingOnSundayHasSixLeadingBlanks() {
    assertEquals(6, calendarCells(november, DayOfWeek.MONDAY).takeWhile { it == null }.size)
  }

  @Test
  fun sizeIsAMultipleOfAWeek() {
    assertEquals(0, calendarCells(november, DayOfWeek.MONDAY).size % DayOfWeek.entries.size)
  }

  @Test
  fun sundayFirstNovemberFillsFiveWeeks() {
    assertEquals(35, calendarCells(november, DayOfWeek.SUNDAY).size)
  }

  @Test
  fun mondayFirstNovemberFillsSixWeeks() {
    assertEquals(42, calendarCells(november, DayOfWeek.MONDAY).size)
  }

  @Test
  fun everyDayOfTheMonthAppearsOnce() {
    assertEquals((1..30).toList(), calendarCells(november, DayOfWeek.MONDAY).filterNotNull())
  }

  @Test
  fun leapFebruaryHasTwentyNineDays() {
    assertEquals(29, calendarCells(YearMonth.of(2028, 2), DayOfWeek.MONDAY).filterNotNull().size)
  }

  @Test
  fun nonLeapFebruaryStartingOnFirstColumnFillsFourWeeks() {
    // February 2027 has 28 days and starts on a Monday.
    assertEquals(28, calendarCells(YearMonth.of(2027, 2), DayOfWeek.MONDAY).size)
  }

  // ---------- default parameters ----------

  @Test
  fun defaultMonthIsTheCurrentMonth() {
    assertEquals(
        calendarCells(YearMonth.now(), DayOfWeek.SUNDAY),
        calendarCells(firstDayOfWeek = DayOfWeek.SUNDAY),
    )
  }

  @Test
  fun defaultFirstDayOfWeekIsMonday() {
    assertEquals(calendarCells(november, DayOfWeek.MONDAY), calendarCells(november))
  }

  @Test
  fun withoutParametersUsesBothDefaults() {
    assertEquals(calendarCells(YearMonth.now(), DayOfWeek.MONDAY), calendarCells())
  }
}
