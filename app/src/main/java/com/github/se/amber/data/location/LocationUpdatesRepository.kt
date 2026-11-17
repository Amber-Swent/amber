// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.location

import com.github.se.amber.model.location.DevicePosition
import kotlinx.coroutines.flow.Flow

/**
 * The phone's live position, for the memories map. The position stays on the phone: it is never
 * stored nor uploaded.
 */
interface LocationUpdatesRepository {

  /** Whether the user let the app read the location, precise or approximate. */
  fun hasPermission(): Boolean

  /** Whether the location is on in the phone's settings; false without a location service. */
  fun isLocationEnabled(): Boolean

  /**
   * The phone's position while collected, from its GPS, so it works without internet. Emits
   * [LocationUpdate.Searching] or [LocationUpdate.LocationOff] at once, then a
   * [LocationUpdate.Position] at each new position, and the location going off or on again (only
   * when it changes, so a position shown stays while the location stays on).
   *
   * The GPS runs only while the flow is collected: cancelling the collection stops it.
   *
   * Without the permission when the collection starts, the flow stops with a [SecurityException].
   * Taking the permission back later ends the app's process, so it can't happen while collected. It
   * stops with another error if the phone can't give its position at all (e.g. no Google Play
   * services).
   */
  fun observeLocation(): Flow<LocationUpdate>
}

/** What [LocationUpdatesRepository.observeLocation] reports. */
sealed interface LocationUpdate {

  /** The location is turned off in the phone's settings. */
  data object LocationOff : LocationUpdate

  /** The location is on, but the phone hasn't found where it is yet. */
  data object Searching : LocationUpdate

  /** A new position. */
  data class Position(val position: DevicePosition) : LocationUpdate
}
