// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.location

import com.github.se.amber.model.location.TestPositions.EXACT_TOLERANCE_METERS
import com.github.se.amber.model.location.TestPositions.FLON_TO_CORNAVIN_METERS
import com.github.se.amber.model.location.TestPositions.GENEVA_CORNAVIN
import com.github.se.amber.model.location.TestPositions.HALF_EARTH_CIRCUMFERENCE_METERS
import com.github.se.amber.model.location.TestPositions.LAUSANNE_FLON
import com.github.se.amber.model.location.TestPositions.MAX_DISTANCE_ERROR
import com.github.se.amber.model.location.TestPositions.OPPOSITE_LONGITUDE
import com.github.se.amber.model.location.TestPositions.movedNorth
import com.github.se.amber.model.location.TestPositions.pinAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
  fun `distance is the real distance between two points, in both directions, up to opposite points`() {
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
    val opposite = DevicePosition(lat = 0.0, lng = OPPOSITE_LONGITUDE)
    assertEquals(
        HALF_EARTH_CIRCUMFERENCE_METERS,
        distance(onEquator, opposite),
        EXACT_TOLERANCE_METERS,
    )
  }

  @Test
  fun `the nearest pin within the radius is near you, the radius included, and it can be changed`() {
    val atHalfRadius = pinAt(here.movedNorth(halfRadius))
    val justInside = pinAt(here.movedNorth(-justInsideRadius))
    val justOutside = pinAt(here.movedNorth(justOutsideRadius))

    assertEquals(atHalfRadius, listOf(justOutside, justInside, atHalfRadius).nearestWithin(here))
    assertEquals(justInside, listOf(justOutside, justInside).nearestWithin(here))
    assertNull(listOf(justOutside).nearestWithin(here))
    assertNull(emptyList<MediaMarker>().nearestWithin(here))

    val distance = justOutside.distanceMetersFrom(here)
    assertEquals(justOutside, listOf(justOutside).nearestWithin(here, radiusMeters = distance))
    assertNull(
        listOf(justOutside).nearestWithin(here, radiusMeters = distance - EXACT_TOLERANCE_METERS)
    )
    assertEquals(
        justOutside,
        listOf(justOutside).nearestWithin(here, radiusMeters = distance + halfRadius),
    )
  }

  @Test
  fun `between pins at the same distance, the first one wins`() {
    val north = pinAt(here.movedNorth(halfRadius), id = "north")
    val south = pinAt(here.movedNorth(-halfRadius), id = "south")

    assertEquals(north, listOf(north, south).nearestWithin(here))
    assertEquals(south, listOf(south, north).nearestWithin(here))
  }
}
