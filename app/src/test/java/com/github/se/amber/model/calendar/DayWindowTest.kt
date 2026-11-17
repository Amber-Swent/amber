// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.calendar

import com.github.se.amber.model.appointment.Appointment
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DayWindowTest {
  private val zurich = ZoneId.of("Europe/Zurich")
  private val hour = 1.hours.inWholeMilliseconds

  @Test
  fun aWindowFollowsTheDaysOfTheZone() {
    // Written out: midnight in Zurich is 23:00 UTC the day before in winter.
    val week = dayWindow(LocalDate.of(2026, 11, 17), 7, zurich)
    assertEquals(DayWindow(1_794_870_000_000L, 1_794_870_000_000L + 7 * 24 * hour), week)

    // The clocks change on 29 March and 25 October 2026.
    val spring = dayWindow(LocalDate.of(2026, 3, 29), 1, zurich)
    assertEquals(DayWindow(1_774_738_800_000L, 1_774_738_800_000L + 23 * hour), spring)
    val autumn = dayWindow(LocalDate.of(2026, 10, 25), 1, zurich)
    assertEquals(DayWindow(1_792_879_200_000L, 1_792_879_200_000L + 25 * hour), autumn)
  }

  @Test
  fun aWindowOfZeroDaysOrLessIsRefused() {
    val day = LocalDate.of(2026, 11, 17)
    assertThrows(IllegalArgumentException::class.java) { dayWindow(day, 0, zurich) }
    assertThrows(IllegalArgumentException::class.java) { dayWindow(day, -1, zurich) }
  }

  @Test
  fun anEventOverlapsTheWindowWhileAnyPartOfItIsInside() {
    val window = DayWindow(startMillis = 100 * hour, endMillis = 124 * hour)
    // Each event lasts 60 minutes.
    val cases =
        listOf(
            "ends at the start" to (99 * hour to false),
            "ends 1 minute after the start" to (99 * hour + 1.minutes.inWholeMilliseconds to true),
            "inside" to (110 * hour to true),
            "starts 1 ms before the end" to (124 * hour - 1 to true),
            "starts at the end" to (124 * hour to false),
        )

    for ((label, case) in cases) {
      val (startAt, expected) = case
      val event = Appointment(title = "Physio", startAt = startAt, durationMinutes = 60)
      assertEquals(label, expected, event.overlaps(window))
    }
  }
}
