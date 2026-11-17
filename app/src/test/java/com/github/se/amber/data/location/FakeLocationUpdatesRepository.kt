// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * A [LocationUpdatesRepository] whose permission and updates the test sets, like the real one:
 * - [hasPermission] returns [permission];
 * - [isLocationEnabled] is false while the current [updates] is [LocationUpdate.LocationOff];
 * - each collection first gets whether the location is on ([LocationUpdate.Searching] or
 *   [LocationUpdate.LocationOff]), as the real one does each time the GPS starts, then the current
 *   [updates] if it is a position (as a first fix), then each new one;
 * - setting [failure] stops the running collections with it, and the next ones at once, until it is
 *   set back to null;
 * - [isRunning] tells whether the GPS would be running.
 */
class FakeLocationUpdatesRepository(var permission: Boolean = true) : LocationUpdatesRepository {

  val updates = MutableStateFlow<LocationUpdate>(LocationUpdate.Searching)

  val failure = MutableStateFlow<Throwable?>(null)

  private var activeCollectors = 0

  /** Whether a collection is running, so the real repository's GPS would be on. */
  val isRunning: Boolean
    get() = activeCollectors > 0

  override fun hasPermission(): Boolean = permission

  override fun isLocationEnabled(): Boolean = updates.value != LocationUpdate.LocationOff

  override fun observeLocation(): Flow<LocationUpdate> = flow {
    activeCollectors++
    try {
      val setting =
          if (isLocationEnabled()) LocationUpdate.Searching else LocationUpdate.LocationOff
      emit(setting)
      merge(updates, failure.filterNotNull().map<Throwable, LocationUpdate> { throw it })
          .collectIndexed { index, update ->
            // The current update was just sent as the setting
            if (index > 0 || update != setting) emit(update)
          }
    } finally {
      activeCollectors--
    }
  }
}
