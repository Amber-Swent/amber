// Written by MeiliV, with assistance from OpenAI Codex
// Modified by nehachak1, with assistance from Claude Code
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.authentication.signIn

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.github.se.amber.model.authentication.AuthUser
import com.github.se.amber.model.validation.InputValidation
import com.github.se.amber.ui.utils.UiText
import com.github.se.amber.ui.utils.errorText
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "EmailSignInViewModel"

/**
 * State displayed by [EmailSignInScreen].
 *
 * @param email Email address currently entered by the user.
 * @param isLoading Whether a Google sign-in operation is currently running.
 * @param user Authenticated user after a successful Google sign-in, or null otherwise.
 * @param error Message describing the latest sign-in error, or null when there is no error.
 */
data class EmailSignInUIState(
    val email: String = "",
    val isLoading: Boolean = false,
    val user: AuthUser? = null,
    val error: UiText? = null,
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
          EmailSignInUIState(
              user = repository.currentUser,
          )
      )
  val uiState = _uiState.asStateFlow() // Read-only

  /**
   * Updates the email address displayed by the screen, cut at [InputValidation.EMAIL_MAX_LENGTH].
   * Spaces are kept as typed, so that [validateEmail] refuses an address with a space inside.
   *
   * @param email New email value entered by the user.
   */
  fun onEmailChange(email: String) {
    _uiState.update { state ->
      // Change the email, keep the rest of the state unchanged
      state.copy(email = InputValidation.EMAIL.clean(email))
    }
  }

  /**
   * Checks that a well-formed email address was entered before continuing with email sign-in.
   *
   * A blank or malformed email sets [EmailSignInUIState.error] so the screen can report it.
   *
   * @return true if the email is a valid address, false otherwise.
   */
  fun validateEmail(): Boolean {
    val error =
        InputValidation.EMAIL.errorText(
            _uiState.value.email,
            blankRes = R.string.signInErrorEmailRequired,
            formatRes = R.string.inputErrorEmailFormat,
        ) ?: return true
    _uiState.update { it.copy(error = error) }
    return false
  }

  /**
   * Clears the current sign-in error after the screen has shown it to the user.
   *
   * This allows a later sign-in attempt to show the same error again.
   */
  fun clearError() {
    _uiState.update { it.copy(error = null) }
  }

  /**
   * Starts Google authentication using Android Credential Manager.
   *
   * A successful authentication stores the authenticated user in [uiState]. Cancelling the Google
   * account picker returns the screen to its idle state without displaying an error. Signing out
   * will be handled in the ViewModel of the screen that allows sign-out.
   *
   * @param context Android context used by Credential Manager and to access the OAuth client ID.
   * @param credentialManager Credential Manager used to request the Google credential.
   */
  fun signInWithGoogle(
      context: Context,
      credentialManager: CredentialManager,
  ) {
    if (_uiState.value.isLoading) return

    _uiState.update { state -> state.copy(isLoading = true, error = null) }

    /*
       Extract the context.getString outside of viewModelScope.launch
       Context can be destroyed at any moment: if the Activity owning the context is destroyed
       before the coroutine runs, accessing context inside the asynchronous code could fail
       or reference a dead context.
    */
    val clientId = context.getString(R.string.default_web_client_id)

    viewModelScope.launch {
      try {
        // default_web...: web/server OAuth client associated with the Firebase
        // configuration: tells Google which client the sign-in is for
        val googleOption = GetSignInWithGoogleOption.Builder(clientId).build()

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
              error = null,
          )
        }
      } catch (_: GetCredentialCancellationException) {
        _uiState.update { state -> state.copy(isLoading = false) }
      } catch (_: NoCredentialException) { // Handle: no matching credentials found on device
        _uiState.update { state ->
          state.copy(
              isLoading = false,
              error = UiText.Resource(R.string.signInErrorNoGoogleAccount),
          )
        }
      } catch (e: CancellationException) { // Propagate to caller
        throw e
      } catch (e: Exception) {
        // The exception message is for logs only; the user sees a translated message
        Log.e(TAG, "Google sign-in failed", e)
        _uiState.update { state ->
          state.copy(
              isLoading = false,
              error = UiText.Resource(R.string.signInErrorFailed),
          )
        }
      }
    }
  }
}
