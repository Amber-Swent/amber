// Written by Neha Chakraborty and Copilot
// Modified by Alaabrine, with assistance from Cursor (Grok).

package com.github.se.amber.data.onboarding

import com.github.se.amber.model.circle.CareCircle

/** Creates a care circle for the signed-in caregiver. */
fun interface OnboardingRepository {

  /**
   * Creates a care circle with a nonblank [name].
   *
   * The implementation verifies that the signed-in user has a caregiver profile, then atomically
   * writes the new circle and adds its ID to that profile with arrayUnion, including when the
   * profile already lists other circles.
   */
  suspend fun createInitialCareCircle(name: String): Result<CareCircle>
}

/** Typed failures returned by [OnboardingRepository.createInitialCareCircle]. */
sealed class OnboardingError(message: String) : Exception(message) {
  data object InvalidCircleName : OnboardingError("A care circle name is required")

  data object NotAuthenticated : OnboardingError("No user is signed in")

  data object ProfileMissing :
      OnboardingError("A user profile is required before creating a care circle")

  data object InvalidProfile : OnboardingError("The user profile is invalid")

  data object NotCaregiver : OnboardingError("Only caregivers can create a care circle")

  data object CareCircleAlreadyExists :
      OnboardingError("The caregiver already belongs to a care circle")

  data object PermissionDenied :
      OnboardingError("You do not have permission to create a care circle")

  data object Network : OnboardingError("Unable to create a care circle while offline")

  data class Unknown(val source: Throwable) : OnboardingError("Unable to create a care circle")
}
