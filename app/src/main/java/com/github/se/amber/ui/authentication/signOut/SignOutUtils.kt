// Written by Alaabrine, with assistance from Cursor.
package com.github.se.amber.ui.authentication.signOut

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_HEIGHT_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_WIDTH
import com.github.se.amber.ui.theme.ROUND_CORNER

/** Test tags owned by [SignOutButton]. */
object SignOutButtonTestTags {
  const val BUTTON = "SignOutButton_test_tag_button"
}

/**
 * Shared confirmation button that signs the user out through [SignOutViewModel].
 *
 * Reused by [SignOutScreen] and by onboarding flows that need the same action.
 *
 * @param modifier Modifier applied to the button.
 * @param text Label displayed on the button.
 * @param buttonHeight Fixed height of the button (caller screens that scroll may override).
 * @param buttonWidth Fraction of the available width taken by the button.
 * @param viewModel ViewModel that performs [SignOutViewModel.signOut].
 * @param testTag Test tag of the button.
 */
@Composable
fun SignOutButton(
    modifier: Modifier = Modifier,
    text: String = "Sign out",
    buttonHeight: Dp = CAREGIVER_BUTTON_HEIGHT_MEDIUM,
    buttonWidth: Float = CAREGIVER_BUTTON_WIDTH,
    viewModel: SignOutViewModel = viewModel(),
    testTag: String = SignOutButtonTestTags.BUTTON,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  Button(
      onClick = { viewModel.signOut() },
      enabled = !uiState.signedOut && !uiState.isSigningOut,
      modifier =
          modifier
              .fillMaxWidth()
              .wrapContentWidth(Alignment.CenterHorizontally)
              .fillMaxWidth(buttonWidth)
              .height(buttonHeight)
              .testTag(testTag),
      shape = RoundedCornerShape(ROUND_CORNER),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.tertiary,
              contentColor = MaterialTheme.colorScheme.onTertiary,
          ),
  ) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
  }
}

/** Preview of [SignOutButton] using the Amber application theme. */
@Preview(showBackground = true)
@Composable
private fun SignOutButtonPreview() {
  AmberAppTheme(dynamicColor = false) { SignOutButton() }
}
