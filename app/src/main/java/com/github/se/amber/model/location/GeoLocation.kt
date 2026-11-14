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

/** Valid latitudes, in degrees. */
private val LATITUDE_RANGE = -90.0..90.0

/** Valid longitudes, in degrees. */
private val LONGITUDE_RANGE = -180.0..180.0

/**
 * This place's point on the map as (lat, lng), or null when it isn't a real point: a coordinate is
 * missing (a place known only by its name), out of range, or not a number. A function, not a
 * property, so Firestore doesn't store it.
 */
fun GeoLocation.pointOrNull(): Pair<Double, Double>? {
  val lat = lat?.takeIf { it in LATITUDE_RANGE } ?: return null
  val lng = lng?.takeIf { it in LONGITUDE_RANGE } ?: return null
  return lat to lng
}
