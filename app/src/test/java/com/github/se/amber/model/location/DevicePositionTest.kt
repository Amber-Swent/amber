// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.location

import com.github.se.amber.model.location.TestPositions.APPROXIMATE_ACCURACY_METERS
import com.github.se.amber.model.location.TestPositions.GPS_ACCURACY_METERS
import com.github.se.amber.model.location.TestPositions.LAUSANNE_FLON
import kotlin.math.nextUp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePositionTest {

  private val limit = NEARBY_MAX_ACCURACY_METERS

  private fun withAccuracy(meters: Float?) = LAUSANNE_FLON.copy(accuracyMeters = meters)

  @Test
  fun aPositionIsPreciseWithinItsAccuracyOrMoreNotWithAnUnknownOrWorseOne() {
    assertTrue(withAccuracy(GPS_ACCURACY_METERS).isPreciseWithin(limit))
    assertTrue(withAccuracy(limit.toFloat()).isPreciseWithin(limit))

    assertFalse(withAccuracy(limit.toFloat().nextUp()).isPreciseWithin(limit))
    assertFalse(withAccuracy(APPROXIMATE_ACCURACY_METERS).isPreciseWithin(limit))
    assertFalse(withAccuracy(null).isPreciseWithin(limit))
  }
}
