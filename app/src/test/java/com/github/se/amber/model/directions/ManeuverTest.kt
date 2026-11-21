// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.directions

import org.junit.Assert.assertEquals
import org.junit.Test

class ManeuverTest {

  @Test
  fun routesApiManeuversBecomeTheFewAPatientHears() {
    val expected =
        mapOf(
            "DEPART" to Maneuver.DEPART,
            "STRAIGHT" to Maneuver.STRAIGHT,
            "NAME_CHANGE" to Maneuver.STRAIGHT,
            "MERGE" to Maneuver.STRAIGHT,
            "TURN_LEFT" to Maneuver.LEFT,
            "TURN_SLIGHT_LEFT" to Maneuver.SLIGHT_LEFT,
            "FORK_LEFT" to Maneuver.SLIGHT_LEFT,
            "RAMP_LEFT" to Maneuver.SLIGHT_LEFT,
            "TURN_SHARP_LEFT" to Maneuver.SHARP_LEFT,
            "TURN_RIGHT" to Maneuver.RIGHT,
            "TURN_SLIGHT_RIGHT" to Maneuver.SLIGHT_RIGHT,
            "FORK_RIGHT" to Maneuver.SLIGHT_RIGHT,
            "RAMP_RIGHT" to Maneuver.SLIGHT_RIGHT,
            "TURN_SHARP_RIGHT" to Maneuver.SHARP_RIGHT,
            "UTURN_LEFT" to Maneuver.U_TURN,
            "UTURN_RIGHT" to Maneuver.U_TURN,
            "ROUNDABOUT_LEFT" to Maneuver.ROUNDABOUT,
            "ROUNDABOUT_RIGHT" to Maneuver.ROUNDABOUT,
            // not something to say on foot, or nothing given
            "FERRY" to Maneuver.UNKNOWN,
            "MANEUVER_UNSPECIFIED" to Maneuver.UNKNOWN,
            null to Maneuver.UNKNOWN,
        )

    assertEquals(expected, expected.keys.associateWith { Maneuver.fromRoutesApi(it) })
  }
}
