// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeoLocationTest {

  @Test
  fun `a place with both coordinates in range is a point, up to the limits`() {
    assertEquals(46.5197 to 6.6323, GeoLocation(lat = 46.5197, lng = 6.6323).pointOrNull())
    assertEquals(90.0 to 180.0, GeoLocation(lat = 90.0, lng = 180.0).pointOrNull())
    assertEquals(-90.0 to -180.0, GeoLocation(lat = -90.0, lng = -180.0).pointOrNull())
  }

  @Test
  fun `a missing, out of range or non-number coordinate is no point`() {
    val invalid =
        listOf(
            GeoLocation(placeName = "Grandma's house"),
            GeoLocation(lat = 46.5),
            GeoLocation(lng = 6.6),
            GeoLocation(lat = 90.000001, lng = 6.6),
            GeoLocation(lat = -90.000001, lng = 6.6),
            GeoLocation(lat = 46.5, lng = 180.000001),
            GeoLocation(lat = 46.5, lng = -180.000001),
            GeoLocation(lat = Double.NaN, lng = 6.6),
            GeoLocation(lat = 46.5, lng = Double.NaN),
            GeoLocation(lat = Double.POSITIVE_INFINITY, lng = 6.6),
            GeoLocation(lat = 46.5, lng = Double.NEGATIVE_INFINITY),
        )

    invalid.forEach { assertNull("$it should be no point", it.pointOrNull()) }
  }
}
