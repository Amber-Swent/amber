// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
// The fake ID token follows the SwEnt bootcamp's end-to-end test utilities.
package com.github.se.amber.e2e

import android.content.Context
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import org.json.JSONObject

/**
 * A Credential Manager whose Google account picker always answers with the account last given to
 * [switchTo], like a phone with that one Google account. Everything else goes to the real one.
 *
 * Its ID tokens are unsigned, which only the Auth emulator accepts.
 */
class FakeGoogleAccount(context: Context) : CredentialManager by CredentialManager.create(context) {

  @Volatile private var email = ""
  @Volatile private var idToken = ""

  /** Makes the next Google sign-in return this account; the same email is the same user. */
  fun switchTo(email: String, name: String) {
    this.email = email
    idToken = unsignedIdToken(email, name)
  }

  override suspend fun getCredential(
      context: Context,
      request: GetCredentialRequest,
  ): GetCredentialResponse {
    check(idToken.isNotEmpty()) { "Call switchTo before signing in" }
    return GetCredentialResponse(
        GoogleIdTokenCredential.Builder().setId(email).setIdToken(idToken).build()
    )
  }

  private fun unsignedIdToken(email: String, name: String): String {
    val header = JSONObject(mapOf("alg" to "none", "typ" to "JWT"))
    // sub is the Google account id: deriving it from the email keeps the uid across sign-ins
    val payload = JSONObject(mapOf("sub" to email, "email" to email, "name" to name))
    return "${base64Url(header)}.${base64Url(payload)}.unsigned"
  }

  private fun base64Url(json: JSONObject): String =
      Base64.encodeToString(
          json.toString().toByteArray(),
          Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
      )
}
