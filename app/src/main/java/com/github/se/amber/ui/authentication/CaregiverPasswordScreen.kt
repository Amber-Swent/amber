// This file was written with the aid of AI
package com.github.se.amber.ui.authentication

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.ui.theme.SampleAppTheme

/**
 * Displays the password authentication screen for a caregiver.
 *
 * The screen observes its email and password state from [CaregiverPasswordViewModel]. User password
 * input is forwarded to the ViewModel, while authentication and navigation actions are handled
 * through callbacks.
 *
 * If the caregiver forgets their password, the screen indicates that a password-reset link will be
 * sent to them.
 *
 * @param onContinue Called when the user presses the Continue button.
 * @param onForgotPassword Called when the user presses the forgotten-password button.
 * @param caregiverPasswordViewModel ViewModel holding the email and password state for this screen.
 * @param modifier Modifier applied to the screen.
 */
@Composable
fun CaregiverPasswordScreen(
    onContinue: () -> Unit = {},
    onForgotPassword: () -> Unit = {},
    caregiverPasswordViewModel: CaregiverPasswordViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
  val uiState by caregiverPasswordViewModel.uiState.collectAsState()

  PasswordScreen(
      email = uiState.email,
      password = uiState.password,
      onPasswordChange = caregiverPasswordViewModel::onPasswordChange,
      onContinue = onContinue,
      onForgotPassword = onForgotPassword,
      forgotPasswordExplanation = "This will send you a link to reset your password",
      modifier = modifier,
  )
}

/** Preview of [CaregiverPasswordScreen] using the Amber application theme. */
@Preview(showBackground = true)
@Composable
private fun CaregiverPasswordScreenPreview() {
  SampleAppTheme(dynamicColor = false) { CaregiverPasswordScreen() }
}
