// This file was written with the aid of AI
package com.github.se.amber.model.authentication

import androidx.credentials.Credential
import com.google.firebase.auth.FirebaseUser

/**
 * Identifies an authenticated user within the app.
 *
 * @property uid The user's unique Firebase Authentication identifier.
 */
data class AuthUser(val uid: String)

/**
 * Provides the current authenticated user and Google sign-in and sign-out operations.
 *
 * [currentUser] is read on demand; it does not emit authentication state changes. Operations return
 * failures through [Result] and propagate coroutine cancellation.
 */
interface AuthRepository {
  val currentUser: AuthUser? // If null: no current signed-in user

  /**
   * Signs in to Firebase using a Google ID-token credential returned by Credential Manager. Is a
   * suspend function so that the app can wait for authentication without blocking the main thread.
   *
   * @param credential The Google credential to use for authentication.
   * @return The authenticated Firebase user on success, or a failure if the credential is
   *   unsupported, cannot be parsed, or authentication fails.
   * @throws kotlinx.coroutines.CancellationException If cancellation occurs during provider
   *   cleanup. Cancellation is rethrown rather than returned as a failure.
   */
  suspend fun signInWithGoogle(credential: Credential): Result<FirebaseUser>

  /**
   * Ends the Firebase session, then clears credential provider state.
   *
   * These operations are sequential. If Firebase sign-out throws, provider cleanup is not
   * attempted. If cleanup fails or is cancelled after Firebase sign-out, the Firebase session
   * remains signed out; it is not restored.
   *
   * @return `Result.success(Unit)` when both operations complete successfully. Returns
   *   `Result.failure(exception)` if either operation throws an exception other than cancellation.
   *   A failure does not necessarily mean the user is still signed in; callers should read
   *   [currentUser] to determine the current authentication state.
   * @throws kotlinx.coroutines.CancellationException If cancellation occurs during provider
   *   cleanup. Cancellation is rethrown rather than returned as a failure.
   */
  suspend fun signOut(): Result<Unit>
}
