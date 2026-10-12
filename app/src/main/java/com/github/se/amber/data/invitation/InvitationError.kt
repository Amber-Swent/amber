// This file was written by Neha Chakraborty and Copilot
package com.github.se.amber.data.invitation

/** Typed failures returned by [InvitationRedemptionRepository.redeemInvitation]. */
sealed class InvitationError(message: String) : Exception(message) {
  data object InvalidCode : InvitationError("The invitation code is invalid")

  data object InvitationNotFound : InvitationError("The invitation was not found")

  data object InvitationExpired : InvitationError("The invitation has expired")

  data object InvitationAlreadyUsed : InvitationError("The invitation has already been used")

  data object UserAlreadyBelongsToCircle : InvitationError("You already belong to this care circle")

  data object MissingOrInvalidUserProfile :
      InvitationError("Your user profile is missing or invalid")

  data object InvalidOrUnavailableCareCircle :
      InvitationError("The invitation's care circle is unavailable")

  data object PatientSlotAlreadyOccupied : InvitationError("This care circle already has a patient")

  data object Network : InvitationError("Unable to redeem the invitation while offline")

  data class Unknown(val source: Throwable) : InvitationError("Unable to redeem the invitation")
}
