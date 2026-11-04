// Written by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.account

/**
 * Typed failures returned by [AccountDeletionRepository.deleteAccount].
 *
 * Messages are developer-facing diagnostics. The UI must map each subtype to a string resource
 * instead of displaying [message].
 */
sealed class AccountDeletionError(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
  data object Network : AccountDeletionError("Network unavailable")

  /**
   * The sign-in is more than 5 minutes old, or nobody is signed in: re-authenticate, then try
   * again. The backend refuses before deleting anything.
   */
  data object ReauthenticationRequired : AccountDeletionError("A recent sign-in is required")

  data class Unknown(val source: Throwable) :
      AccountDeletionError("Unknown account deletion failure", source)

  /**
   * The account is deleted, but something stayed on this device. Unlike the other failures, the
   * account can't be used any more: retry with [AccountDeletionRepository.clearLocalData], not with
   * a new deletion.
   */
  data class LocalDataNotCleared(val source: Throwable) :
      AccountDeletionError("The account is deleted but the device isn't cleared", source)
}
