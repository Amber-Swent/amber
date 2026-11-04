// Written by Alaabrine, with assistance from Cursor.
package com.github.se.amber.ui.authentication.signOut

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MediaRepositoryProvider
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "SignOutViewModel"
private const val SIGN_OUT_ERROR_MSG = "Could not sign out. Please try again."

/**
 * UI state for the shared sign-out button and [SignOutScreen].
 *
 * @param isSigningOut Whether a sign-out request is currently in progress.
 * @param signedOut Whether the user has successfully signed out.
 * @param errorMsg Error message to display, or null when there is no error.
 * @param hasUnsentMedia Whether this phone still has media that has not been sent. Signing out
 *   deletes it.
 */
data class SignOutUIState(
    val isSigningOut: Boolean = false,
    val signedOut: Boolean = false,
    val errorMsg: String? = null,
    val hasUnsentMedia: Boolean = false,
)

/**
 * ViewModel that owns the shared sign-out action used by [SignOutButton] (and therefore by
 * [SignOutScreen] and onboarding flows that reuse the helper).
 *
 * @param authRepository Repository used to end the authenticated session.
 * @param mediaRepository Clears media kept on this phone before the session ends.
 */
class SignOutViewModel(
    private val authRepository: AuthRepository = AuthRepositoryProvider.repository,
    private val mediaRepository: MediaRepository = MediaRepositoryProvider.repository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(SignOutUIState())
  val uiState: StateFlow<SignOutUIState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      mediaRepository.observeHasUnsent().collect { hasUnsent ->
        _uiState.update { it.copy(hasUnsentMedia = hasUnsent) }
      }
    }
  }

  /** Clears the error message from the UI state. */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMsg = null) }
  }

  /**
   * Sets an error message in the UI state.
   *
   * @param errorMsg Error message to display in the UI.
   */
  internal fun setErrorMsg(errorMsg: String) {
    _uiState.update { it.copy(errorMsg = errorMsg) }
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
    _uiState.update { it.copy(isSigningOut = true) }
    viewModelScope.launch {
      try {
        mediaRepository.clearLocalMedia()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to clear local media", e)
        _uiState.update { it.copy(isSigningOut = false) }
        setErrorMsg(SIGN_OUT_ERROR_MSG)
        return@launch
      }
      authRepository
          .signOut()
          .fold(
              onSuccess = { _uiState.update { it.copy(signedOut = true, isSigningOut = false) } },
              onFailure = { error ->
                Log.e(TAG, "Failed to sign out", error)
                if (authRepository.currentUser == null) {
                  // Firebase is already signed out: treat it as a successful sign-out.
                  _uiState.update { it.copy(signedOut = true, isSigningOut = false) }
                } else {
                  _uiState.update { it.copy(isSigningOut = false) }
                  setErrorMsg(SIGN_OUT_ERROR_MSG)
                }
              },
          )
    }
  }
}
