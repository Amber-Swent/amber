// Written by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.account

/** Permanently deletes the signed-in user's account. */
fun interface AccountDeletionRepository {

  /**
   * Asks the backend to delete the current user's account and personal data. Once it confirms,
   * everything kept on this device is cleared and the user is signed out. If the backend fails,
   * nothing changes on the device and the user stays signed in, so they can try again: deletion is
   * safe to repeat.
   *
   * @return Success once the account is deleted and the device is cleared, or an
   *   [AccountDeletionError].
   */
  suspend fun deleteAccount(): Result<Unit>
}
