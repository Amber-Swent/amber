// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.authentication

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import com.github.se.amber.ui.caregiver.DisplayCaregiverButton
import com.github.se.amber.ui.patient.DisplayPatientButton

/**
 * Text style of the body of the authentication confirmation screens (sign out, delete account).
 *
 * Patient screens need larger text than caregiver ones.
 *
 * @param isPatient Boolean indicating if the user viewing the screen is a Patient.
 * @return the typography style to apply to the body text of the screen.
 */
@Composable
fun authenticationTextStyle(isPatient: Boolean = true): TextStyle =
    if (isPatient) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge

/**
 * Button of the authentication confirmation screens (sign out, delete account), built with the
 * shared helper matching the user's role.
 *
 * @param isPatient Boolean indicating if the user viewing the screen is a Patient.
 * @param text Label of the button.
 * @param onClick Callback invoked when the button is clicked.
 * @param enabled Whether the button can be clicked.
 * @param testTag Optional test tag. The helper's default tag is kept when null.
 */
@Composable
fun DisplayAuthenticationButton(
    isPatient: Boolean = true,
    text: String = "",
    onClick: () -> Unit = {},
    enabled: Boolean = true,
    testTag: String? = null,
) {
  if (isPatient) {
    DisplayPatientButton(text = text, onClick = onClick, enabled = enabled, testTag = testTag)
  } else if (testTag != null) {
    DisplayCaregiverButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        buttonTestTag = testTag,
    )
  } else {
    DisplayCaregiverButton(text = text, onClick = onClick, enabled = enabled)
  }
}
