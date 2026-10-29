/* Written by LaMaier, with assistance from
 * Claude (Anthropic) via Claude Code.
 * Modified with assistance from OpenAI Codex.
 */
package com.github.se.amber.ui.caregiver

import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.ui.caregiver.upload.DateField
import java.time.Month
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for [DateField]: no Android, no network, no UI. */
class DateFieldTest {

  private val leapDay = MemoryDate(2024, Month.FEBRUARY, 29)
  private val nonLeapFebruary = MemoryDate(2023, Month.FEBRUARY, 15)
  private val thirtyOneMonth = MemoryDate(2024, Month.JANUARY, 31)
  private val thirtyMonth = MemoryDate(2024, Month.APRIL, 10)

  // ---------- range ----------

  @Test
  fun range_day_isEmptyWhenMonthIsUnknown() {
    assertTrue(DateField.DAY.range(MemoryDate(2024)).isEmpty())
  }

  @Test
  fun range_year_startsAtFixedMinimum() {
    assertEquals(1900, DateField.YEAR.range(thirtyOneMonth, currentYear = 2026).first)
  }

  @Test
  fun range_year_endsAtCurrentYear() {
    assertEquals(2026, DateField.YEAR.range(thirtyOneMonth, currentYear = 2026).last)
  }

  @Test
  fun range_year_doesNotShiftWithSelectedDate() {
    assertEquals(
        DateField.YEAR.range(thirtyOneMonth, currentYear = 2026),
        DateField.YEAR.range(MemoryDate(1930), currentYear = 2026),
    )
  }

  @Test
  fun range_month_startsAtJanuary() {
    assertEquals(Month.JANUARY.value, DateField.MONTH.range(thirtyOneMonth).first)
  }

  @Test
  fun range_month_endsAtDecember() {
    assertEquals(Month.DECEMBER.value, DateField.MONTH.range(thirtyOneMonth).last)
  }

  @Test
  fun range_day_startsAtOne() {
    assertEquals(1, DateField.DAY.range(thirtyOneMonth).first)
  }

  @Test
  fun range_day_endsAt29InLeapFebruary() {
    assertEquals(29, DateField.DAY.range(leapDay).last)
  }

  @Test
  fun range_day_endsAt28InNonLeapFebruary() {
    assertEquals(28, DateField.DAY.range(nonLeapFebruary).last)
  }

  @Test
  fun range_day_endsAt30InShortMonth() {
    assertEquals(30, DateField.DAY.range(thirtyMonth).last)
  }

  @Test
  fun range_day_endsAt31InLongMonth() {
    assertEquals(31, DateField.DAY.range(thirtyOneMonth).last)
  }

  // ---------- valueIn ----------

  @Test
  fun valueIn_year_returnsYear() {
    assertEquals(leapDay.year, DateField.YEAR.valueIn(leapDay))
  }

  @Test
  fun valueIn_month_returnsMonthValue() {
    assertEquals(Month.FEBRUARY.value, DateField.MONTH.valueIn(leapDay))
  }

  @Test
  fun valueIn_day_returnsDayOfMonth() {
    assertEquals(leapDay.day, DateField.DAY.valueIn(leapDay))
  }

  // ---------- applyTo ----------

  @Test
  fun applyTo_unknownMonthClearsDay() {
    assertEquals(MemoryDate(2024), DateField.MONTH.applyTo(leapDay, null))
  }

  @Test
  fun applyTo_dayStaysUnknownWhenMonthIsUnknown() {
    assertEquals(MemoryDate(2024), DateField.DAY.applyTo(MemoryDate(2024), 15))
  }

  @Test
  fun applyTo_yearKeepsUnknownDayUnknown() {
    assertEquals(
        MemoryDate(2023, Month.FEBRUARY),
        DateField.YEAR.applyTo(MemoryDate(2024, Month.FEBRUARY), 2023),
    )
  }

  @Test
  fun applyTo_monthKeepsUnknownDayUnknown() {
    assertEquals(
        MemoryDate(2024, Month.FEBRUARY),
        DateField.MONTH.applyTo(MemoryDate(2024, Month.JANUARY), Month.FEBRUARY.value),
    )
  }

  @Test
  fun applyTo_year_replacesOnlyTheYear() {
    assertEquals(
        MemoryDate(2030, Month.APRIL, 10),
        DateField.YEAR.applyTo(thirtyMonth, 2030),
    )
  }

  @Test
  fun applyTo_month_replacesOnlyTheMonth() {
    assertEquals(
        MemoryDate(2024, Month.MARCH, 10),
        DateField.MONTH.applyTo(thirtyMonth, Month.MARCH.value),
    )
  }

  @Test
  fun applyTo_day_replacesOnlyTheDay() {
    assertEquals(
        MemoryDate(2024, Month.APRIL, 1),
        DateField.DAY.applyTo(thirtyMonth, 1),
    )
  }

  @Test
  fun applyTo_month_clampsDayWhenTargetMonthIsShorter() {
    assertEquals(
        MemoryDate(2024, Month.FEBRUARY, 29),
        DateField.MONTH.applyTo(thirtyOneMonth, Month.FEBRUARY.value),
    )
  }

  @Test
  fun applyTo_year_clampsLeapDayWhenTargetYearIsNotLeap() {
    assertEquals(
        MemoryDate(2023, Month.FEBRUARY, 28),
        DateField.YEAR.applyTo(leapDay, 2023),
    )
  }

  // ---------- format ----------

  @Test
  fun format_monthUsesFullLocalizedName() {
    assertEquals("March", DateField.MONTH.format(3, Locale.ENGLISH))
    assertEquals("mars", DateField.MONTH.format(3, Locale.FRENCH))
  }

  @Test
  fun formatCompact_monthUsesShortName() {
    assertEquals("Mar", DateField.MONTH.formatCompact(3, Locale.ENGLISH))
  }

  @Test
  fun format_day_isPaddedToTwoDigits() {
    assertEquals("07", DateField.DAY.format(7))
  }

  @Test
  fun format_day_keepsTwoDigitValueUntouched() {
    assertEquals("31", DateField.DAY.format(31))
  }
}
