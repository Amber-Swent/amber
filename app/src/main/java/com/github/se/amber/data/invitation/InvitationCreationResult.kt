// This file was written by Neha Chakraborty and Copilot
package com.github.se.amber.data.invitation

/** Result of creating a universal invitation code. */
sealed interface InvitationCreationResult {
  /** The exact code that was successfully persisted at `invitations/{code}`. */
  data class Created(val code: String) : InvitationCreationResult

  /** The internally calculated expiration is invalid and no write was attempted. */
  data object InvalidExpiry : InvitationCreationResult

  /** No Firebase user is signed in. */
  data object NotAuthenticated : InvitationCreationResult

  /** Firestore rejected invitation creation for the current user. */
  data object PermissionDenied : InvitationCreationResult

  /** Firestore could not be reached before the request deadline. */
  data object NetworkFailure : InvitationCreationResult

  /** Every generated code collided with an existing invitation document. */
  data object CollisionRetryLimitExceeded : InvitationCreationResult

  /** Firestore failed for a reason that is not represented by a more specific result. */
  data class FirestoreFailure(val source: Throwable) : InvitationCreationResult
}
