// This file was written by Neha Chakraborty and Copilot
package com.github.se.amber.data.profile

/** Typed failures returned by [ProfileRepository.createProfile]. */
sealed class ProfileCreationError(message: String) : Exception(message) {
  data object InvalidPersonDetails : ProfileCreationError("A first name is required")

  data object NotAuthenticated : ProfileCreationError("No user is signed in")

  data object ProfileAlreadyExists : ProfileCreationError("A profile already exists for this user")

  data object PermissionDenied :
      ProfileCreationError("You do not have permission to create a profile")

  data object Network : ProfileCreationError("Unable to create a profile while offline")

  data class Unknown(val source: Throwable) : ProfileCreationError("Unable to create a profile")
}
