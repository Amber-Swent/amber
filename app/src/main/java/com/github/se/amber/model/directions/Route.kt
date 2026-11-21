// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.directions

/**
 * A point of a route, in degrees. Unlike GeoLocation, both coordinates are always there. The
 * getWalkingRoute function refuses a point out of range.
 */
data class RoutePoint(val lat: Double, val lng: Double)

/**
 * What the patient does at the start of a step. Fewer kinds than the Routes API has: a patient on
 * foot needs to hear "turn left", not whether it is a ramp or a fork.
 */
enum class Maneuver {
  DEPART,
  STRAIGHT,
  SLIGHT_LEFT,
  LEFT,
  SHARP_LEFT,
  SLIGHT_RIGHT,
  RIGHT,
  SHARP_RIGHT,
  U_TURN,
  ROUNDABOUT,
  /** Not one the app can say, such as a ferry, or none given. */
  UNKNOWN;

  companion object {
    /**
     * The maneuver for the Routes API's [name] (e.g. "TURN_LEFT"): forks and ramps count as slight
     * turns, a merge or a new street name as going straight. UNKNOWN for anything else.
     */
    fun fromRoutesApi(name: String?): Maneuver =
        when (name) {
          "DEPART" -> DEPART
          "STRAIGHT",
          "MERGE",
          "NAME_CHANGE" -> STRAIGHT
          "TURN_SLIGHT_LEFT",
          "FORK_LEFT",
          "RAMP_LEFT" -> SLIGHT_LEFT
          "TURN_LEFT" -> LEFT
          "TURN_SHARP_LEFT" -> SHARP_LEFT
          "TURN_SLIGHT_RIGHT",
          "FORK_RIGHT",
          "RAMP_RIGHT" -> SLIGHT_RIGHT
          "TURN_RIGHT" -> RIGHT
          "TURN_SHARP_RIGHT" -> SHARP_RIGHT
          "UTURN_LEFT",
          "UTURN_RIGHT" -> U_TURN
          "ROUNDABOUT_LEFT",
          "ROUNDABOUT_RIGHT" -> ROUNDABOUT
          else -> UNKNOWN
        }
  }
}

/**
 * One step of a route: do [maneuver] at [start], then walk [distanceMeters] to [end], where the
 * next step starts.
 */
data class RouteStep(
    val maneuver: Maneuver,
    val distanceMeters: Int,
    val start: RoutePoint,
    val end: RoutePoint,
)

/**
 * A walking route, as the getWalkingRoute function gives it. Kept on the phone (see
 * RouteRepository) so guidance works offline; never stored online.
 *
 * @param destination where the patient asked to go. The route may end a few meters away, on the
 *   nearest path.
 * @param distanceMeters the whole walk.
 * @param durationSeconds how long the walk takes, by Google's estimate.
 * @param points the line to draw on the map, from start to end.
 * @param steps what to do along it, in order.
 */
data class Route(
    val destination: RoutePoint,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val points: List<RoutePoint>,
    val steps: List<RouteStep>,
)
