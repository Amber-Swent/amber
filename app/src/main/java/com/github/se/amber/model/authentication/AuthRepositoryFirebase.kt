// This file was written with the aid of AI
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.authentication

import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Implements [AuthRepository] using Firebase Authentication and Credential Manager.
 *
 * Validates Google ID-token credentials, extract the token, wraps it in a Firebase credential and
 * awaits authentication. Sign-out ends the Firebase session before clearing credential provider
 * state; a cleanup failure does not restore the Firebase session.
 *
 * @param credentialManager The Android Credential Manager instance.
 * @param googleSignInHelper The Google sign-in credential converter.
 * @param auth The Firebase Authentication instance.
 */
class AuthRepositoryFirebase(
    private val credentialManager: CredentialManager,
    private val googleSignInHelper: GoogleSignInHelper = GoogleSignInHandler(),
    private val auth: FirebaseAuth = Firebase.auth,
) : AuthRepository {

  override val currentUser: AuthUser?
    // Only continue if auth.currentUser is not null
    // If not null, create an AuthUser containing that user's ID
    get() = auth.currentUser?.let { AuthUser(it.uid) }

  override suspend fun signInWithGoogle(credential: Credential): Result<AuthUser> {
    return try {
      val firebaseCredential = firebaseCredentialOf(credential)

      // Authenticate with Firebase
      val user = checkNotNull(auth.signInWithCredential(firebaseCredential).await().user)
      Result.success(AuthUser(user.uid)) // return AuthUser
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun reauthenticateWithGoogle(credential: Credential): Result<Unit> {
    return try {
      val firebaseCredential = firebaseCredentialOf(credential)
      val user = checkNotNull(auth.currentUser) { "Nobody is signed in" }
      user.reauthenticate(firebaseCredential).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Checks that [credential] is a Google one, and builds the Firebase credential from its token.
   */
  private fun firebaseCredentialOf(credential: Credential): AuthCredential {
    require(
        credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
      "Unsupported sign-in credential"
    }
    return googleSignInHelper.getAuthCredential(credential.data)
  }

  override suspend fun signOut(): Result<Unit> {
    return try {
      auth.signOut()
      credentialManager.clearCredentialState(ClearCredentialStateRequest())
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
