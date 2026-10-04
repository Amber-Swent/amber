// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

/**
 * Place attached to a memory. Our own type, not Firebase's GeoPoint. Embedded in a MediaItem as a
 * map {lat, lng, placeName}, never stored on its own.
 */
data class GeoLocation(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val placeName: String? = null, // human-readable, e.g. typed by the user for an old photo
)
