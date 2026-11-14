// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.location

/**
 * Where the phone is now, from its GPS. Never stored nor uploaded: it only lives in memory while
 * the map is open.
 *
 * @param lat latitude, in degrees.
 * @param lng longitude, in degrees.
 * @param accuracyMeters radius around the point within which the phone likely is; null if unknown.
 */
data class DevicePosition(val lat: Double, val lng: Double, val accuracyMeters: Float? = null)

/**
 * Whether this position is known to within [meters]: false when its accuracy is unknown or worse,
 * as with the approximate location the user can choose instead of the precise one, or a poor GPS
 * fix indoors.
 */
fun DevicePosition.isPreciseWithin(meters: Double): Boolean =
    accuracyMeters?.let { it <= meters } ?: false
