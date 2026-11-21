// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.circle

import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.location.Place
import com.github.se.amber.model.location.pointOrNull
import kotlinx.coroutines.flow.Flow

/**
 * Keeps the saved places of a care circle (see [CareCircle.places]). Failures are
 * [CareCircleError]s, as for the rest of the circle.
 */
interface SavedPlacesRepository {

  /**
   * Watches the saved places of the circle [circleId] (see [CareCircle.places]), in no particular
   * order. Emits them at once, then again on every change, until the collector stops. A place saved
   * on this phone shows up at once, even offline.
   *
   * Offline, the places come from what this phone has already loaded. The flow ends with a
   * [CareCircleError]: [CareCircleError.InvalidCircleId] right away for an invalid id, and
   * [CareCircleError.PermissionDenied] when the user can't read the circle, for example after
   * leaving it.
   */
  fun observePlaces(circleId: String): Flow<List<Place>>

  /**
   * Saves a new place in the circle [circleId] and returns it as saved: with a new id, and its name
   * trimmed. The id the caller gives is ignored.
   *
   * Unlike [CareCircleRepository.addPerson], it doesn't wait for the server: it returns once the
   * place is saved on this phone, which sends it when it is online. A caregiver saving the place
   * they stand in may have no signal there. Only caregivers may write places; the security rules
   * refuse anyone else, and the phone then removes the place again.
   *
   * Fails right away, without writing anything, with [CareCircleError.InvalidCircleId], or with
   * [CareCircleError.InvalidPlace] when the name is blank or the location has no point on the map.
   * Other failures are [CareCircleError]s too. Cancellation is rethrown.
   */
  suspend fun addPlace(circleId: String, place: Place): Result<Place>

  /**
   * Saves the changes to the existing place [place], for example a new name, and returns it as
   * saved, its name trimmed. Saved like [addPlace], with the same failures, and
   * [CareCircleError.InvalidPlaceId] when [Place.id] is not a valid id.
   */
  suspend fun updatePlace(circleId: String, place: Place): Result<Place>

  /**
   * Removes the place [placeId] from the circle [circleId]. Appointments keep their own copy of its
   * location, so they don't change. Saved like [addPlace]. Fails right away with
   * [CareCircleError.InvalidCircleId] or [CareCircleError.InvalidPlaceId]. Cancellation is
   * rethrown.
   */
  suspend fun deletePlace(circleId: String, placeId: String): Result<Unit>
}

/**
 * Whether [SavedPlacesRepository] saves this place: it has a name to show and a real point the
 * patient can be walked to (see [pointOrNull]). The security rules check the same.
 */
fun Place.isValidToSave(): Boolean = name.isNotBlank() && location.pointOrNull() != null
