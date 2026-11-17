// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.location

/**
 * A place on the map. Our own type, not Firebase's GeoPoint. Embedded as a map {lat, lng,
 * placeName} in the documents that use it (MediaItem, Appointment, Place), never stored on its own.
 *
 * A place known only by a typed name (e.g. "Grandma's house" for an old photo) has no [lat] or
 * [lng]: they stay null rather than 0.0, which is a real point in the Gulf of Guinea.
 */
data class GeoLocation(
    val lat: Double? = null,
    val lng: Double? = null,
    val placeName: String? = null, // human-readable, e.g. typed by the user for an old photo
)

/**
 * This place's point on the map as (lat, lng), or null when it isn't a real point: a coordinate is
 * missing (a place known only by its name), out of range, or not a number, or the place is exactly
 * (0, 0). That point is in the ocean, and a place there is almost surely a default value that was
 * saved by mistake, so it gets no pin. A function, not a property, so Firestore doesn't store it.
 */
fun GeoLocation.pointOrNull(): Pair<Double, Double>? {
  val lat = lat?.takeIf { it in LATITUDE_RANGE } ?: return null
  val lng = lng?.takeIf { it in LONGITUDE_RANGE } ?: return null
  if (lat == 0.0 && lng == 0.0) return null
  return lat to lng
}

/** The largest latitude, at the poles, in degrees; the smallest is its opposite. */
internal const val MAX_LATITUDE = 90.0

/** The largest longitude, in degrees; the smallest is its opposite. */
internal const val MAX_LONGITUDE = 180.0

private val LATITUDE_RANGE = -MAX_LATITUDE..MAX_LATITUDE

private val LONGITUDE_RANGE = -MAX_LONGITUDE..MAX_LONGITUDE
