// This file was written by Neha Chakraborty and Copilot
package com.github.se.amber.data.invitation

/** Retrieves invitations previously created by the signed-in caregiver for a care circle. */
interface InvitationLookupRepository {

  /**
   * Returns invitations created by the current caregiver for [selectedCircleId].
   *
   * The result distinguishes shareable active codes from expired and already-used codes.
   */
  suspend fun getInvitations(selectedCircleId: String): Result<List<InvitationSummary>>
}
