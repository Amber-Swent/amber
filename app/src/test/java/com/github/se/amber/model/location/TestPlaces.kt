// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.location

import kotlin.math.nextDown
import kotlin.math.nextUp

/**
 * Places shared by the memories map tests, and moves measured in cells of the grid media share pins
 * in ([SAME_SPOT_CELL_DEGREES]), so the tests read in cells instead of raw degrees.
 */
object TestPlaces {

  /** Lausanne Flon station. */
  val LAUSANNE = GeoLocation(lat = 46.520856, lng = 6.630127, placeName = "Lausanne")

  /** Geneva Cornavin station. */
  val GENEVA = GeoLocation(lat = 46.210208, lng = 6.142438, placeName = "Geneva")

  /** The latitude of this place; it must have one. */
  val GeoLocation.latitude: Double
    get() = checkNotNull(lat) { "$this has no latitude" }

  /** The longitude of this place; it must have one. */
  val GeoLocation.longitude: Double
    get() = checkNotNull(lng) { "$this has no longitude" }

  /** The center of the grid cell this place is in, so moves from it stay away from cell edges. */
  fun GeoLocation.cellCenter(): GeoLocation =
      copy(
          lat = Math.round(latitude / SAME_SPOT_CELL_DEGREES) * SAME_SPOT_CELL_DEGREES,
          lng = Math.round(longitude / SAME_SPOT_CELL_DEGREES) * SAME_SPOT_CELL_DEGREES,
      )

  /** This place moved [cells] grid cells north (south if negative). */
  fun GeoLocation.movedNorthByCells(cells: Double): GeoLocation =
      copy(lat = latitude + cells * SAME_SPOT_CELL_DEGREES)

  /** The smallest latitude above [MAX_LATITUDE], just out of range. */
  val JUST_ABOVE_MAX_LATITUDE = MAX_LATITUDE.nextUp()

  /** The largest longitude below −[MAX_LONGITUDE], just out of range. */
  val JUST_BELOW_MIN_LONGITUDE = (-MAX_LONGITUDE).nextDown()
}
