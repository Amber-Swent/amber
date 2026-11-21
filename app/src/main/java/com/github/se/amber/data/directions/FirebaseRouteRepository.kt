// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.directions

import android.util.Log
import com.github.se.amber.model.directions.Maneuver
import com.github.se.amber.model.directions.Route
import com.github.se.amber.model.directions.RoutePoint
import com.github.se.amber.model.directions.RouteStep
import com.github.se.amber.model.location.SAME_PLACE_RADIUS_METERS
import com.github.se.amber.model.location.distanceMeters
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * How long the app waits for a route. A patient is waiting at the door: past this, the kept route
 * or an error is better than waiting. The function gives up on the Routes API sooner
 * (ROUTES_TIMEOUT_MS), so it can still answer "unavailable" in time.
 */
internal const val CALL_TIMEOUT_SECONDS = 20L

/**
 * [RouteRepository] backed by the getWalkingRoute Cloud Function, which holds the Routes API key.
 * Only the two points are sent; the function doesn't store them.
 *
 * @param store keeps the last route, for offline.
 * @param functions the Firebase Functions instance.
 */
class FirebaseRouteRepository(
    private val store: RouteStore,
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
) : RouteRepository {

  override suspend fun walkingRoute(from: RoutePoint, to: RoutePoint): Result<Route> {
    val fetched = fetch(from, to)
    fetched.onSuccess { route ->
      try {
        store.save(route)
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        // The route still works now, just not offline later
        Log.w(TAG, "Could not keep the route on the phone", exception)
      }
    }
    if (fetched.exceptionOrNull() != RouteError.Network) return fetched
    val kept = store.load()?.takeIf { it.destination.isNear(to) }
    return kept?.let { Result.success(it) } ?: fetched
  }

  private suspend fun fetch(from: RoutePoint, to: RoutePoint): Result<Route> =
      try {
        val callable = functions.getHttpsCallable(FUNCTION)
        callable.setTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val data = mapOf(ORIGIN to from.toMap(), DESTINATION to to.toMap())
        val response = callable.call(data).await().data as? Map<*, *>
        response?.toRoute(to)?.let { Result.success(it) } ?: malformedResponse()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: FirebaseFunctionsException) {
        Result.failure(exception.toRouteError())
      } catch (exception: Exception) {
        Result.failure(RouteError.Unknown(exception))
      }

  private fun malformedResponse(): Result<Route> =
      Result.failure(
          RouteError.Unknown(IllegalStateException("getWalkingRoute returned an invalid route"))
      )

  /** The route of the function's answer, or null if a field is missing or of the wrong type. */
  private fun Map<*, *>.toRoute(destination: RoutePoint): Route? {
    val points = (this[POINTS] as? List<*>)?.map { (it as? Map<*, *>)?.toPoint() ?: return null }
    val steps =
        (this[STEPS] as? List<*>)?.map { item ->
          val step = item as? Map<*, *> ?: return null
          RouteStep(
              maneuver = Maneuver.fromRoutesApi(step[MANEUVER] as? String),
              distanceMeters = (step[DISTANCE] as? Number)?.toInt() ?: return null,
              start = (step[START] as? Map<*, *>)?.toPoint() ?: return null,
              end = (step[END] as? Map<*, *>)?.toPoint() ?: return null,
          )
        }
    return Route(
        destination = destination,
        distanceMeters = (this[DISTANCE] as? Number)?.toInt() ?: return null,
        durationSeconds = (this[DURATION] as? Number)?.toInt() ?: return null,
        points = points?.takeIf { it.isNotEmpty() } ?: return null,
        steps = steps ?: return null,
    )
  }

  private fun Map<*, *>.toPoint(): RoutePoint? {
    val lat = (this[LAT] as? Number)?.toDouble() ?: return null
    val lng = (this[LNG] as? Number)?.toDouble() ?: return null
    return RoutePoint(lat, lng)
  }

  private fun RoutePoint.toMap() = mapOf(LAT to lat, LNG to lng)

  private fun RoutePoint.isNear(other: RoutePoint) =
      distanceMeters(lat, lng, other.lat, other.lng) <= SAME_PLACE_RADIUS_METERS

  private fun FirebaseFunctionsException.toRouteError(): RouteError =
      when (code) {
        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> RouteError.Network
        // The SDK reports a request that couldn't be sent as INTERNAL, with the IOException as
        // cause
        FirebaseFunctionsException.Code.INTERNAL ->
            if (cause is IOException) RouteError.Network else RouteError.Unknown(this)
        FirebaseFunctionsException.Code.UNAUTHENTICATED -> RouteError.NotAuthenticated
        FirebaseFunctionsException.Code.NOT_FOUND ->
            if ((details as? Map<*, *>)?.get(REASON) == NO_ROUTE) RouteError.NoRoute
            else RouteError.Unknown(this)
        else -> RouteError.Unknown(this)
      }

  private companion object {
    const val TAG = "FirebaseRouteRepository"
    const val FUNCTION = "getWalkingRoute"
    const val ORIGIN = "origin"
    const val DESTINATION = "destination"
    const val POINTS = "points"
    const val STEPS = "steps"
    const val MANEUVER = "maneuver"
    const val DISTANCE = "distanceMeters"
    const val DURATION = "durationSeconds"
    const val START = "start"
    const val END = "end"
    const val LAT = "lat"
    const val LNG = "lng"
    const val REASON = "reason"
    const val NO_ROUTE = "NO_ROUTE"
  }
}
