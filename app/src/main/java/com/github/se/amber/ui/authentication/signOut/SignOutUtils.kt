// Written by Alaabrine, with assistance from Cursor.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
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
import androidx.credentials.Credential
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthUser
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_HEIGHT_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_WIDTH
import com.github.se.amber.ui.theme.ROUND_CORNER

/** No-op [AuthRepository] used by Compose previews (and preview-driven tests). */
internal object PreviewSignOutAuthRepository : AuthRepository {
  override val currentUser: AuthUser? = null

  override suspend fun signInWithGoogle(credential: Credential): Result<AuthUser> =
      Result.failure(UnsupportedOperationException("Preview auth repository"))

  override suspend fun reauthenticateWithGoogle(credential: Credential): Result<Unit> =
      Result.failure(UnsupportedOperationException("Preview auth repository"))

  override suspend fun signOut(): Result<Unit> = Result.success(Unit)
}

/** Test tags owned by [SignOutButton]. */
object SignOutButtonTestTags {
  const val BUTTON = "SignOutButton_test_tag_button"
}

/**
 * Shared confirmation button that signs the user out through [SignOutViewModel].
 *
 * Reused by [SignOutScreen] and by onboarding flows that need the same action.
 *
 * @param viewModel ViewModel that performs [SignOutViewModel.signOut]. Required so callers (and
 *   tests) share one instance with [SignOutScreen].
 * @param modifier Modifier applied to the button.
 * @param text Label displayed on the button.
 * @param buttonHeight Fixed height of the button (caller screens that scroll may override).
 * @param buttonWidth Fraction of the available width taken by the button.
 * @param testTag Test tag of the button.
 */
@Composable
fun SignOutButton(
    viewModel: SignOutViewModel,
    modifier: Modifier = Modifier,
    text: String = "Sign out",
    buttonHeight: Dp = CAREGIVER_BUTTON_HEIGHT_MEDIUM,
    buttonWidth: Float = CAREGIVER_BUTTON_WIDTH,
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

internal val previewSignOutViewModel = SignOutViewModel(PreviewSignOutAuthRepository)

/** Preview of [SignOutButton] using the Amber application theme. */
@Preview(showBackground = true)
@Composable
internal fun SignOutButtonPreview() {
  AmberAppTheme(dynamicColor = false) {
    // Pass an explicit ViewModel so preview/tests do not touch Firebase via [viewModel].
    SignOutButton(viewModel = previewSignOutViewModel)
  }
}
