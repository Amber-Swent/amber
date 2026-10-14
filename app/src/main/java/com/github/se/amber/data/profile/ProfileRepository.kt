// This file was written by Neha Chakraborty and Copilot
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.profile

import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile

/** Creates and reads user profiles stored at users/{uid}. */
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

  /**
   * Reads the profile of the current authenticated user.
   *
   * Succeeds with `null` when the user has no profile yet (first launch, go to onboarding). Fails
   * with [ProfileReadError.NotAuthenticated] when no user is signed in, otherwise like
   * [getProfile].
   */
  suspend fun getCurrentProfile(): Result<UserProfile?>

  /**
   * Reads the profile stored at users/{[uid]}.
   *
   * Succeeds with `null` when no such profile exists. The returned [Result] fails only with
   * [ProfileReadError]: `InvalidProfile` when the stored document has no role, `PermissionDenied`
   * when Firestore rejects the read (e.g. the caller shares no circle with [uid]),
   * `NotAuthenticated` when Firestore reports no signed-in user, `Network` for unavailable or
   * timed-out Firestore operations, and `Unknown` for other failures. Cancellation is rethrown.
   */
  suspend fun getProfile(uid: String): Result<UserProfile?>
}
