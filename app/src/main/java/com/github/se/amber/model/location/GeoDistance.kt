// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.location

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** How close a pin must be to the user to count as "near you", in meters. */
const val NEARBY_RADIUS_METERS = 100.0

/**
 * The worst accuracy, in meters, at which a position can still say which pin is near: a position
 * less precise than the radius could be on either side of it.
 */
const val NEARBY_MAX_ACCURACY_METERS = NEARBY_RADIUS_METERS

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
 * The pin nearest to [position], if it is at most [radiusMeters] away; null if no pin is that
 * close. Between pins at the same distance, the first one wins, so the one with the newest media.
 */
fun List<MediaMarker>.nearestWithin(
    position: DevicePosition,
    radiusMeters: Double = NEARBY_RADIUS_METERS,
): MediaMarker? = map {
  it to it.distanceMetersFrom(position)
}
    .filter { (_, distance) -> distance <= radiusMeters }
    .minByOrNull { (_, distance) -> distance }
    ?.first
