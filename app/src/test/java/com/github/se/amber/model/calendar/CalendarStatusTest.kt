// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.calendar

import com.github.se.amber.model.location.GeoLocation
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarStatusTest {

  private data class Event(
      override val startAt: Long,
      override val durationMinutes: Int?,
      override val id: String = "e1",
      override val title: String = "Physio",
      override val notes: String = "",
      override val location: GeoLocation? = null,
      override val attendeeIds: List<String> = emptyList(),
  ) : CalendarEvent

  private val start = 10.days.inWholeMilliseconds
  private val event = Event(startAt = start, durationMinutes = 30)
  private val end = start + 30.minutes.inWholeMilliseconds

  @Test
  fun endAtAddsTheDurationToTheStart() {
    assertEquals(end, event.endAt())
  }

  @Test
  fun anUnknownDurationAndOneOfZeroOrLessCountAsTheDefault() {
    val defaultEnd = start + 60.minutes.inWholeMilliseconds

    listOf(null, 0, -5).forEach { minutes ->
      val withDefault = Event(startAt = start, durationMinutes = minutes)

      assertEquals("duration $minutes", defaultEnd, withDefault.endAt())
      assertEquals("duration $minutes", defaultEnd, withDefault.nextStatusChangeAt(start))
    }
  }

  @Test
  fun eachStatusStartsAtItsBoundary() {
    // The times are written out, not computed from SOON_WINDOW_MILLIS, so a wrong window fails.
    val cases =
        listOf(
            "long before" to (0L to CalendarStatus.PLANNED),
            "25 h before" to (start - 25.hours.inWholeMilliseconds to CalendarStatus.PLANNED),
            "just over 24 h before" to
                (start - 24.hours.inWholeMilliseconds - 1 to CalendarStatus.PLANNED),
            "24 h before" to (start - 24.hours.inWholeMilliseconds to CalendarStatus.SOON),
            "23 h before" to (start - 23.hours.inWholeMilliseconds to CalendarStatus.SOON),
            "just before the start" to (start - 1 to CalendarStatus.SOON),
            "at the start" to (start to CalendarStatus.ONGOING),
            "just before the end" to (end - 1 to CalendarStatus.ONGOING),
            "at the end" to (end to CalendarStatus.PASSED),
            "a day after the end" to (end + 1.days.inWholeMilliseconds to CalendarStatus.PASSED),
        )

    cases.forEach { (label, case) ->
      val (now, expected) = case
      assertEquals(label, expected, event.statusAt(now))
    }
  }

  @Test
  fun aCustomWindowMovesTheSoonBoundary() {
    val window = 2.hours.inWholeMilliseconds

    assertEquals(CalendarStatus.PLANNED, event.statusAt(start - window - 1, window))
    assertEquals(CalendarStatus.SOON, event.statusAt(start - window, window))
    assertEquals(start - window, event.nextStatusChangeAt(0L, window))
  }

  @Test
  fun aWindowOfZeroGoesFromPlannedStraightToOngoing() {
    assertEquals(CalendarStatus.PLANNED, event.statusAt(start - 1, soonWindowMillis = 0L))
    assertEquals(CalendarStatus.ONGOING, event.statusAt(start, soonWindowMillis = 0L))
    assertEquals(start, event.nextStatusChangeAt(start - 1, soonWindowMillis = 0L))
  }

  @Test
  fun aNegativeWindowIsRefused() {
    assertThrows(IllegalArgumentException::class.java) {
      event.statusAt(start, soonWindowMillis = -1L)
    }
    assertThrows(IllegalArgumentException::class.java) {
      event.nextStatusChangeAt(start, soonWindowMillis = -1L)
    }
  }

  @Test
  fun eachNextChangeIsTheFirstMomentOfTheNextStatus() {
    var now = 0L
    val seen = mutableListOf(event.statusAt(now))
    repeat(CalendarStatus.entries.size) {
      val next = event.nextStatusChangeAt(now) ?: return@repeat
      assertTrue(next > now)
      // Just before the change, the status is still the current one.
      assertEquals(event.statusAt(now), event.statusAt(next - 1))
      now = next
      seen += event.statusAt(now)
    }

    assertEquals(CalendarStatus.entries.toList(), seen)
    // Once it has passed, no change follows.
    assertNull(event.nextStatusChangeAt(now))
  }
}
