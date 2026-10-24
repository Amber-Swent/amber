// This file was written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude
// Code.
package com.github.se.amber.data.location

import com.github.se.amber.model.location.GeoLocation

/**
 * Finds real places from what the user types, so a media's location is always a point on the map
 * and never a free-form name such as "Grandma's house".
 */
interface PlaceSearchRepository {

  /**
   * Real places matching [query], best match first, at most a handful. Each has its coordinates and
   * a readable [GeoLocation.placeName] (e.g. "Lausanne, Switzerland"). A blank [query] finds
   * nothing.
   *
   * The returned [Result] fails if the search can't be done: no connection, or no place search on
   * this device.
   */
  suspend fun search(query: String): Result<List<GeoLocation>>
}
