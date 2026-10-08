// This file was written with the aid of AI
package com.github.se.amber.ui.authentication

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.ui.theme.SampleAppTheme
import com.github.se.amber.ui.theme.darkGray
import com.github.se.amber.ui.theme.lightGray
import com.github.se.amber.ui.theme.lightOrange

/**
 * Displays the initial authentication screen where the user can enter an email address or
 * authenticate using Google.
 *
 * Authentication state is provided by [EmailSignInViewModel]. Navigation is handled outside this
 * composable through callbacks.
 *
 * @param context Android context used for Google authentication.
 * @param credentialManager Credential Manager used for Google authentication.
 * @param modifier Modifier applied to the root authentication layout.
 * @param emailSignInViewModel ViewModel holding the state of the screen.
 * @param onContinue Called when the user presses the Continue button.
 * @param onGoogleSignIn Called after Google authentication succeeds.
 */
@Composable
fun EmailSignInScreen(
    context: Context = LocalContext.current,
    credentialManager: CredentialManager = CredentialManager.create(context),
    onContinue: () -> Unit = {},
    onGoogleSignIn: () -> Unit = {},
    emailSignInViewModel: EmailSignInViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
  val uiState by emailSignInViewModel.uiState.collectAsState()

  LaunchedEffect(uiState.user) {
    if (uiState.user != null) {
      onGoogleSignIn()
    }
  }
  AuthenticationLayout(modifier = modifier) {
    Text(
        text = "Create an account",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = "Enter your email to sign up",
        style = MaterialTheme.typography.bodyLarge,
    )

    Spacer(modifier = Modifier.height(16.dp))

    AuthenticationTextField(
        value = uiState.email,
        onValueChange = emailSignInViewModel::onEmailChange,
        placeholder = "email@domain.com",
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
            ),
    )

    Spacer(modifier = Modifier.height(12.dp))

    AuthenticationButton(
        text = "Continue",
        onClick = onContinue,
    )

    Spacer(modifier = Modifier.height(18.dp))

    OrDivider()

    Spacer(modifier = Modifier.height(18.dp))

    Button(
        onClick = { emailSignInViewModel.signInWithGoogle(context, credentialManager) },
        enabled = !uiState.isLoading,
        modifier = Modifier.fillMaxWidth().height(44.dp),
        shape = buttonShape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = lightOrange,
                contentColor = Color.Black,
            ),
    ) {
      Image(
          painter = painterResource(R.drawable.google_logo),
          contentDescription = null, // no need to put a description for the logo
          modifier = Modifier.size(24.dp),
      )
      Spacer(modifier = Modifier.size(12.dp))
      Text(
          text = "Sign in with Google",
          style = MaterialTheme.typography.labelLarge,
      )
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

    Text(
        text = "or",
        modifier = Modifier.padding(horizontal = 14.dp),
        color = darkGray,
        style = MaterialTheme.typography.bodySmall,
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
  SampleAppTheme(dynamicColor = false) { EmailSignInScreen() }
}
