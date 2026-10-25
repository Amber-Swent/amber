// This file was written by Neha Chakraborty and Claude Code
// Modified with assistance from OpenAI Codex

package com.github.se.amber.data.circle

import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.user.Person

/** Reads care circles the signed-in user belongs to and adds people without accounts. */
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

  data class Unknown(val source: Throwable) : CareCircleError("The care circle operation failed")
}
