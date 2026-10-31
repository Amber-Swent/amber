// Written by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.account

/** Permanently deletes the signed-in user's account. */
interface AccountDeletionRepository {

  /**
   * Asks the backend to delete the current user's account and personal data. Once it confirms,
   * everything kept on this device is cleared, the user is signed out and the app restarts on its
   * first screen, so this doesn't return: tell the user what will happen before calling it. If the
   * backend fails, nothing changes on the device and the user stays signed in, so they can try
   * again: deletion is safe to repeat.
   *
   * @return An [AccountDeletionError]; success can only be seen with a fake [LocalAccountData].
   */
  suspend fun deleteAccount(): Result<Unit>

  /**
   * Clears this device again, for an account that is already gone: after
   * [AccountDeletionError.LocalDataNotCleared], or if the deletion was interrupted before the
   * device was cleared, which shows as
   * [com.github.se.amber.model.authentication.ReauthenticationError.AccountNotFound].
   *
   * Like [deleteAccount], this restarts the app once the device is cleared.
   *
   * @return [AccountDeletionError.LocalDataNotCleared]; success can only be seen with a fake
   *   [LocalAccountData].
   */
  suspend fun clearLocalData(): Result<Unit>

  /**
   * Finishes a deletion the app couldn't: it may have died during the call to the backend, or
   * before the device was cleared. If one was started and the account is gone, the device is
   * cleared and the app restarts. If the account still exists, or that can't be told offline,
   * nothing happens and a later launch checks again: the backend may still be working on it.
   */
  suspend fun finishInterruptedDeletion()
}
