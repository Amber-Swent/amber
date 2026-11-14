// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.location

import com.github.se.amber.model.location.MediaMarker
import kotlinx.coroutines.flow.Flow

/**
 * The pins of the memories map: where the media of a care circle happened. Shared by the patient's
 * and the caregiver's map.
 */
fun interface MediaLocationRepository {

  /**
   * The pins of [circleId] (see [com.github.se.amber.model.location.toMediaMarkers]), in the order
   * of their newest media. Emits the pins at once, then again whenever they change. Offline, it
   * gives the pins of the media this phone has already loaded, and catches up once back online.
   *
   * If the media can't be read (e.g. permission denied after the user left the circle), the flow
   * stops with that error: handle it in the ViewModel (e.g. with `catch`).
   */
  fun observeMarkers(circleId: String): Flow<List<MediaMarker>>
}
