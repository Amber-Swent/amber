package com.github.se.amber.ui.caregiver

import com.github.se.amber.ui.caregiver.upload.DateField
import java.time.LocalDate
import java.time.Month
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure JVM tests for [DateField]: no Android, no network, no UI. */
class DateFieldTest {

  private val leapDay = LocalDate.of(2024, Month.FEBRUARY, 29)
  private val nonLeapFebruary = LocalDate.of(2023, Month.FEBRUARY, 15)
  private val thirtyOneMonth = LocalDate.of(2024, Month.JANUARY, 31)
  private val thirtyMonth = LocalDate.of(2024, Month.APRIL, 10)

  // ---------- range ----------

  @Test
  fun range_year_startsOneHundredYearsBeforeDate() {
    assertEquals(leapDay.year - 100, DateField.YEAR.range(thirtyOneMonth).first)
  }

  @Test
  fun range_year_endsOneHundredYearsAfterDate() {
    assertEquals(leapDay.year + 100, DateField.YEAR.range(thirtyOneMonth).last)
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
    assertEquals(leapDay.dayOfMonth, DateField.DAY.valueIn(leapDay))
  }

  // ---------- applyTo ----------

  @Test
  fun applyTo_year_replacesOnlyTheYear() {
    assertEquals(
        LocalDate.of(2030, Month.APRIL, 10),
        DateField.YEAR.applyTo(thirtyMonth, 2030),
    )
  }

  @Test
  fun applyTo_month_replacesOnlyTheMonth() {
    assertEquals(
        LocalDate.of(2024, Month.MARCH, 10),
        DateField.MONTH.applyTo(thirtyMonth, Month.MARCH.value),
    )
  }

  @Test
  fun applyTo_day_replacesOnlyTheDay() {
    assertEquals(
        LocalDate.of(2024, Month.APRIL, 1),
        DateField.DAY.applyTo(thirtyMonth, 1),
    )
  }

  @Test
  fun applyTo_month_clampsDayWhenTargetMonthIsShorter() {
    assertEquals(
        LocalDate.of(2024, Month.FEBRUARY, 29),
        DateField.MONTH.applyTo(thirtyOneMonth, Month.FEBRUARY.value),
    )
  }

  @Test
  fun applyTo_year_clampsLeapDayWhenTargetYearIsNotLeap() {
    assertEquals(
        LocalDate.of(2023, Month.FEBRUARY, 28),
        DateField.YEAR.applyTo(leapDay, 2023),
    )
  }

  // ---------- format ----------

  @Test
  fun format_month_isPaddedToTwoDigits() {
    assertEquals("03", DateField.MONTH.format(3))
  }

  @Test
  fun format_month_keepsTwoDigitValueUntouched() {
    assertEquals("12", DateField.MONTH.format(12))
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
