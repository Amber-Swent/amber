// This file was written with the aid of AI
package com.github.se.amber.ui.authentication

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.github.se.amber.model.authentication.AuthUser
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State displayed by [EmailSignInScreen].
 *
 * @param email Email address currently entered by the user.
 * @param isLoading Whether a Google sign-in operation is currently running.
 * @param user Authenticated user after a successful Google sign-in, or null otherwise.
 * @param errorMessage Message describing the latest sign-in error, or null when there is no error.
 */
data class EmailSignInUiState(
    val email: String = "",

    // Whether sign-in is running, becomes false when we replace the state with a new AuthUIState
    val isLoading: Boolean = false,

    // UI's AuthUser or null
    val user: AuthUser? = null,
    val errorMessage: String? = null,
)

/**
 * Owns the UI state and authentication actions of [EmailSignInScreen].
 *
 * The ViewModel stores the entered email and handles Google authentication through
 * [AuthRepository]. It does not perform navigation; navigation remains the responsibility of the
 * caller displaying the screen.
 *
 * @param repository Repository used to access authentication operations.
 */
class EmailSignInViewModel(
    private val repository: AuthRepository = AuthRepositoryProvider.repository,
) : ViewModel() {

  private val _uiState =
      MutableStateFlow(
          EmailSignInUiState(
              user = repository.currentUser,
          )
      )
  val uiState = _uiState.asStateFlow() // Read-only

  /**
   * Updates the email address displayed by the screen.
   *
   * @param email New email value entered by the user.
   */
  fun onEmailChange(email: String) {
    _uiState.update { state ->
      state.copy(email = email) // Change the email, keep the rest of the state unchanged
    }
  }

  /**
   * Starts Google authentication using Android Credential Manager.
   *
   * A successful authentication stores the authenticated user in [uiState]. Cancelling the Google
   * account picker returns the screen to its idle state without displaying an error.
   *
   * @param context Android context used by Credential Manager and to access the OAuth client ID.
   * @param credentialManager Credential Manager used to request the Google credential.
   */
  fun signInWithGoogle(
      context: Context,
      credentialManager: CredentialManager,
  ) {
    if (_uiState.value.isLoading) return

    _uiState.update { state -> state.copy(isLoading = true, errorMessage = null) }

    viewModelScope.launch {
      try {
        // default_web...: web/server OAuth client associated with the Firebase
        // configuration: tells Google which client the sign-in is for
        val googleOption =
            GetSignInWithGoogleOption.Builder(context.getString(R.string.default_web_client_id))
                .build()

        // Wraps the option in a request
        val request = GetCredentialRequest.Builder().addCredentialOption(googleOption).build()

        val credential =
            credentialManager.getCredential(context = context, request = request).credential

        val user =
            repository
                .signInWithGoogle(credential)
                .getOrThrow() // Returns user on success, throws stored exception on failure

        _uiState.update { state ->
          state.copy(
              isLoading = false,
              user = user,
              errorMessage = null,
          )
        }
      } catch (e: GetCredentialCancellationException) {
        _uiState.update { state -> state.copy(isLoading = false) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.update { state ->
          state.copy(
              isLoading = false,
              errorMessage = e.localizedMessage ?: "Sign-in failed. Please try again.",
          )
        }
      }
    }
  }
}
