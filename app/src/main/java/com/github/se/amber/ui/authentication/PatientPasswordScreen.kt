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
 * Displays the password authentication screen for a patient.
 *
 * The screen observes its email and password state from [PatientPasswordViewModel]. User password
 * input is forwarded to the ViewModel, while authentication and navigation actions are handled
 * through callbacks.
 *
 * If the patient forgets their password, the screen indicates that a help message will be sent to
 * their loved ones.
 *
 * @param onContinue Called when the user presses the Continue button.
 * @param onForgotPassword Called when the user presses the forgotten-password button.
 * @param patientPasswordViewModel ViewModel holding the email and password state for this screen.
 * @param modifier Modifier applied to the screen.
 */
@Composable
fun PatientPasswordScreen(
    onContinue: () -> Unit = {},
    onForgotPassword: () -> Unit = {},
    patientPasswordViewModel: PatientPasswordViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
  val uiState by patientPasswordViewModel.uiState.collectAsState()

  PasswordScreen(
      email = uiState.email,
      password = uiState.password,
      onPasswordChange = patientPasswordViewModel::onPasswordChange,
      onContinue = onContinue,
      onForgotPassword = onForgotPassword,
      forgotPasswordExplanation = "This will send a help message to your loved ones",
      modifier = modifier,
  )
}

/** Preview of [PatientPasswordScreen] using the Amber application theme. */
@Preview(showBackground = true)
@Composable
private fun PatientPasswordScreenPreview() {
  SampleAppTheme(dynamicColor = false) { PatientPasswordScreen() }
}
