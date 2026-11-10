// Written by Alaabrine, with assistance from Cursor.
// Modified by Alaabrine, with assistance from Cursor (Grok).
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.authentication.signOut

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.circle.ActiveCircleRepositoryProvider
import com.github.se.amber.R
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "SignOutViewModel"

/**
 * UI state for the shared sign-out button and [SignOutScreen].
 *
 * @param isSigningOut Whether a sign-out request is currently in progress.
 * @param signedOut Whether the user has successfully signed out.
 * @param errorMsgRes String resource of the error to display, or null when there is no error.
 */
data class SignOutUIState(
    val isSigningOut: Boolean = false,
    val signedOut: Boolean = false,
    @param:StringRes val errorMsgRes: Int? = null,
)

/**
 * ViewModel that owns the shared sign-out action used by [SignOutButton] (and therefore by
 * [SignOutScreen] and onboarding flows that reuse the helper).
 *
 * @param authRepository Repository used to end the authenticated session.
 * @param activeCircleRepository Repository whose stored circle is cleared once the session ends.
 */
class SignOutViewModel(
    private val authRepository: AuthRepository = AuthRepositoryProvider.repository,
    private val activeCircleRepository: ActiveCircleRepository =
        ActiveCircleRepositoryProvider.default.repository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(SignOutUIState())
  val uiState: StateFlow<SignOutUIState> = _uiState.asStateFlow()

  /** Clears the error message from the UI state. */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMsgRes = null) }
  }

  /**
   * Sets an error message in the UI state.
   *
   * @param errorMsgRes String resource of the error to display in the UI.
   */
  internal fun setErrorMsg(@StringRes errorMsgRes: Int) {
    _uiState.update { it.copy(errorMsgRes = errorMsgRes) }
  }

  /**
   * Sets the signed-out flag in the UI state.
   *
   * @param signedOut New value of [SignOutUIState.signedOut].
   */
  internal fun setSignedOut(signedOut: Boolean) {
    _uiState.update { it.copy(signedOut = signedOut) }
  }

  /** Initiates sign-out and updates the UI state on success or failure. */
  fun signOut() {
    if (_uiState.value.isSigningOut) return
    // Captured first: after a successful sign-out the auth repository no longer has this uid.
    val uid = authRepository.currentUser?.uid
    _uiState.update { it.copy(isSigningOut = true) }
    viewModelScope.launch {
      authRepository
          .signOut()
          .fold(
              onSuccess = {
                clearActiveCircle(uid)
                _uiState.update { it.copy(signedOut = true, isSigningOut = false) }
              },
              onFailure = { error ->
                Log.e(TAG, "Failed to sign out", error)
                if (authRepository.currentUser == null) {
                  // Firebase is already signed out: treat it as a successful sign-out.
                  clearActiveCircle(uid)
                  _uiState.update { it.copy(signedOut = true, isSigningOut = false) }
                } else {
                  _uiState.update { it.copy(isSigningOut = false) }
                  setErrorMsg(R.string.signOutError)
                }
              },
          )
    }
  }

  /**
   * Drops the device's active circle for [uid]. The session is already over, so a storage failure
   * is logged and sign-out still finishes.
   */
  private suspend fun clearActiveCircle(uid: String?) {
    if (uid.isNullOrBlank()) return
    try {
      activeCircleRepository.clear(uid)
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      Log.e(TAG, "Failed to clear the active circle", exception)
    }
  }
}
