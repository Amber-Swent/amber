// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.account

import com.github.se.amber.model.authentication.AuthRepository
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Firebase Functions-backed implementation of [AccountDeletionRepository].
 *
 * @param localData What is cleared from the device once the backend confirms the deletion.
 * @param marker Set before calling the backend, as the app can die before hearing its answer. If it
 *   can't be saved, the backend isn't called. Removed again if the backend refuses to start.
 * @param authRepository Tells whether the account is gone, for an interrupted deletion.
 * @param functions The Firebase Functions instance.
 */
class FirebaseAccountDeletionRepository(
    private val localData: LocalAccountData,
    private val marker: DeletionMarker,
    private val authRepository: AuthRepository,
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
) : AccountDeletionRepository {
  // The launch check and the role loading screen can both find the account gone at once: two
  // clears would stop Firestore under each other, and a failed one ends the app without reopening.
  // Both share the instance of AccountDeletionRepositoryProvider.default, which this lock relies on
  private val clearing = Mutex()

  override suspend fun deleteAccount(): Result<Unit> {
    try {
      marker.set()
    } catch (exception: IOException) {
      // Without the marker, an app killed mid-deletion would never finish clearing the device
      return Result.failure(AccountDeletionError.Unknown(exception))
    }
    val deletion = requestDeletion()
    if (deletion.exceptionOrNull() == AccountDeletionError.ReauthenticationRequired) {
      // The backend refuses before writing anything: the account is untouched, so a signed-out
      // launch must not take the marker for an interrupted deletion and clear the device. If
      // removing it fails, that launch clears a device the user can sign in to again.
      runCatching { marker.remove() }
    }
    return if (deletion.isSuccess) clearLocalData() else deletion
  }

  private suspend fun requestDeletion(): Result<Unit> =
      try {
        val callable = functions.getHttpsCallable(DELETE_ACCOUNT_FUNCTION)
        callable.setTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val response = callable.call().await().data as? Map<*, *>
        if (response?.get(DELETED_KEY) == true) Result.success(Unit) else malformedResponse()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: FirebaseFunctionsException) {
        Result.failure(exception.toAccountDeletionError())
      } catch (exception: Exception) {
        Result.failure(AccountDeletionError.Unknown(exception))
      }

  override suspend fun clearLocalData(): Result<Unit> =
      try {
        clearing.withLock { localData.clear() }
        Result.success(Unit)
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Result.failure(AccountDeletionError.LocalDataNotCleared(exception))
      }

  override suspend fun clearGoneAccount(): Result<Unit> {
    // Without the marker, a failed clear would never run again: the user is already signed out, so
    // the next launch has no gone account to find. If it can't be saved, clearing still goes ahead
    runCatching { marker.set() }
    return clearLocalData()
  }

  override suspend fun finishInterruptedDeletion() {
    // Null also when nobody is signed in: the wipe signs out, so it got that far and has nothing to
    // protect
    if (marker.isSet && authRepository.refreshCurrentUser() == null) clearLocalData()
  }

  private fun malformedResponse(): Result<Unit> =
      Result.failure(
          AccountDeletionError.Unknown(
              IllegalStateException("deleteAccount returned an invalid response"),
          ),
      )

  private fun FirebaseFunctionsException.toAccountDeletionError(): AccountDeletionError =
      when (code) {
        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> AccountDeletionError.Network
        // The SDK reports a request that couldn't be sent as INTERNAL, with the IOException as
        // cause
        FirebaseFunctionsException.Code.INTERNAL ->
            if (cause is IOException) AccountDeletionError.Network
            else AccountDeletionError.Unknown(this)
        FirebaseFunctionsException.Code.UNAUTHENTICATED ->
            AccountDeletionError.ReauthenticationRequired
        FirebaseFunctionsException.Code.FAILED_PRECONDITION -> toPreconditionError()
        else -> AccountDeletionError.Unknown(this)
      }

  /** Maps the backend's machine-readable `details.reason`; the message is for logs only. */
  private fun FirebaseFunctionsException.toPreconditionError(): AccountDeletionError =
      if ((details as? Map<*, *>)?.get(REASON_KEY) == RECENT_SIGN_IN_REQUIRED) {
        AccountDeletionError.ReauthenticationRequired
      } else {
        AccountDeletionError.Unknown(this)
      }

  private companion object {
    const val DELETE_ACCOUNT_FUNCTION = "deleteAccount"
    const val DELETED_KEY = "deleted"
    const val REASON_KEY = "reason"
    const val RECENT_SIGN_IN_REQUIRED = "RECENT_SIGN_IN_REQUIRED"
    // Above the function's own 540 s limit, not the SDK's default of 70 s: a large circle would
    // otherwise look like a failure while the backend is still deleting it
    const val CALL_TIMEOUT_SECONDS = 550L
  }
}
