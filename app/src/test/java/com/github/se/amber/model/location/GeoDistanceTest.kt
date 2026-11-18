// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.location

import com.github.se.amber.model.location.TestPlaces.HALF_EARTH_CIRCUMFERENCE_METERS
import com.github.se.amber.model.location.TestPositions.EXACT_TOLERANCE_METERS
import com.github.se.amber.model.location.TestPositions.FLON_TO_CORNAVIN_METERS
import com.github.se.amber.model.location.TestPositions.GENEVA_CORNAVIN
import com.github.se.amber.model.location.TestPositions.LAUSANNE_FLON
import com.github.se.amber.model.location.TestPositions.MAX_DISTANCE_ERROR
import com.github.se.amber.model.location.TestPositions.movedNorth
import com.github.se.amber.model.location.TestPositions.pinAt
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoDistanceTest {

  private val here = LAUSANNE_FLON
  private val radius = NEARBY_RADIUS_METERS
  private val halfRadius = radius / 2
  private val justInsideRadius = radius * 0.9
  private val justOutsideRadius = radius * 1.1

  private fun distance(from: DevicePosition, to: DevicePosition) =
      distanceMeters(from.lat, from.lng, to.lat, to.lng)

  @Test
  fun distanceIsTheRealDistanceBetweenTwoPointsInBothDirectionsUpToOppositePoints() {
    val lausanneToGeneva = distance(LAUSANNE_FLON, GENEVA_CORNAVIN)

    assertEquals(
        FLON_TO_CORNAVIN_METERS,
        lausanneToGeneva,
        FLON_TO_CORNAVIN_METERS * MAX_DISTANCE_ERROR,
    )
    assertEquals(lausanneToGeneva, distance(GENEVA_CORNAVIN, LAUSANNE_FLON), EXACT_TOLERANCE_METERS)
    assertEquals(0.0, distance(here, here), EXACT_TOLERANCE_METERS)
    assertEquals(radius, distance(here, here.movedNorth(radius)), EXACT_TOLERANCE_METERS)

    val onEquator = DevicePosition(lat = 0.0, lng = 0.0)
    val opposite = DevicePosition(lat = 0.0, lng = MAX_LONGITUDE)
    assertEquals(
        HALF_EARTH_CIRCUMFERENCE_METERS,
        distance(onEquator, opposite),
        EXACT_TOLERANCE_METERS,
    )
  }

  @Test
  fun pinsWithinTheRadiusAreNearYouNearestFirstTheFirstOneBetweenEquals() {
    val atHalfRadius = pinAt(here.movedNorth(halfRadius), id = "north")
    val southAtHalfRadius = pinAt(here.movedNorth(-halfRadius), id = "south")
    val justInside = pinAt(here.movedNorth(-justInsideRadius))
    val justOutside = pinAt(here.movedNorth(justOutsideRadius))

    assertEquals(
        listOf(atHalfRadius, justInside),
        listOf(justOutside, justInside, atHalfRadius).nearby(here),
    )
    assertEquals(
        listOf(atHalfRadius, southAtHalfRadius),
        listOf(atHalfRadius, southAtHalfRadius).nearby(here),
    )
    assertEquals(
        listOf(southAtHalfRadius, atHalfRadius),
        listOf(southAtHalfRadius, atHalfRadius).nearby(here),
    )
    assertEquals(emptyList<MediaMarker>(), emptyList<MediaMarker>().nearby(here))
  }

  @Test
  fun aPinNearBeforeStaysNearUpToTheExitMarginOneNotNearBeforeDoesnt() {
    val inExitMargin = pinAt(here.movedNorth(radius + NEARBY_EXIT_MARGIN_METERS / 2))
    val pastExitMargin = pinAt(here.movedNorth(radius + NEARBY_EXIT_MARGIN_METERS * 2))
    val nearBefore = setOf(inExitMargin.id, pastExitMargin.id)

    assertEquals(emptyList<MediaMarker>(), listOf(inExitMargin).nearby(here))
    assertEquals(
        listOf(inExitMargin),
        listOf(inExitMargin, pastExitMargin).nearby(here, nearBefore),
    )
  }
}
