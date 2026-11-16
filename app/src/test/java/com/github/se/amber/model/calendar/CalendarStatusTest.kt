// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.calendar

import com.github.se.amber.model.location.GeoLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

  private val minute = 60_000L
  private val start = 10 * SOON_WINDOW_MILLIS
  private val event = Event(startAt = start, durationMinutes = 30)
  private val end = start + 30 * minute

  @Test
  fun endAtAddsTheDurationToTheStart() {
    assertEquals(end, event.endAt())
  }

  @Test
  fun anUnknownDurationCountsAsTheDefault() {
    val unknown = Event(startAt = start, durationMinutes = null)

    assertEquals(start + DEFAULT_DURATION_MINUTES * minute, unknown.endAt())
    assertEquals(
        CalendarStatus.ONGOING,
        unknown.statusAt(start + (DEFAULT_DURATION_MINUTES - 1) * minute),
    )
  }

  @Test
  fun aDurationOfZeroOrLessCountsAsTheDefault() {
    val defaultEnd = start + DEFAULT_DURATION_MINUTES * minute

    assertEquals(defaultEnd, Event(start, 0).endAt())
    assertEquals(defaultEnd, Event(start, -5).endAt())
  }

  @Test
  fun itIsPlannedUntilTheSoonWindowStarts() {
    assertEquals(CalendarStatus.PLANNED, event.statusAt(0L))
    assertEquals(CalendarStatus.PLANNED, event.statusAt(start - SOON_WINDOW_MILLIS - 1))
  }

  @Test
  fun itIsSoonFromOneDayBeforeTheStart() {
    assertEquals(CalendarStatus.SOON, event.statusAt(start - SOON_WINDOW_MILLIS))
    assertEquals(CalendarStatus.SOON, event.statusAt(start - 1))
  }

  @Test
  fun itIsOngoingFromTheStartUntilTheEnd() {
    assertEquals(CalendarStatus.ONGOING, event.statusAt(start))
    assertEquals(CalendarStatus.ONGOING, event.statusAt(end - 1))
  }

  @Test
  fun itHasPassedFromTheEnd() {
    assertEquals(CalendarStatus.PASSED, event.statusAt(end))
    assertEquals(CalendarStatus.PASSED, event.statusAt(end + SOON_WINDOW_MILLIS))
  }

  @Test
  fun aCustomWindowMovesTheSoonBoundary() {
    val window = 2 * 60 * minute

    assertEquals(CalendarStatus.PLANNED, event.statusAt(start - window - 1, window))
    assertEquals(CalendarStatus.SOON, event.statusAt(start - window, window))
  }

  @Test
  fun aWindowOfZeroGoesFromPlannedStraightToOngoing() {
    assertEquals(CalendarStatus.PLANNED, event.statusAt(start - 1, soonWindowMillis = 0L))
    assertEquals(CalendarStatus.ONGOING, event.statusAt(start, soonWindowMillis = 0L))
    assertEquals(start, event.nextStatusChangeAt(start - 1, soonWindowMillis = 0L))
  }

  @Test(expected = IllegalArgumentException::class)
  fun aNegativeWindowIsRefused() {
    event.statusAt(start, soonWindowMillis = -1L)
  }

  @Test
  fun theNextChangeIsTheNextBoundary() {
    assertEquals(start - SOON_WINDOW_MILLIS, event.nextStatusChangeAt(0L))
    assertEquals(start, event.nextStatusChangeAt(start - SOON_WINDOW_MILLIS))
    assertEquals(end, event.nextStatusChangeAt(start))
    assertEquals(end, event.nextStatusChangeAt(end - 1))
  }

  @Test
  fun thereIsNoNextChangeOnceItHasPassed() {
    assertNull(event.nextStatusChangeAt(end))
  }

  @Test
  fun theNextChangeUsesTheGivenWindow() {
    val window = 2 * 60 * minute

    assertEquals(start - window, event.nextStatusChangeAt(0L, window))
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
    assertNull(event.nextStatusChangeAt(now))
  }

  @Test
  fun theNextChangeOfAnEventWithoutDurationIsTheDefaultEnd() {
    val unknown = Event(startAt = start, durationMinutes = null)

    assertEquals(start + DEFAULT_DURATION_MINUTES * minute, unknown.nextStatusChangeAt(start))
  }

  @Test(expected = IllegalArgumentException::class)
  fun theNextChangeRefusesANegativeWindow() {
    event.nextStatusChangeAt(start, soonWindowMillis = -1L)
  }
}
