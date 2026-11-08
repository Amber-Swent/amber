// Written by MeiliV, with assistance from OpenAI Codex
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.authentication.signIn

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_BIG
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_REGULAR

/** Test tags of [EmailSignInScreen] and of its private helpers. */
internal object EmailSignInTestTags {
  const val WELCOME = "EmailSignIn_test_tag_welcome"
  const val INSTRUCTION = "EmailSignIn_test_tag_instruction"
  const val HEADING = "email_sign_in_heading"
  const val EMAIL_FIELD = "email_sign_in_email_field"
  const val CONTINUE_BUTTON = "email_sign_in_continue_button"
  const val GOOGLE_BUTTON = "email_sign_in_google_button"
  const val OR_DIVIDER = "email_sign_in_or_divider"
}

internal const val WELCOME_LABEL = "Welcome to Amber"
internal const val INSTRUCTION_LABEL = "Sign in with Google to access your account or create one."

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
 * @param onContinue Called when the user presses Continue; it is only enabled for a nonblank email.
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

  EmailSignInContent(
      modifier = modifier,
      uiState = uiState,
      onEmailChange = emailSignInViewModel::onEmailChange,
      // The ViewModel check stays as a guard behind the disabled button
      onContinue = { if (emailSignInViewModel.validateEmail()) onContinue() },
      onGoogleSignIn = { emailSignInViewModel.signInWithGoogle(context, credentialManager) },
  )
}

/**
 * Stateless content of [EmailSignInScreen], rendered from [uiState] only.
 *
 * Only Google sign-in is shown until email sign-in is implemented. Continue will then stay disabled
 * while the email is blank, so the user can see why it does nothing.
 *
 * @param modifier Modifier applied to the root authentication layout.
 * @param uiState State to display.
 * @param onEmailChange Called whenever the email field value changes.
 * @param onContinue Called when the user presses Continue.
 * @param onGoogleSignIn Called when the user presses the Google sign-in button.
 */
@Suppress("UNUSED_PARAMETER") // used by the email sign-in commented out below
@Composable
internal fun EmailSignInContent(
    modifier: Modifier = Modifier,
    uiState: EmailSignInUIState = EmailSignInUIState(),
    onEmailChange: (String) -> Unit = {},
    onContinue: () -> Unit = {},
    onGoogleSignIn: () -> Unit = {},
) {
  // Parameter spacing spaces the layout content (heading, email field, action group)
  AuthenticationLayout(modifier = modifier, spacing = CAREGIVER_PADDING_REGULAR) {
    // Email sign-in isn't implemented yet: put this back with it, and OrDivider below
    // AuthenticationText(
    //     modifier = Modifier.testTag(EmailSignInTestTags.HEADING),
    //     text = "Enter your email to sign in with Amber",
    //     textAlign = TextAlign.Center,
    //     style = MaterialTheme.typography.titleMedium,
    // )
    // AuthenticationTextField(
    //     value = uiState.email,
    //     onValueChange = onEmailChange,
    //     placeholder = "email@domain.com",
    //     modifier = Modifier.testTag(EmailSignInTestTags.EMAIL_FIELD),
    //     keyboardOptions =
    //         KeyboardOptions(
    //             keyboardType = KeyboardType.Email,
    //             imeAction = ImeAction.Done,
    //         ),
    // )
    //
    // // Action group
    // Column(
    //     modifier = Modifier.fillMaxWidth(),
    //     verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_MEDIUM),
    // ) {
    //   // Email sign-in, the alternative to Google sign-in
    //   AuthenticationButton(
    //       text = "Continue",
    //       onClick = onContinue,
    //       modifier = Modifier.testTag(EmailSignInTestTags.CONTINUE_BUTTON),
    //       enabled = uiState.email.isNotBlank(),
    //   )
    //   OrDivider()
    // Close the action group's Column after the Google button below
    AuthenticationText(
        modifier = Modifier.testTag(EmailSignInTestTags.WELCOME),
        text = WELCOME_LABEL,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    AuthenticationText(
        modifier =
            Modifier.padding(bottom = CAREGIVER_PADDING_BIG)
                .testTag(EmailSignInTestTags.INSTRUCTION),
        text = INSTRUCTION_LABEL,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.secondary,
    )
    AuthenticationButton(
        text = if (uiState.isLoading) "Signing in…" else "Sign in with Google",
        onClick = onGoogleSignIn,
        modifier = Modifier.testTag(EmailSignInTestTags.GOOGLE_BUTTON),
        enabled = !uiState.isLoading,
        leadingIcon = painterResource(R.drawable.google_logo),
    )
  }
}

// /**
//  * Displays a horizontal separator with the word "or" between two divider lines.
//  *
//  * Used to visually separate email authentication from Google authentication.
//  *
//  * @param modifier Modifier applied to the divider row.
//  */
// @Composable
// private fun OrDivider(
//     modifier: Modifier = Modifier,
// ) {
//   Row(
//       modifier = modifier.fillMaxWidth().testTag(EmailSignInTestTags.OR_DIVIDER),
//       verticalAlignment = Alignment.CenterVertically,
//       horizontalArrangement = Arrangement.Center,
//   ) {
//     HorizontalDivider(modifier = Modifier.weight(WEIGHT_ONE))
//     AuthenticationText(
//         modifier = Modifier.padding(horizontal = CAREGIVER_PADDING_MEDIUM),
//         text = "or",
//         style = MaterialTheme.typography.bodySmall,
//     )
//     HorizontalDivider(modifier = Modifier.weight(WEIGHT_ONE))
//   }
// }

/** Preview of [EmailSignInContent] with an entered email, using the Amber application theme. */
@Preview(showBackground = true)
@Composable
internal fun EmailSignInContentPreview() {
  AmberAppTheme { EmailSignInContent(uiState = EmailSignInUIState(email = "person@example.com")) }
}
