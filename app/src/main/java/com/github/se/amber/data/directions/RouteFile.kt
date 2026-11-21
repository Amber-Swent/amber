// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.directions

import com.github.se.amber.model.directions.Maneuver
import com.github.se.amber.model.directions.Route
import com.github.se.amber.model.directions.RoutePoint
import com.github.se.amber.model.directions.RouteStep
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The name of the file in the app's cache that keeps the last route. Sign-out and account deletion
 * delete it (see DeviceLocalPhone): it holds where the patient lives and goes.
 */
const val LAST_ROUTE_FILE_NAME = "last_route.json"

/** Where the last route is kept on the phone. */
interface RouteStore {

  /** Keeps [route], replacing the one kept before. */
  suspend fun save(route: Route)

  /** The route kept, or null if there is none or it can't be read. */
  suspend fun load(): Route?
}

/**
 * Keeps the last route as JSON in [file], in the app's cache: losing it only means no route
 * offline. Written to a temporary file first, so an app killed mid-write never leaves half a route.
 */
class RouteFile(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RouteStore {

  override suspend fun save(route: Route): Unit =
      withContext(ioDispatcher) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(route.toJson().toString())
        if (!temporary.renameTo(file)) {
          val leftover = if (temporary.delete()) "" else ", and $temporary was left behind"
          error("Could not save the route in $file$leftover")
        }
      }

  override suspend fun load(): Route? =
      withContext(ioDispatcher) {
        if (!file.exists()) return@withContext null
        try {
          JSONObject(file.readText()).toRoute()
        } catch (_: JSONException) {
          // An older or broken file: as if none was kept
          null
        } catch (_: IOException) {
          null
        }
      }

  private fun Route.toJson() =
      JSONObject()
          .put(DESTINATION, destination.toJson())
          .put(DISTANCE, distanceMeters)
          .put(DURATION, durationSeconds)
          .put(POINTS, JSONArray(points.map { it.toJson() }))
          .put(
              STEPS,
              JSONArray(
                  steps.map { step ->
                    JSONObject()
                        .put(MANEUVER, step.maneuver.name)
                        .put(DISTANCE, step.distanceMeters)
                        .put(START, step.start.toJson())
                        .put(END, step.end.toJson())
                  }
              ),
          )

  private fun RoutePoint.toJson() = JSONObject().put(LAT, lat).put(LNG, lng)

  private fun JSONObject.toRoute() =
      Route(
          destination = getJSONObject(DESTINATION).toPoint(),
          distanceMeters = getInt(DISTANCE),
          durationSeconds = getInt(DURATION),
          points = getJSONArray(POINTS).objects().map { it.toPoint() },
          steps =
              getJSONArray(STEPS).objects().map { step ->
                RouteStep(
                    maneuver =
                        Maneuver.entries.find { it.name == step.getString(MANEUVER) }
                            ?: Maneuver.UNKNOWN,
                    distanceMeters = step.getInt(DISTANCE),
                    start = step.getJSONObject(START).toPoint(),
                    end = step.getJSONObject(END).toPoint(),
                )
              },
      )

  private fun JSONObject.toPoint() = RoutePoint(getDouble(LAT), getDouble(LNG))

  private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

  private companion object {
    const val DESTINATION = "destination"
    const val DISTANCE = "distanceMeters"
    const val DURATION = "durationSeconds"
    const val POINTS = "points"
    const val STEPS = "steps"
    const val MANEUVER = "maneuver"
    const val START = "start"
    const val END = "end"
    const val LAT = "lat"
    const val LNG = "lng"
  }
}
