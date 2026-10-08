// This file was written with the aid of AI
package com.github.se.amber.model.authentication

import androidx.credentials.CredentialManager
import com.google.firebase.Firebase
import com.google.firebase.app

/**
 * Provides a shared [AuthRepository], initialized lazily on first access.
 *
 * Uses the default Firebase app's application context to create Credential Manager. The default
 * Firebase app must be initialized before [repository] is first accessed.
 */
object AuthRepositoryProvider {
  val repository: AuthRepository by lazy {
    AuthRepositoryFirebase(
        credentialManager = CredentialManager.create(Firebase.app.applicationContext)
    )
  }
}
