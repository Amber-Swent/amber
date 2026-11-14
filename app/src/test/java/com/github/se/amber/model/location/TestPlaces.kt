// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.location

import kotlin.math.PI
import kotlin.math.nextDown
import kotlin.math.nextUp

/**
 * Places shared by the memories map tests, and moves measured in meters, so the tests read in
 * meters and in terms of the code's own distances instead of raw degrees.
 */
object TestPlaces {

  /** Lausanne Flon station. */
  val LAUSANNE = GeoLocation(lat = 46.520856, lng = 6.630127, placeName = "Lausanne")

  /** Geneva Cornavin station. */
  val GENEVA = GeoLocation(lat = 46.210208, lng = 6.142438, placeName = "Geneva")

  /** The latitude of this place; it must have one. */
  val GeoLocation.latitude: Double
    get() = checkNotNull(lat) { "$this has no latitude" }

  /** The longitude of this place; it must have one. */
  val GeoLocation.longitude: Double
    get() = checkNotNull(lng) { "$this has no longitude" }

  /** The Earth's mean radius, in meters. */
  const val MEAN_EARTH_RADIUS_METERS = 6_371_000.0

  /** Half the Earth's circumference: the distance between two opposite points. */
  const val HALF_EARTH_CIRCUMFERENCE_METERS = PI * MEAN_EARTH_RADIUS_METERS

  /** Meters in one degree of latitude, the same everywhere on a sphere. */
  const val METERS_PER_DEGREE_OF_LATITUDE = HALF_EARTH_CIRCUMFERENCE_METERS / MAX_LONGITUDE

  /** This place moved [meters] north (south if negative). */
  fun GeoLocation.movedNorth(meters: Double): GeoLocation =
      copy(lat = latitude + meters / METERS_PER_DEGREE_OF_LATITUDE)

  /** The smallest latitude above [MAX_LATITUDE], just out of range. */
  val JUST_ABOVE_MAX_LATITUDE = MAX_LATITUDE.nextUp()

  /** The largest longitude below −[MAX_LONGITUDE], just out of range. */
  val JUST_BELOW_MIN_LONGITUDE = (-MAX_LONGITUDE).nextDown()
}
