// Written by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.authentication

/**
 * Typed failures returned by [AuthRepository.reauthenticateWithGoogle], so that ViewModels can tell
 * them apart without importing Firebase.
 *
 * Messages are developer-facing diagnostics. The UI must map each subtype to a string resource
 * instead of displaying [message].
 */
sealed class ReauthenticationError(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
  /** The Google account chosen isn't the signed-in one: try again with the right one. */
  data object WrongAccount : ReauthenticationError("Another account than the signed-in one")

  /** The account no longer exists, for example after an interrupted account deletion. */
  data object AccountNotFound : ReauthenticationError("The account no longer exists")

  data object Network : ReauthenticationError("Network unavailable")

  data class Unknown(val source: Throwable) :
      ReauthenticationError("Unknown re-authentication failure", source)
}
