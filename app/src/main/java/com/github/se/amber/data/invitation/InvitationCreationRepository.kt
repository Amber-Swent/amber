// This file was written by Neha Chakraborty and Copilot
package com.github.se.amber.data.invitation

/** Creates a universal invitation code for the authenticated caregiver's selected care circle. */
fun interface InvitationCreationRepository {

  /**
   * Persists a new invitation for [selectedCircleId] and returns its code.
   *
   * The implementation derives the creator, creation time, and fixed 30-day expiration internally.
   * It never accepts an invited role or expiration from the caller.
   */
  suspend fun createInvitation(selectedCircleId: String): InvitationCreationResult
}
