// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.location

import com.github.se.amber.model.location.TestPlaces.JUST_ABOVE_MAX_LATITUDE
import com.github.se.amber.model.location.TestPlaces.JUST_BELOW_MIN_LONGITUDE
import com.github.se.amber.model.location.TestPlaces.LAUSANNE
import com.github.se.amber.model.location.TestPlaces.latitude
import com.github.se.amber.model.location.TestPlaces.longitude
import kotlin.math.nextDown
import kotlin.math.nextUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeoLocationTest {

  @Test
  fun `a place with both coordinates in range is a point, up to the limits and on the axes`() {
    assertEquals(LAUSANNE.latitude to LAUSANNE.longitude, LAUSANNE.pointOrNull())
    // Only exactly (0, 0) is left out: the equator and the prime meridian are real places
    assertEquals(0.0 to LAUSANNE.longitude, LAUSANNE.copy(lat = 0.0).pointOrNull())
    assertEquals(LAUSANNE.latitude to 0.0, LAUSANNE.copy(lng = 0.0).pointOrNull())
    assertEquals(
        MAX_LATITUDE to MAX_LONGITUDE,
        GeoLocation(lat = MAX_LATITUDE, lng = MAX_LONGITUDE).pointOrNull(),
    )
    assertEquals(
        -MAX_LATITUDE to -MAX_LONGITUDE,
        GeoLocation(lat = -MAX_LATITUDE, lng = -MAX_LONGITUDE).pointOrNull(),
    )
  }

  @Test
  fun `a missing, out of range or non-number coordinate, or exactly (0, 0), is no point`() {
    val invalid =
        listOf(
            GeoLocation(placeName = "Grandma's house"),
            LAUSANNE.copy(lng = null),
            LAUSANNE.copy(lat = null),
            LAUSANNE.copy(lat = JUST_ABOVE_MAX_LATITUDE),
            LAUSANNE.copy(lat = (-MAX_LATITUDE).nextDown()),
            LAUSANNE.copy(lng = MAX_LONGITUDE.nextUp()),
            LAUSANNE.copy(lng = JUST_BELOW_MIN_LONGITUDE),
            LAUSANNE.copy(lat = Double.NaN),
            LAUSANNE.copy(lng = Double.NaN),
            LAUSANNE.copy(lat = Double.POSITIVE_INFINITY),
            LAUSANNE.copy(lng = Double.NEGATIVE_INFINITY),
            GeoLocation(lat = 0.0, lng = 0.0),
        )

    invalid.forEach { assertNull("$it should be no point", it.pointOrNull()) }
  }
}
