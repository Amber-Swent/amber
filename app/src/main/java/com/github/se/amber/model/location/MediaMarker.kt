// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.location

import com.github.se.amber.model.media.MediaItem

/**
 * A pin on the memories map: one place, and the media that happened there.
 *
 * Built from the media by [toMediaMarkers], never stored. Media within [SAME_PLACE_RADIUS_METERS]
 * of a pin's first media share it, so the same café picked once from the saved places and once from
 * a search gives one pin, not two drawn on top of each other.
 *
 * @param id identifies the pin by its oldest media, which new media don't change, so an open pin
 *   can be found again after the media change.
 * @param lat latitude of the pin, from its oldest media, so the pin doesn't move when media are
 *   added.
 * @param lng longitude of the pin, from its oldest media.
 * @param placeName the place name most of its media have; between names as common, the newest
 *   one's; null if none has one.
 * @param media the media at this place, newest first; never empty.
 */
data class MediaMarker(
    val id: String,
    val lat: Double,
    val lng: Double,
    val placeName: String?,
    val media: List<MediaItem>,
)

/**
 * How far from a pin's first media other media can be and still share the pin, in meters: about a
 * building, more than two searches for the same place usually differ by.
 */
const val SAME_PLACE_RADIUS_METERS = 25.0

/**
 * The pins for these media, given newest first, in the order of their newest media. Media without a
 * real point (see [GeoLocation.pointOrNull]) are left out, so they get no pin.
 *
 * Media are grouped oldest first: each media joins the first pin whose oldest media is within
 * [SAME_PLACE_RADIUS_METERS], or starts its own. So a new media never moves a pin nor changes its
 * id.
 */
fun List<MediaItem>.toMediaMarkers(): List<MediaMarker> {
  val pins = mutableListOf<PinBuilder>()
  for (index in indices.reversed()) { // oldest first
    val media = this[index]
    val location = media.location ?: continue
    val (lat, lng) = location.pointOrNull() ?: continue
    val pin =
        pins.firstOrNull { distanceMeters(it.lat, it.lng, lat, lng) <= SAME_PLACE_RADIUS_METERS }
            ?: PinBuilder(id = media.id, lat = lat, lng = lng).also { pins += it }
    pin.media.add(0, media) // newest first
    location.placeName?.takeIf { it.isNotBlank() }?.let { pin.placeNames.add(0, it) }
    pin.newestIndex = index
  }
  return pins
      .sortedBy { it.newestIndex }
      .map { pin ->
        MediaMarker(
            id = pin.id,
            lat = pin.lat,
            lng = pin.lng,
            placeName = pin.placeNames.mostCommon(),
            media = pin.media.toList(),
        )
      }
}

/**
 * A pin being built: the point of its oldest media, its media and their non-blank place names so
 * far (both newest first), and its newest media's index.
 */
private class PinBuilder(val id: String, val lat: Double, val lng: Double) {
  val media = mutableListOf<MediaItem>()
  val placeNames = mutableListOf<String>()
  var newestIndex = 0
}

/** The name these names have most often; between names as common, the first one. */
private fun List<String>.mostCommon(): String? = groupingBy {
  it
}
    .eachCount()
    .maxByOrNull { (_, count) -> count }
    ?.key
