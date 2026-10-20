// This file was written with the aid of AI
package com.github.se.amber.ui.authentication.signIn

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PADDING_BIG
import com.github.se.amber.ui.theme.PADDING_ENORMOUS
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_REGULAR
import com.github.se.amber.ui.theme.SMALL_ROUND_CORNER
import com.github.se.amber.ui.theme.darkGray
import com.github.se.amber.ui.theme.lightGray
import com.github.se.amber.ui.theme.lightOrange

internal object EmailSignInTestTags {
  const val HEADING = "emailSignInHeading"
  const val EMAIL_FIELD = "emailSignInEmailField"
  const val CONTINUE_BUTTON = "emailSignInContinueButton"
  const val GOOGLE_BUTTON = "emailSignInGoogleButton"
  const val SIGN_UP_PROMPT = "emailSignInSignUpPrompt"
  const val SIGN_UP_LINK = "emailSignInSignUpLink"
}

/**
 * Displays the initial authentication screen where the user can enter an email address or
 * authenticate using Google.
 *
 * Authentication state is provided by [EmailSignInViewModel]. Navigation is handled outside this
 * composable through callbacks.
 *
 * @param modifier Modifier applied to the root authentication layout.
 * @param context Android context used for Google authentication.
 * @param credentialManager Credential Manager used for Google authentication.
 * @param emailSignInViewModel ViewModel holding the state of the screen.
 * @param onContinue Called when the user presses Continue with a nonblank email address.
 * @param onGoogleSignIn Called after Google authentication succeeds.
 */
@Composable
fun EmailSignInScreen(
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current,
    credentialManager: CredentialManager = CredentialManager.create(context),
    onContinue: () -> Unit = {},
    onGoogleSignIn: () -> Unit = {},
    emailSignInViewModel: EmailSignInViewModel = viewModel(),
) {
  val uiState by emailSignInViewModel.uiState.collectAsState()

  LaunchedEffect(uiState.user) {
    if (uiState.user != null) {
      onGoogleSignIn()
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
      uiState = uiState,
      onEmailChange = emailSignInViewModel::onEmailChange,
      onContinue = { if (emailSignInViewModel.validateEmail()) onContinue() },
      onGoogleClick = { emailSignInViewModel.signInWithGoogle(context, credentialManager) },
      modifier = modifier,
  )
}

/**
 * Renders the email entry and Google sign-in options without owning authentication state.
 *
 * The caller supplies the current state and handles each user action through callbacks.
 *
 * @param uiState Email value and Google sign-in loading state to display.
 * @param onEmailChange Called when the email field changes.
 * @param onContinue Called when the user presses Continue.
 * @param onGoogleClick Called when the user presses the Google sign-in button.
 * @param modifier Modifier applied to the authentication layout.
 */
@Composable
private fun EmailSignInContent(
    uiState: EmailSignInUiState,
    onEmailChange: (String) -> Unit,
    onContinue: () -> Unit,
    onGoogleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {

  // Parameter spacing spaces the layout content (heading, email field, action group)
  AuthenticationLayout(modifier = modifier, spacing = PADDING_REGULAR) {
    AuthenticationText(
        modifier = Modifier.testTag(EmailSignInTestTags.HEADING),
        text = "Enter your email to sign in with Amber",
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
    )
    AuthenticationTextField(
        value = uiState.email,
        onValueChange = onEmailChange,
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
        verticalArrangement = Arrangement.spacedBy(PADDING_MEDIUM),
    ) {
      // This button redirects the user to the Amber sign in page
      // This is an alternative to Google sign in
      AuthenticationButton(
          text = "Continue",
          onClick = onContinue,
          modifier = Modifier.testTag(EmailSignInTestTags.CONTINUE_BUTTON),
      )
      OrDivider()

      // Google section
      Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(PADDING_ENORMOUS),
      ) {
        Button(
            onClick = onGoogleClick,
            enabled = !uiState.isLoading,
            modifier =
                Modifier.fillMaxWidth()
                    .height(PADDING_ENORMOUS)
                    .testTag(EmailSignInTestTags.GOOGLE_BUTTON),
            shape = RoundedCornerShape(SMALL_ROUND_CORNER),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = lightOrange,
                    contentColor = Color.Black,
                ),
        ) {
          Image(
              painter = painterResource(R.drawable.google_logo),
              contentDescription = null, // no need to put a description for the logo
              modifier = Modifier.size(PADDING_BIG),
          )
          Spacer(modifier = Modifier.size(PADDING_REGULAR))
          AuthenticationText(
              text = if (uiState.isLoading) "Signing in…" else "Sign in with Google",
              style = MaterialTheme.typography.labelLarge,
          )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          AuthenticationText(
              modifier = Modifier.testTag(EmailSignInTestTags.SIGN_UP_PROMPT),
              text = "Don't have an Amber account?",
              // TextAlign is added in case the text has to wrap around in a narrow screen
              textAlign = TextAlign.Center,
              style = MaterialTheme.typography.labelLarge,
          )
          AuthenticationText(
              modifier = Modifier.testTag(EmailSignInTestTags.SIGN_UP_LINK),
              text = "Click here to sign up.",
              textAlign = TextAlign.Center,
              style = MaterialTheme.typography.labelLarge,
          )
        }
      }
    }
  }
}

/**
 * Displays a horizontal separator with the word "or" between two divider lines.
 *
 * Used to visually separate email authentication from Google authentication.
 */
@Composable
private fun OrDivider() {
  Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
  ) {
    HorizontalDivider(
        modifier = Modifier.weight(1f),
        color = lightGray,
    )

    AuthenticationText(
        modifier = Modifier.padding(horizontal = PADDING_MEDIUM),
        text = "or",
        style = MaterialTheme.typography.bodySmall,
        color = darkGray,
    )

    HorizontalDivider(
        modifier = Modifier.weight(1f),
        color = lightGray,
    )
  }
}

/** Preview of [EmailSignInScreen] using the Amber application theme. */
@Preview(showBackground = true)
@Composable
private fun EmailSignInScreenPreview() {
  AmberAppTheme(darkTheme = false, dynamicColor = false) {
    EmailSignInContent(
        uiState = EmailSignInUiState(),
        onEmailChange = {},
        onContinue = {},
        onGoogleClick = {},
    )
  }
}
