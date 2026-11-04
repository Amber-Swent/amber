// This file was written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude
// Code.
// Modified by Yuhan YANG, with assistance from Cursor.
package com.github.se.amber.data.profile

/** Typed failures returned by [ProfileRepository.createProfile]. */
sealed class ProfileCreationError(message: String) : Exception(message) {
  data object InvalidPersonDetails : ProfileCreationError("A first name is required")

  data object NotAuthenticated : ProfileCreationError("No user is signed in")

  data object ProfileAlreadyExists : ProfileCreationError("A profile already exists for this user")

  data object PermissionDenied :
      ProfileCreationError("You do not have permission to create a profile")

  data object Network :
      ProfileCreationError("Unable to create a profile while offline or the request timed out")

  data class Unknown(val source: Throwable) : ProfileCreationError("Unable to create a profile")
}

/**
 * Typed failures returned by [ProfileRepository.getProfile] and
 * [ProfileRepository.getCurrentProfile].
 */
sealed class ProfileReadError(message: String) : Exception(message) {
  data object NotAuthenticated : ProfileReadError("No user is signed in")

  data object InvalidProfile : ProfileReadError("The stored profile is invalid")

  data object PermissionDenied : ProfileReadError("You do not have permission to read this profile")

  data object Network :
      ProfileReadError("Unable to read the profile while offline or the request timed out")

  data class Unknown(val source: Throwable) : ProfileReadError("Unable to read the profile")
}

/**
 * Typed failures returned by [ProfileRepository.updateOnboardingProfile].
 *
 * [AlreadyInCareCircle] means the account already belongs to a circle, so the role can no longer be
 * changed from onboarding.
 */
sealed class ProfileUpdateError(message: String) : Exception(message) {
  data object InvalidPersonDetails : ProfileUpdateError("A first and last name are required")

  data object NotAuthenticated : ProfileUpdateError("No user is signed in")

  data object NoProfile : ProfileUpdateError("No profile exists for this user")

  data object AlreadyInCareCircle :
      ProfileUpdateError("The profile already belongs to a care circle")

  data object PermissionDenied :
      ProfileUpdateError("You do not have permission to update this profile")

  data object Network :
      ProfileUpdateError("Unable to update the profile while offline or the request timed out")

  data class Unknown(val source: Throwable) : ProfileUpdateError("Unable to update the profile")
}
