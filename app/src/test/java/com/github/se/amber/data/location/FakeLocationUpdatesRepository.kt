// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * A [LocationUpdatesRepository] whose permission and updates the test sets, like the real one:
 * - [hasPermission] returns [permission];
 * - [isLocationEnabled] is false while the current [updates] is [LocationUpdate.LocationOff];
 * - each collection first gets the current [updates] ([LocationUpdate.Searching] at first), then
 *   each new one;
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
      merge(updates, failure.filterNotNull().map<Throwable, LocationUpdate> { throw it }).collect {
        emit(it)
      }
    } finally {
      activeCollectors--
    }
  }
}
