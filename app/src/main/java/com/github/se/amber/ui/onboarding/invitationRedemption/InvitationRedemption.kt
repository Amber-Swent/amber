// this code was written by Alaabrine
// with the aid of AI coding agent
// Modified by Yuhan YANG, with assistance from Cursor.
package com.github.se.amber.ui.onboarding.invitationRedemption

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.data.invitation.InvitationRedemptionResult
import com.github.se.amber.ui.caregiver.DisplayCaregiverButton
import com.github.se.amber.ui.onboarding.AmberHeader
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_BIG
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.ROUND_CORNER
import com.github.se.amber.ui.theme.WEIGHT_ONE

/** Stable tags for locating the invite form controls and validation message in UI tests. */
object InvitationRedemptionTestTags {
  const val CODE_INPUT = "InvitationRedemption_test_tag_code_input"
  const val CODE_PLACEHOLDER = "InvitationRedemption_test_tag_code_placeholder"
  const val CODE_ERROR = "InvitationRedemption_test_tag_code_error"
  const val SUBMIT_BUTTON = "InvitationRedemption_test_tag_submit_button"
  const val BACK_BUTTON = "InvitationRedemption_test_tag_back_button"
}

internal const val TITLE_LABEL = "Enter your invite link"
internal const val CODE_PLACEHOLDER_LABEL = "Enter your invitation Code"
internal const val SUBMIT_LABEL = "Submit"
internal const val BACK_LABEL = "Back"

/**
 * Screen where a patient or caregiver enters an invitation code to join an existing care circle.
 *
 * Validation and the repository call live in [viewModel]. [onRedeemSuccess] should navigate to the
 * home graph for the redeemed role. [onBack] returns to the previous step.
 *
 * @param modifier Modifier applied to the root invitation layout.
 * @param viewModel UI state and events for this screen. Defaults to a ViewModel scoped to the
 *   composition; tests can pass a custom instance with a fake repository.
 * @param onRedeemSuccess called with the redeemed circle and role after a successful redeem.
 * @param onBack called when the user leaves this step.
 */
@Composable
fun InvitationRedemptionScreen(
    modifier: Modifier = Modifier,
    viewModel: InvitationRedemptionViewModel = viewModel(),
    onRedeemSuccess: (InvitationRedemptionResult) -> Unit = {},
    onBack: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  Surface(
      modifier = modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
      contentColor = MaterialTheme.colorScheme.onBackground,
  ) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CAREGIVER_PADDING_BIG, vertical = CAREGIVER_PADDING_BIG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_BIG),
    ) {
      AmberHeader()

      Text(
          text = TITLE_LABEL,
          modifier = Modifier.fillMaxWidth(WEIGHT_ONE).padding(top = CAREGIVER_PADDING_MEDIUM),
          style = MaterialTheme.typography.headlineSmall,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.primary,
      )

      OutlinedTextField(
          value = uiState.inviteCode,
          onValueChange = viewModel::onInviteCodeChange,
          modifier =
              Modifier.fillMaxWidth(WEIGHT_ONE).testTag(InvitationRedemptionTestTags.CODE_INPUT),
          label = {
            Text(text = CODE_PLACEHOLDER_LABEL, style = MaterialTheme.typography.labelMedium)
          },
          placeholder = {
            Text(
                text = CODE_PLACEHOLDER_LABEL,
                modifier = Modifier.testTag(InvitationRedemptionTestTags.CODE_PLACEHOLDER),
                style = MaterialTheme.typography.labelMedium,
            )
          },
          supportingText =
              uiState.errorMessage?.let { error ->
                {
                  Text(
                      text = error,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.error,
                      modifier = Modifier.testTag(InvitationRedemptionTestTags.CODE_ERROR),
                  )
                }
              },
          isError = uiState.errorMessage != null,
          enabled = !uiState.isRedeeming,
          singleLine = true,
          shape = RoundedCornerShape(ROUND_CORNER),
          // Move these colors into a shared utils helper later.
          // Doing that now would conflict with other open changes.
          colors =
              OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = MaterialTheme.colorScheme.outline,
                  unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                  focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                  unfocusedLabelColor = MaterialTheme.colorScheme.onSurface,
                  cursorColor = MaterialTheme.colorScheme.onSurface,
                  focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface,
                  unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface,
                  errorBorderColor = MaterialTheme.colorScheme.error,
                  errorLabelColor = MaterialTheme.colorScheme.error,
                  errorCursorColor = MaterialTheme.colorScheme.error,
                  errorSupportingTextColor = MaterialTheme.colorScheme.error,
              ),
      )

      DisplayCaregiverButton(
          modifier = Modifier.testTag(InvitationRedemptionTestTags.SUBMIT_BUTTON),
          text = SUBMIT_LABEL,
          enabled = !uiState.isRedeeming,
          onClick = { viewModel.onSubmit(onRedeemSuccess) },
      )
      DisplayCaregiverButton(
          modifier = Modifier.testTag(InvitationRedemptionTestTags.BACK_BUTTON),
          text = BACK_LABEL,
          enabled = !uiState.isRedeeming,
          onClick = onBack,
      )
    }
  }
}
