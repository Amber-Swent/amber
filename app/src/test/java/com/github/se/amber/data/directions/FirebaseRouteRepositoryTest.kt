// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.directions

import com.github.se.amber.data.directions.TestRoutes.CATHEDRAL
import com.github.se.amber.data.directions.TestRoutes.CORNER
import com.github.se.amber.data.directions.TestRoutes.FLON
import com.github.se.amber.data.directions.TestRoutes.FLON_TO_CATHEDRAL
import com.github.se.amber.data.directions.TestRoutes.FUNCTION_ANSWER
import com.github.se.amber.data.directions.TestRoutes.movedNorth
import com.github.se.amber.data.directions.TestRoutes.samePlace
import com.github.se.amber.model.directions.Route
import com.github.se.amber.model.directions.RoutePoint
import com.github.se.amber.model.location.SAME_PLACE_RADIUS_METERS
import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.FirebaseFunctionsException.Code
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric gives the test Android's org.json, to read the function's answer from its file. */
@RunWith(RobolectricTestRunner::class)
class FirebaseRouteRepositoryTest {

  /** Keeps the route in memory; saving throws [saveFailure] when it is set. */
  private class FakeRouteStore : RouteStore {
    var kept: Route? = null
    var saveFailure: Exception? = null

    override suspend fun save(route: Route) {
      saveFailure?.let { throw it }
      kept = route
    }

    override suspend fun load() = kept
  }

  private val store = FakeRouteStore()
  private val callable = mockk<HttpsCallableReference>(relaxed = true)
  private val sent = slot<Any>()
  private lateinit var repository: FirebaseRouteRepository

  /**
   * The function's answer for [FLON_TO_CATHEDRAL], as Firebase decodes it: maps, lists, numbers.
   */
  @Suppress("UNCHECKED_CAST")
  private val answer = JSONObject(FUNCTION_ANSWER.readText()).decoded() as Map<String, Any?>

  private val firstStep = (answer.getValue("steps") as List<*>).first() as Map<*, *>

  private fun Any?.decoded(): Any? =
      when (this) {
        is JSONObject -> keys().asSequence().associateWith { get(it).decoded() }
        is JSONArray -> (0 until length()).map { get(it).decoded() }
        else -> this
      }

  private fun RoutePoint.toMap() = mapOf("lat" to lat, "lng" to lng)

  @Before
  fun setUp() {
    val functions = mockk<FirebaseFunctions>()
    every { functions.getHttpsCallable("getWalkingRoute") } returns callable
    repository = FirebaseRouteRepository(store, functions)
  }

  private fun respondWith(data: Any?) {
    val result = HttpsCallableResult::class.java.getConstructor(Any::class.java).newInstance(data)
    every { callable.call(capture(sent)) } returns Tasks.forResult(result)
  }

  private fun failWith(exception: Exception) {
    every { callable.call(any()) } returns Tasks.forException(exception)
  }

  private fun functionsException(code: Code, details: Any? = null): FirebaseFunctionsException =
      FirebaseFunctionsException::class
          .java
          .getConstructor(String::class.java, Code::class.java, Any::class.java)
          .newInstance(code.name, code, details)

  /** A request the SDK couldn't send: it wraps the IOException, as it does when offline. */
  private fun offlineException(): FirebaseFunctionsException =
      FirebaseFunctionsException::class
          .java
          .getConstructor(
              String::class.java,
              Code::class.java,
              Any::class.java,
              Throwable::class.java,
          )
          .newInstance("INTERNAL", Code.INTERNAL, null, IOException("Offline"))

  private fun route(to: RoutePoint = CATHEDRAL) = runBlocking { repository.walkingRoute(FLON, to) }

  @Test
  fun sendsOnlyTheTwoPointsAndReadsAndKeepsTheFunctionsRoute() {
    respondWith(answer)

    // The function's own test checks it returns this file: both sides read the format the same way
    assertEquals(FLON_TO_CATHEDRAL, route().getOrThrow())

    assertEquals(mapOf("origin" to FLON.toMap(), "destination" to CATHEDRAL.toMap()), sent.captured)
    verify { callable.setTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
    assertEquals(FLON_TO_CATHEDRAL, store.kept)
  }

  @Test
  fun aRouteThatCannotBeKeptIsStillGiven() {
    respondWith(answer)
    store.saveFailure = IOException("Disk full")

    assertEquals(FLON_TO_CATHEDRAL, route().getOrThrow())
  }

  @Test
  fun aMalformedAnswerIsUnknownAndNotKept() {
    val malformed =
        listOf(
            null,
            "not a map",
            answer - "distanceMeters",
            answer - "durationSeconds",
            answer + ("durationSeconds" to "${FLON_TO_CATHEDRAL.durationSeconds}s"),
            answer + ("points" to emptyList<Any>()),
            answer + ("points" to "not a list"),
            answer + ("points" to listOf("not a point")),
            answer + ("points" to listOf(mapOf("lat" to FLON.lat))),
            answer + ("points" to listOf(mapOf("lng" to FLON.lng))),
            answer - "steps",
            answer + ("steps" to listOf("not a step")),
            answer + ("steps" to listOf(firstStep - "distanceMeters")),
            answer + ("steps" to listOf(firstStep - "start")),
            answer + ("steps" to listOf(firstStep - "end")),
        )

    for (data in malformed) {
      respondWith(data)
      assertTrue("$data", route().exceptionOrNull() is RouteError.Unknown)
    }
    assertEquals(null, store.kept)
  }

  @Test
  fun functionErrorsBecomeRouteErrors() {
    val expected =
        listOf(
            functionsException(Code.NOT_FOUND, mapOf("reason" to "NO_ROUTE")) to RouteError.NoRoute,
            functionsException(Code.UNAVAILABLE) to RouteError.Network,
            functionsException(Code.DEADLINE_EXCEEDED) to RouteError.Network,
            offlineException() to RouteError.Network,
            functionsException(Code.UNAUTHENTICATED) to RouteError.NotAuthenticated,
        )
    for ((exception, error) in expected) {
      failWith(exception)
      assertSame(error, route().exceptionOrNull())
    }

    for (exception in
        listOf(
            functionsException(Code.NOT_FOUND),
            functionsException(Code.INTERNAL),
            functionsException(Code.INVALID_ARGUMENT),
            IllegalStateException("Firebase not initialized"),
        )) {
      failWith(exception)
      assertTrue("$exception", route().exceptionOrNull() is RouteError.Unknown)
    }
  }

  @Test
  fun offlineTheKeptRouteToTheSamePlaceIsGiven() {
    store.kept = FLON_TO_CATHEDRAL
    failWith(functionsException(Code.UNAVAILABLE))

    // Asked again from elsewhere, to the same place: guidance leads the patient to the route
    assertEquals(
        FLON_TO_CATHEDRAL,
        runBlocking { repository.walkingRoute(CORNER, CATHEDRAL.samePlace()) }.getOrThrow(),
    )
    // Another place
    assertSame(
        RouteError.Network,
        route(to = CATHEDRAL.movedNorth(SAME_PLACE_RADIUS_METERS * 2)).exceptionOrNull(),
    )
    // Not when the server says there is no route
    failWith(functionsException(Code.NOT_FOUND, mapOf("reason" to "NO_ROUTE")))
    assertSame(RouteError.NoRoute, route().exceptionOrNull())
  }

  @Test
  fun cancellationIsRethrown() {
    failWith(CancellationException("Screen closed"))
    assertThrows(CancellationException::class.java) { route() }

    // Also while the route is being kept
    respondWith(answer)
    store.saveFailure = CancellationException("Screen closed")
    assertThrows(CancellationException::class.java) { route() }
  }
}
