// This file was written by Neha Chakraborty and Copilot
package com.github.se.amber.data.profile

import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role

/** Creates the authenticated user's initial profile during first-time onboarding. */
interface ProfileRepository {

  /**
   * Atomically creates the current authenticated user's initial profile with [person] and [role].
   *
   * The returned [Result] fails only with [ProfileCreationError]: `InvalidPersonDetails` when
   * [person] has no valid name, `NotAuthenticated` when no user is authenticated,
   * `ProfileAlreadyExists` when the user already has a profile, `PermissionDenied` when Firestore
   * rejects the write, `Network` for unavailable or timed-out Firestore operations, and `Unknown`
   * for other failures. Cancellation is rethrown.
   *
   * The implementation derives both the profile UID and `person.id` from the current authenticated
   * user and rejects an attempt to overwrite an existing profile.
   */
  suspend fun createProfile(person: Person, role: Role): Result<Unit>
}
