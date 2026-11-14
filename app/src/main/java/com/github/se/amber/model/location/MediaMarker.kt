// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.location

import com.github.se.amber.model.media.MediaItem
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * A pin on the memories map: one spot, and the media that happened there.
 *
 * Built from the media by [toMediaMarkers], never stored. Media share a spot when their coordinates
 * round to the same [SAME_SPOT_DECIMALS] decimals: a grid of cells about 1 m wide. Media picked
 * from the same saved place or search result have the same coordinates, so they always share one.
 * Two points a few cm apart on both sides of a cell edge still get two pins.
 *
 * @param id identifies the spot: two lists of media give the same id for the same spot, so a
 *   selected pin can be found again after the media change.
 * @param lat latitude of the pin, from its newest media.
 * @param lng longitude of the pin, from its newest media.
 * @param placeName the first place name of its media, newest first; null if none has one.
 * @param media the media at this spot, newest first; never empty.
 */
data class MediaMarker(
    val id: String,
    val lat: Double,
    val lng: Double,
    val placeName: String?,
    val media: List<MediaItem>,
)

/** Decimals the coordinates are rounded to, to tell whether two media share a pin. */
private const val SAME_SPOT_DECIMALS = 5

private val SAME_SPOT_SCALE = 10.0.pow(SAME_SPOT_DECIMALS)

/**
 * The pins for these media, in the order of their newest media. Media without a real point (no
 * location, a place known only by its name, or coordinates out of range) are left out, so they get
 * no pin.
 *
 * Keeps the order of the media in each pin, so media given newest first stay newest first.
 */
fun List<MediaItem>.toMediaMarkers(): List<MediaMarker> = mapNotNull { media ->
  media.spot()?.let { spot -> spot to media }
}
    .groupBy { (spot, _) -> spot.id }
    .map { (id, located) ->
      val newest = located.first().first
      MediaMarker(
          id = id,
          lat = newest.lat,
          lng = newest.lng,
          placeName =
              located.firstNotNullOfOrNull { (_, media) ->
                media.location?.placeName?.takeIf { it.isNotBlank() }
              },
          media = located.map { it.second },
      )
    }

/** A media's point on the map, and the id of its spot. */
private data class Spot(val id: String, val lat: Double, val lng: Double)

private fun MediaItem.spot(): Spot? {
  val (lat, lng) = location?.pointOrNull() ?: return null
  return Spot(id = "${lat.toSpotKey()}_${lng.toSpotKey()}", lat = lat, lng = lng)
}

// The coordinate as a whole number of 1/10^SAME_SPOT_DECIMALS degrees: no locale, no floating-point
// rounding issues
private fun Double.toSpotKey(): Long = (this * SAME_SPOT_SCALE).roundToLong()
