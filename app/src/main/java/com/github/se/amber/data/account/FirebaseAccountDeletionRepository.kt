// Written by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.account

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Firebase Functions-backed implementation of [AccountDeletionRepository].
 *
 * @param localData What is cleared from the device once the backend confirms the deletion.
 * @param marker Set before calling the backend, as the app can die before hearing its answer.
 * @param functions The Firebase Functions instance.
 * @param auth The Firebase Authentication instance.
 */
class FirebaseAccountDeletionRepository(
    private val localData: LocalAccountData,
    private val marker: DeletionMarker,
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : AccountDeletionRepository {

  override suspend fun deleteAccount(): Result<Unit> {
    marker.set()
    val deletion = requestDeletion()
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
        localData.clear()
        Result.success(Unit)
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Result.failure(AccountDeletionError.LocalDataNotCleared(exception))
      }

  override suspend fun finishInterruptedDeletion() {
    if (marker.isSet && accountIsGone()) clearLocalData()
  }

  /** Nobody signed in counts: the wipe signs out, so it got that far and has nothing to protect. */
  private suspend fun accountIsGone(): Boolean {
    val user = auth.currentUser ?: return true
    return try {
      user.reload().await()
      false
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      (exception as? FirebaseAuthException)?.errorCode == ERROR_USER_NOT_FOUND
    }
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
        FirebaseFunctionsException.Code.PERMISSION_DENIED -> AccountDeletionError.PermissionDenied
        FirebaseFunctionsException.Code.FAILED_PRECONDITION -> toPreconditionError()
        else -> AccountDeletionError.Unknown(this)
      }

  /** Maps the backend's machine-readable `details.reason`; the message is for logs only. */
  private fun FirebaseFunctionsException.toPreconditionError(): AccountDeletionError =
      when ((details as? Map<*, *>)?.get(REASON_KEY)) {
        "RECENT_SIGN_IN_REQUIRED" -> AccountDeletionError.ReauthenticationRequired
        else -> AccountDeletionError.Unknown(this)
      }

  private companion object {
    const val DELETE_ACCOUNT_FUNCTION = "deleteAccount"
    const val DELETED_KEY = "deleted"
    const val REASON_KEY = "reason"
    const val ERROR_USER_NOT_FOUND = "ERROR_USER_NOT_FOUND"
    // Above the function's own 540 s limit, not the SDK's default of 70 s: a large circle would
    // otherwise look like a failure while the backend is still deleting it
    const val CALL_TIMEOUT_SECONDS = 550L
  }
}
