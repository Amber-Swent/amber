// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.location

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

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
