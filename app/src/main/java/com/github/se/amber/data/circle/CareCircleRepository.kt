// This file was written by Neha Chakraborty and Claude Code
package com.github.se.amber.data.circle

import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.user.Person

/** Reads care circles the signed-in user belongs to. */
interface CareCircleRepository {

  /**
   * Loads the care circle stored at careCircles/[circleId].
   *
   * The returned [Result] fails only with [CareCircleError]. Security rules only let members read a
   * circle, and they also deny reads of a circle that does not exist, so a missing circle usually
   * surfaces as `PermissionDenied` rather than `NotFound`. Cancellation is rethrown.
   */
  suspend fun getCircle(circleId: String): Result<CareCircle>

  suspend fun addPerson(circleId: String, person: Person): Result<Person>
}

/** Typed failures returned by [CareCircleRepository.getCircle]. */
sealed class CareCircleError(message: String) : Exception(message) {
  data object InvalidCircleId : CareCircleError("A valid care circle ID is required")

  data object NotFound : CareCircleError("The care circle does not exist")

  data object NotAuthenticated : CareCircleError("No user is signed in")

  data object PermissionDenied :
      CareCircleError("You do not have permission to view this care circle")

  data object Network : CareCircleError("Unable to load the care circle while offline")

  data object InvalidPersonName: CareCircleError("A first name is required")

  data class Unknown(val source: Throwable) : CareCircleError("Unable to load the care circle")
}
