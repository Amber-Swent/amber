// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.location

/** Where a destination comes from, so the screen can show a fitting icon. */
enum class DestinationKind {
  SAVED_PLACE,
  MEMORY,
  SEARCH,
}

/**
 * A place the patient can be walked to, as "Go somewhere" lists it. Built on the phone, never
 * stored.
 *
 * @param id the id of what it comes from: the saved place or the memories' pin. Unique for its
 *   [kind] only; a search result has an empty id.
 * @param name what the button says: the saved place's name, or the place name of the memories or
 *   the search result.
 * @param lat latitude of the point the patient is walked to.
 * @param lng longitude of that point.
 */
data class Destination(
    val id: String,
    val kind: DestinationKind,
    val name: String,
    val lat: Double,
    val lng: Double,
)

/**
 * The most places from memories in the list: the patient finds many choices hard, and the saved
 * places come first anyway.
 */
const val MAX_MEMORY_DESTINATIONS = 5

/**
 * The destinations to offer, in this order: the saved [places] by name, then up to
 * [MAX_MEMORY_DESTINATIONS] places from [memories], newest first.
 *
 * Left out: whatever has no real point (see [GeoLocation.pointOrNull]), memories without a place
 * name, and memories within [SAME_PLACE_RADIUS_METERS] of a saved place, so the same pharmacy isn't
 * offered twice.
 *
 * @param memories the pins of the memories map, newest first, as MediaLocationRepository gives
 *   them.
 */
fun destinations(places: List<Place>, memories: List<MediaMarker>): List<Destination> {
  val saved =
      places
          .sortedBy { it.name.lowercase() }
          .mapNotNull { it.location.toDestination(it.id, DestinationKind.SAVED_PLACE, it.name) }
  val fromMemories =
      memories
          .filter { pin -> !pin.placeName.isNullOrBlank() && saved.none { it.isNear(pin) } }
          .take(MAX_MEMORY_DESTINATIONS)
          .map { pin ->
            Destination(pin.id, DestinationKind.MEMORY, pin.placeName.orEmpty(), pin.lat, pin.lng)
          }
  return saved + fromMemories
}

/** A place found by the address search, or null if it has no real point or no name to show. */
fun GeoLocation.toSearchDestination(): Destination? =
    placeName?.takeIf { it.isNotBlank() }?.let { toDestination("", DestinationKind.SEARCH, it) }

private fun GeoLocation.toDestination(
    id: String,
    kind: DestinationKind,
    name: String,
): Destination? = pointOrNull()?.let { (lat, lng) -> Destination(id, kind, name, lat, lng) }

private fun Destination.isNear(pin: MediaMarker): Boolean =
    distanceMeters(lat, lng, pin.lat, pin.lng) <= SAME_PLACE_RADIUS_METERS
