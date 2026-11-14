// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.location

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** How close a pin must be to the user to become "near you", in meters. */
const val NEARBY_RADIUS_METERS = 100.0

/**
 * How much further than [NEARBY_RADIUS_METERS] a pin that is near you stays near, in meters. A
 * phone's position jitters by 10 to 30 m, so without this margin a pin at the edge would blink.
 */
const val NEARBY_EXIT_MARGIN_METERS = 30.0

/**
 * The worst accuracy, in meters, at which a position can still say which pins are near. Indoor GPS
 * is often 50 to 150 m off, and memories are often made indoors, so it still counts; the
 * approximate location the user can choose instead of the precise one (about 2 km) doesn't.
 */
const val NEARBY_MAX_ACCURACY_METERS = 200.0

/** Mean radius of the Earth, in meters, as used by the haversine formula. */
private const val EARTH_RADIUS_METERS = 6_371_000.0

/**
 * Distance in meters between two points along the Earth's surface (haversine formula, on a sphere:
 * off by under 0.5% from the real distance, far less than a phone's GPS error).
 */
fun distanceMeters(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): Double {
  // Haversine: hav(d / R) = hav(dLat) + cos(lat1) cos(lat2) hav(dLng), with hav(x) = sin²(x / 2)
  val dLat = Math.toRadians(toLat - fromLat)
  val dLng = Math.toRadians(toLng - fromLng)
  val a =
      sin(dLat / 2).pow(2) +
          cos(Math.toRadians(fromLat)) * cos(Math.toRadians(toLat)) * sin(dLng / 2).pow(2)
  // min: rounding can push a just above 1 for two opposite points
  return 2 * EARTH_RADIUS_METERS * asin(sqrt(min(a, 1.0)))
}

/** Distance in meters from [position] to this pin. */
fun MediaMarker.distanceMetersFrom(position: DevicePosition): Double =
    distanceMeters(position.lat, position.lng, lat, lng)

/**
 * The pins near [position], nearest first; between pins as near, the first one (the one with the
 * newest media). A pin is near within [NEARBY_RADIUS_METERS]. A pin of [nearBeforeIds], near at the
 * previous position, stays near up to [NEARBY_EXIT_MARGIN_METERS] further, so it doesn't blink at
 * the edge.
 */
fun List<MediaMarker>.nearby(
    position: DevicePosition,
    nearBeforeIds: Set<String> = emptySet(),
): List<MediaMarker> = map {
  it to it.distanceMetersFrom(position)
}
    .filter { (pin, distance) ->
      distance <= NEARBY_RADIUS_METERS ||
          (pin.id in nearBeforeIds && distance <= NEARBY_RADIUS_METERS + NEARBY_EXIT_MARGIN_METERS)
    }
    .sortedBy { (_, distance) -> distance }
    .map { (pin, _) -> pin }
