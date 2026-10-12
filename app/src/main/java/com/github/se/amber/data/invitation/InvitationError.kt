// This file was written by Neha Chakraborty and Copilot, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.invitation

/**
 * Typed failures returned by [InvitationRedemptionRepository.redeemInvitation].
 *
 * Messages are developer-facing diagnostics. The UI must map each subtype to a string resource
 * instead of displaying [message].
 */
sealed class InvitationError(message: String) : Exception(message) {
  data object InvalidCode : InvitationError("Invalid invitation code")

  data object InvitationNotFound : InvitationError("Invitation not found")

  data object InvitationExpired : InvitationError("Invitation expired")

  data object InvitationAlreadyUsed : InvitationError("Invitation already used")

  data object UserAlreadyBelongsToCircle : InvitationError("User already belongs to circle")

  data object MissingOrInvalidUserProfile : InvitationError("User profile missing or invalid")

  data object InvalidOrUnavailableCareCircle : InvitationError("Care circle unavailable")

  data object PatientSlotAlreadyOccupied : InvitationError("Patient slot already occupied")

  data object Network : InvitationError("Network unavailable")

  data class Unknown(val source: Throwable) : InvitationError("Unknown redemption failure")
}
