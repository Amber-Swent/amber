// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.location

import com.github.se.amber.model.location.MediaMarker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/**
 * A [MediaLocationRepository] whose pins the test sets: [markers] holds the live pins of each
 * circle (a circle missing from it has none), and a circle in [failures] has a flow that stops at
 * once with its error, before any pin.
 */
class FakeMediaLocationRepository(markers: Map<String, List<MediaMarker>> = emptyMap()) :
    MediaLocationRepository {

  val markers = MutableStateFlow(markers)

  val failures = mutableMapOf<String, Throwable>()

  /** The circles whose pins were asked for, in order. */
  val observedCircleIds = mutableListOf<String>()

  override fun observeMarkers(circleId: String): Flow<List<MediaMarker>> {
    observedCircleIds += circleId
    return flow {
      failures[circleId]?.let { throw it }
      markers.collect { emit(it[circleId].orEmpty()) }
    }
  }
}
