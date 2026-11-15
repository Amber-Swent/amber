// Written by Alaabrine, with assistance from Cursor.

package com.github.se.amber.data.session

import com.github.se.amber.data.account.DeviceLocalPhone
import com.github.se.amber.model.authentication.AuthRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * Ends the signed-in session on this device.
 *
 * The phone is cleared first. The session ends only after that, so a failed clear leaves the user
 * signed in and the caller can try again.
 */
fun interface SessionRepository {

  /**
   * Clears local media, decoded pictures and temporary upload files, then signs out.
   *
   * [DeviceLocalPhone] runs every clearing step even when an earlier one fails. If any of them
   * fails, the session stays open and this returns that failure. A failure from
   * [AuthRepository.signOut] can mean the session is already gone; read
   * [AuthRepository.currentUser].
   *
   * @throws CancellationException If the call is cancelled. The session is left as it was when
   *   cancellation happened.
   */
  suspend fun signOut(): Result<Unit>
}

/**
 * Clears the phone through [DeviceLocalPhone], then ends the session.
 *
 * @param phone Clears local media, decoded pictures and temporary upload files.
 * @param authRepository Ends the session once the phone is clear.
 */
class DeviceSessionRepository(
    private val phone: DeviceLocalPhone,
    private val authRepository: AuthRepository,
) : SessionRepository {

  override suspend fun signOut(): Result<Unit> {
    try {
      phone.clear()
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      return Result.failure(exception)
    }
    return authRepository.signOut()
  }
}
