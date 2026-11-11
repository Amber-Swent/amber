// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

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

  /**
   * Makes the next Google sign-in return this account; the same email is the same user.
   *
   * @param email the account's email address
   * @param name the account's display name
   */
  fun switchTo(email: String, name: String) {
    this.email = email
    idToken = unsignedGoogleIdToken(email, name)
  }

  /**
   * Answers every credential request with the account given to [switchTo], without showing the
   * account picker.
   *
   * @param context the context the request comes from, unused
   * @param request the credential request, unused
   * @return a Google ID token credential for the current account
   */
  override suspend fun getCredential(
      context: Context,
      request: GetCredentialRequest,
  ): GetCredentialResponse {
    check(idToken.isNotEmpty()) { "Call switchTo before signing in" }
    return GetCredentialResponse(
        GoogleIdTokenCredential.Builder().setId(email).setIdToken(idToken).build()
    )
  }
}

/**
 * Builds an unsigned Google ID token for [email], which only the Auth emulator accepts. The same
 * email always gives the same user, so a test can create a user with it before the app signs in.
 *
 * @param email the account's email address
 * @param name the account's display name
 * @return the token, ready for `GoogleAuthProvider.getCredential(token, null)`
 */
internal fun unsignedGoogleIdToken(email: String, name: String): String {
  val header = JSONObject(mapOf("alg" to "none", "typ" to "JWT"))
  // sub is the Google account id: deriving it from the email keeps the uid across sign-ins
  val payload = JSONObject(mapOf("sub" to email, "email" to email, "name" to name))
  return "${base64Url(header)}.${base64Url(payload)}.unsigned"
}

/**
 * Encodes [json] as unpadded base64url, the encoding of the parts of a JWT.
 *
 * @param json the object to encode
 * @return the encoded text
 */
private fun base64Url(json: JSONObject): String =
    Base64.encodeToString(
        json.toString().toByteArray(),
        Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
    )
