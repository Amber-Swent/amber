// Written by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.account

/** Permanently deletes the signed-in user's account. */
interface AccountDeletionRepository {

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

  /**
   * Clears this device again, for an account that is already gone: after
   * [AccountDeletionError.LocalDataNotCleared], or if the deletion was interrupted before the
   * device was cleared, which shows as
   * [com.github.se.amber.model.authentication.ReauthenticationError.AccountNotFound].
   *
   * @return Success once the device is cleared, or [AccountDeletionError.LocalDataNotCleared].
   */
  suspend fun clearLocalData(): Result<Unit>
}
