// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.location

/**
 * A place on the map. Our own type, not Firebase's GeoPoint. Embedded as a map {lat, lng,
 * placeName} in the documents that use it (MediaItem, Appointment, Place), never stored on its own.
 */
data class GeoLocation(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val placeName: String? = null, // human-readable, e.g. typed by the user for an old photo
)
