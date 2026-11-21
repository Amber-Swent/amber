// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.directions

import com.github.se.amber.model.directions.Route
import com.github.se.amber.model.directions.RoutePoint

/** Finds walking routes for guidance, and keeps the last one so it still works offline. */
fun interface RouteRepository {

  /**
   * The walking route from [from] to [to].
   *
   * Online, it comes from the server and is kept on the phone, replacing the one kept before. When
   * the server can't be reached, the route kept on the phone is given instead if it goes to [to]
   * (within SAME_PLACE_RADIUS_METERS). It may start elsewhere: guidance leads the patient to it.
   *
   * The returned [Result] fails with a [RouteError]: [RouteError.NoRoute] when there is no walking
   * route, [RouteError.Network] when the server can't be reached and no route to [to] is kept,
   * [RouteError.NotAuthenticated], or [RouteError.Unknown]. Cancellation is rethrown.
   */
  suspend fun walkingRoute(from: RoutePoint, to: RoutePoint): Result<Route>
}

/** Typed failures returned by [RouteRepository]. */
sealed class RouteError(message: String) : Exception(message) {
  data object NoRoute : RouteError("There is no walking route to this place")

  data object Network : RouteError("The route service can't be reached")

  data object NotAuthenticated : RouteError("No user is signed in")

  data class Unknown(val source: Throwable) : RouteError("The route could not be found")
}
