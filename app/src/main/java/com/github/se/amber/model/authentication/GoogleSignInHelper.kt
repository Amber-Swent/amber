// This file was written with the aid of AI
package com.github.se.amber.model.authentication

import android.os.Bundle
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider

/**
 * Converts Google ID-token credential data into a Firebase [AuthCredential].
 *
 * Accepts the credential's [Bundle]. Conversion errors propagate to the caller.
 */
interface GoogleSignInHelper {
  // Extract from credential Bundle the Google ID token
  fun getAuthCredential(data: Bundle): AuthCredential
}

/**
 * Default implementation of GoogleSignInHelper. Extracts a Google ID token from credential data and
 * creates a Firebase credential.
 *
 * This conversion does not sign in the user; [AuthRepositoryFirebase] performs authentication.
 * Malformed credential data causes conversion to throw.
 */
class GoogleSignInHandler : GoogleSignInHelper {
  override fun getAuthCredential(data: Bundle): AuthCredential {
    val token = GoogleIdTokenCredential.createFrom(data).idToken // Extract ID token
    return GoogleAuthProvider.getCredential(
        token,
        null,
    ) // Builds Firebase Credential
  }
}
