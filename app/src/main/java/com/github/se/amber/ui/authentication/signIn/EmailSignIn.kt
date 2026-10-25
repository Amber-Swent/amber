/* This file was written with the aid of AI.
 * Modified by nehachak1, with assistance from
 * Claude Code
 */
package com.github.se.amber.ui.authentication.signIn

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_REGULAR
import com.github.se.amber.ui.theme.WEIGHT_ONE

/** Test tags of [EmailSignInScreen] and of its private helpers. */
internal object EmailSignInTestTags {
  const val HEADING = "email_sign_in_heading"
  const val EMAIL_FIELD = "email_sign_in_email_field"
  const val CONTINUE_BUTTON = "email_sign_in_continue_button"
  const val GOOGLE_BUTTON = "email_sign_in_google_button"
  const val OR_DIVIDER = "email_sign_in_or_divider"
}

/**
 * Displays the initial authentication screen where the user can enter an email address or
 * authenticate using Google.
 *
 * Authentication state is provided by [EmailSignInViewModel]. Navigation is handled outside this
 * composable through callbacks.
 *
 * @param modifier Modifier applied to the root authentication layout.
 * @param context Android context used for Google authentication and to display error toasts.
 * @param credentialManager Credential Manager used for Google authentication.
 * @param onContinue Called when the user presses Continue with a nonblank email address.
 * @param onGoogleSignIn Called once a user is authenticated, either after a successful Google
 *   sign-in or because a user was already signed in when the screen opened.
 * @param emailSignInViewModel ViewModel holding the state of the screen.
 */
@Composable
fun EmailSignInScreen(
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current,
    credentialManager: CredentialManager = remember(context) { CredentialManager.create(context) },
    onContinue: () -> Unit = {},
    onGoogleSignIn: () -> Unit = {},
    emailSignInViewModel: EmailSignInViewModel = viewModel(),
) {
  val uiState by emailSignInViewModel.uiState.collectAsStateWithLifecycle()

  // Always call the latest callback, even if the caller recomposes with a new lambda
  val currentOnGoogleSignIn by rememberUpdatedState(onGoogleSignIn)

  // Navigate away as soon as a user is authenticated
  LaunchedEffect(uiState.user) {
    if (uiState.user != null) {
      currentOnGoogleSignIn()
    }
  }

  // Display a Toast when the errorMessage of the uiState is not null
  LaunchedEffect(uiState.errorMessage) {
    uiState.errorMessage?.let { message ->
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
      emailSignInViewModel.clearError()
    }
  }

  // Parameter spacing spaces the layout content (heading, email field, action group)
  AuthenticationLayout(modifier = modifier, spacing = CAREGIVER_PADDING_REGULAR) {
    AuthenticationText(
        modifier = Modifier.testTag(EmailSignInTestTags.HEADING),
        text = "Enter your email to sign in with Amber",
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleMedium,
    )
    AuthenticationTextField(
        value = uiState.email,
        onValueChange = emailSignInViewModel::onEmailChange,
        placeholder = "email@domain.com",
        modifier = Modifier.testTag(EmailSignInTestTags.EMAIL_FIELD),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
            ),
    )

    // Action group
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_MEDIUM),
    ) {
      // Email sign-in, the alternative to Google sign-in
      AuthenticationButton(
          text = "Continue",
          onClick = { if (emailSignInViewModel.validateEmail()) onContinue() },
          modifier = Modifier.testTag(EmailSignInTestTags.CONTINUE_BUTTON),
      )
      OrDivider()
      AuthenticationButton(
          text = if (uiState.isLoading) "Signing in…" else "Sign in with Google",
          onClick = { emailSignInViewModel.signInWithGoogle(context, credentialManager) },
          modifier = Modifier.testTag(EmailSignInTestTags.GOOGLE_BUTTON),
          enabled = !uiState.isLoading,
          leadingIcon = painterResource(R.drawable.google_logo),
      )
    }
  }
}

/**
 * Displays a horizontal separator with the word "or" between two divider lines.
 *
 * Used to visually separate email authentication from Google authentication.
 *
 * @param modifier Modifier applied to the divider row.
 */
@Composable
private fun OrDivider(
    modifier: Modifier = Modifier,
) {
  Row(
      modifier = modifier.fillMaxWidth().testTag(EmailSignInTestTags.OR_DIVIDER),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
  ) {
    HorizontalDivider(modifier = Modifier.weight(WEIGHT_ONE))
    AuthenticationText(
        modifier = Modifier.padding(horizontal = CAREGIVER_PADDING_MEDIUM),
        text = "or",
        style = MaterialTheme.typography.bodySmall,
    )
    HorizontalDivider(modifier = Modifier.weight(WEIGHT_ONE))
  }
}
