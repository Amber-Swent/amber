// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.directions

import com.github.se.amber.model.directions.Maneuver
import com.github.se.amber.model.directions.Route
import com.github.se.amber.model.directions.RoutePoint
import com.github.se.amber.model.directions.RouteStep
import com.github.se.amber.model.location.TestPlaces.JUST_INSIDE_SAME_PLACE_RADIUS
import com.github.se.amber.model.location.TestPlaces.LAUSANNE
import com.github.se.amber.model.location.TestPlaces.METERS_PER_DEGREE_OF_LATITUDE
import com.github.se.amber.model.location.TestPlaces.latitude
import com.github.se.amber.model.location.TestPlaces.longitude
import java.io.File

/**
 * Routes shared by the route tests: a short walk in Lausanne, from Flon to the cathedral. The same
 * route is in [FUNCTION_ANSWER], as the getWalkingRoute function returns it.
 */
object TestRoutes {

  /** Lausanne Flon station, the place the location tests use for Lausanne. */
  val FLON = RoutePoint(lat = LAUSANNE.latitude, lng = LAUSANNE.longitude)

  /** A corner on the way. */
  val CORNER = RoutePoint(lat = 46.5215, lng = 6.6331)

  /** Lausanne cathedral. */
  val CATHEDRAL = RoutePoint(lat = 46.522778, lng = 6.635556)

  /** The two steps from Flon to the cathedral: depart, then turn left at the corner. */
  val FLON_TO_CATHEDRAL =
      Route(
          destination = CATHEDRAL,
          distanceMeters = 520,
          durationSeconds = 410,
          points = listOf(FLON, CORNER, CATHEDRAL),
          steps =
              listOf(
                  RouteStep(Maneuver.DEPART, distanceMeters = 300, start = FLON, end = CORNER),
                  RouteStep(Maneuver.LEFT, distanceMeters = 220, start = CORNER, end = CATHEDRAL),
              ),
      )

  /**
   * The function's answer for [FLON_TO_CATHEDRAL], shared with its own test in functions/, so the
   * function and the app agree on the format. The unit tests run from the app folder.
   */
  val FUNCTION_ANSWER = File("../functions/test/fixtures/walkingRoute.json")

  /** This point moved [meters] north. */
  fun RoutePoint.movedNorth(meters: Double) =
      copy(lat = lat + meters / METERS_PER_DEGREE_OF_LATITUDE)

  /** A point that still counts as the same place as this one. */
  fun RoutePoint.samePlace() = movedNorth(JUST_INSIDE_SAME_PLACE_RADIUS)
}
