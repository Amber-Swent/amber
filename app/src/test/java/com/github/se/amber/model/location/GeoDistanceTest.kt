// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.location

import com.github.se.amber.model.location.TestPlaces.GENEVA
import com.github.se.amber.model.location.TestPlaces.HALF_EARTH_CIRCUMFERENCE_METERS
import com.github.se.amber.model.location.TestPlaces.LAUSANNE
import com.github.se.amber.model.location.TestPlaces.latitude
import com.github.se.amber.model.location.TestPlaces.longitude
import com.github.se.amber.model.location.TestPlaces.movedNorth
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoDistanceTest {

  /** The straight-line distance between Lausanne Flon and Geneva Cornavin, from a map. */
  private val flonToCornavinMeters = 50_900.0

  /** The largest error of the distance on a sphere, as a fraction of the distance (0.5%). */
  private val maxDistanceError = 0.005

  /** Tolerance for distances that are exact on a sphere, in meters. */
  private val exactTolerance = 1e-6

  private fun distance(from: GeoLocation, to: GeoLocation) =
      distanceMeters(from.latitude, from.longitude, to.latitude, to.longitude)

  @Test
  fun distanceIsTheRealDistanceBetweenTwoPointsInBothDirectionsUpToOppositePoints() {
    val lausanneToGeneva = distance(LAUSANNE, GENEVA)

    assertEquals(flonToCornavinMeters, lausanneToGeneva, flonToCornavinMeters * maxDistanceError)
    assertEquals(lausanneToGeneva, distance(GENEVA, LAUSANNE), exactTolerance)
    assertEquals(0.0, distance(LAUSANNE, LAUSANNE), exactTolerance)
    assertEquals(
        SAME_PLACE_RADIUS_METERS,
        distance(LAUSANNE, LAUSANNE.movedNorth(SAME_PLACE_RADIUS_METERS)),
        exactTolerance,
    )

    val onEquator = GeoLocation(lat = 0.0, lng = 0.0)
    val opposite = GeoLocation(lat = 0.0, lng = MAX_LONGITUDE)
    assertEquals(HALF_EARTH_CIRCUMFERENCE_METERS, distance(onEquator, opposite), exactTolerance)
  }
}
