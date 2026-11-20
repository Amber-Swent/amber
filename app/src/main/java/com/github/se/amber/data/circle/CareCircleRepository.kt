// Written by Neha Chakraborty and Claude Code
// Modified by MeiliV, with assistance from OpenAI Codex

package com.github.se.amber.data.circle

import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.location.Place
import com.github.se.amber.model.user.Person
import kotlinx.coroutines.flow.Flow

/**
 * Reads care circles the signed-in user belongs to, adds people without accounts, and keeps the
 * circle's saved places.
 */
interface CareCircleRepository {

  /**
   * Generates a Firestore ID locally without creating a document. Not a suspending function ince
   * generating a document ID is local and has no await().
   */
  fun newId(): String

  /**
   * Loads the care circle stored at careCircles/[circleId].
   *
   * The returned [Result] fails only with [CareCircleError]. Security rules only let members read a
   * circle, and they also deny reads of a circle that does not exist, so a missing circle usually
   * surfaces as `PermissionDenied` rather than `NotFound`. Cancellation is rethrown.
   */
  suspend fun getCircle(circleId: String): Result<CareCircle>

  /**
   * Adds a person without an account to the care circle's [CareCircle.people] map.
   *
   * Requires a nonblank first name. Generates the person's ID, trims the first name, last name, and
   * nickname, and stores a blank nickname as null. This does not add the person to
   * [CareCircle.memberIds] or create a user account.
   *
   * On a valid request, this function awaits Firestore's confirmation of the write before returning
   * success. If the device is offline, Firestore may queue the write locally and this function
   * remains suspended until the server confirms or rejects it after reconnection.
   *
   * A failed [Result] contains a [CareCircleError]. Cancellation is rethrown.
   */
  suspend fun addPerson(circleId: String, person: Person): Result<Person>

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
   * Unlike [addPerson], it doesn't wait for the server: it returns once the place is saved on this
   * phone, which sends it when it is online. A caregiver saving the place they stand in may have no
   * signal there. Only caregivers may write places; the security rules refuse anyone else, and the
   * phone then removes the place again.
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

/** Typed failures returned by [CareCircleRepository]. */
sealed class CareCircleError(message: String) : Exception(message) {
  data object InvalidCircleId : CareCircleError("A valid care circle ID is required")

  data object NotFound : CareCircleError("The care circle does not exist")

  data object NotAuthenticated : CareCircleError("No user is signed in")

  data object PermissionDenied :
      CareCircleError("You do not have permission to access this care circle")

  data object Network : CareCircleError("Unable to reach the care circle")

  data object InvalidPersonName : CareCircleError("A first name is required")

  data object InvalidPlace : CareCircleError("A place needs a name and a point on the map")

  data object InvalidPlaceId : CareCircleError("A valid place ID is required")

  data class Unknown(val source: Throwable) : CareCircleError("The care circle operation failed")
}
