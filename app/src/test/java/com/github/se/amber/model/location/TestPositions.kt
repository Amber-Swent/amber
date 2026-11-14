// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.location

import com.github.se.amber.model.location.TestPlaces.latitude
import com.github.se.amber.model.location.TestPlaces.longitude
import com.github.se.amber.model.media.MediaItem
import kotlin.math.PI

/**
 * Places and distances shared by the location tests, so they read in meters and in terms of
 * [NEARBY_RADIUS_METERS] instead of raw degrees.
 */
object TestPositions {

  /** Lausanne Flon station, as [TestPlaces.LAUSANNE]. */
  val LAUSANNE_FLON = DevicePosition(TestPlaces.LAUSANNE.latitude, TestPlaces.LAUSANNE.longitude)

  /** Geneva Cornavin station, as [TestPlaces.GENEVA]. */
  val GENEVA_CORNAVIN = DevicePosition(TestPlaces.GENEVA.latitude, TestPlaces.GENEVA.longitude)

  /** The straight-line distance between [LAUSANNE_FLON] and [GENEVA_CORNAVIN], from a map. */
  const val FLON_TO_CORNAVIN_METERS = 50_900.0

  /** The largest error of the distance on a sphere, as a fraction of the distance (0.5%). */
  const val MAX_DISTANCE_ERROR = 0.005

  /** The Earth's mean radius, in meters. */
  const val MEAN_EARTH_RADIUS_METERS = 6_371_000.0

  /** Half the Earth's circumference: the distance between two opposite points. */
  const val HALF_EARTH_CIRCUMFERENCE_METERS = PI * MEAN_EARTH_RADIUS_METERS

  /** Degrees of longitude between two opposite points on the equator. */
  const val OPPOSITE_LONGITUDE = 180.0

  /** Meters in one degree of latitude, the same everywhere on a sphere. */
  const val METERS_PER_DEGREE_OF_LATITUDE = HALF_EARTH_CIRCUMFERENCE_METERS / OPPOSITE_LONGITUDE

  /** Tolerance for distances that are exact on a sphere, in meters. */
  const val EXACT_TOLERANCE_METERS = 1e-6

  /** The accuracy of a good GPS fix outdoors. */
  const val GPS_ACCURACY_METERS = 10f

  /** The accuracy of the approximate location the user can choose instead of the precise one. */
  const val APPROXIMATE_ACCURACY_METERS = 2_000f

  /** This position moved [meters] north (south if negative). */
  fun DevicePosition.movedNorth(meters: Double) =
      copy(lat = lat + meters / METERS_PER_DEGREE_OF_LATITUDE)

  /** A pin with one picture at [position]. */
  fun pinAt(position: DevicePosition, id: String = "p-${position.lat}"): MediaMarker =
      listOf(
              MediaItem.Picture(
                  id = id,
                  location = GeoLocation(lat = position.lat, lng = position.lng),
              )
          )
          .toMediaMarkers()
          .single()
}
