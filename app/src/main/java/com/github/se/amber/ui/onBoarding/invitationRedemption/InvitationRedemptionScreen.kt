// this code was written by Alaabrine
// with the aid of AI coding agent
package com.github.se.amber.ui.onBoarding.invitationRedemption

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.data.invitation.InvitationRedemptionResult
import com.github.se.amber.ui.onBoarding.AmberHeader
import com.github.se.amber.ui.theme.BUTTON_HEIGHT
import com.github.se.amber.ui.theme.PADDING_BIG
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.darkGray
import com.github.se.amber.ui.theme.lightGray
import com.github.se.amber.ui.theme.lightOrange

/** Test tags for [InvitationRedemptionScreen]. */
enum class InvitationRedemptionTestTags {
  CODE_INPUT,
  CODE_PLACEHOLDER,
  CODE_ERROR,
  SUBMIT_BUTTON,
  BACK_BUTTON,
}

internal const val TITLE_LABEL = "Enter your invite link"
internal const val CODE_PLACEHOLDER_LABEL = "Enter your invitation Code"
internal const val SUBMIT_LABEL = "Submit"
internal const val BACK_LABEL = "Back"

/**
 * Screen where a user enters an invitation code / link to join an existing care circle.
 *
 * [onRedeemSuccess] should navigate to the matching home graph. [onBack] returns to the previous
 * onboarding step.
 */
@Composable
fun InvitationRedemptionScreen(
    viewModel: InvitationRedemptionViewModel = viewModel(),
    onRedeemSuccess: (InvitationRedemptionResult) -> Unit = {},
    onBack: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsState()

  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(Color.White)
              .padding(horizontal = PADDING_BIG, vertical = PADDING_MEDIUM),
  ) {
    AmberHeader()

    Spacer(modifier = Modifier.weight(1f))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PADDING_BIG),
    ) {
      Text(
          text = TITLE_LABEL,
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          color = Color.Black,
          textAlign = TextAlign.Center,
      )

      OutlinedTextField(
          value = uiState.inviteCode,
          onValueChange = viewModel::onInviteCodeChange,
          modifier =
              Modifier.fillMaxWidth(0.9f).testTag(InvitationRedemptionTestTags.CODE_INPUT.name),
          placeholder = {
            Text(
                text = CODE_PLACEHOLDER_LABEL,
                modifier = Modifier.testTag(InvitationRedemptionTestTags.CODE_PLACEHOLDER.name),
            )
          },
          supportingText =
              uiState.errorMessage?.let { error ->
                {
                  Text(
                      text = error,
                      style = MaterialTheme.typography.bodySmall,
                      modifier = Modifier.testTag(InvitationRedemptionTestTags.CODE_ERROR.name),
                  )
                }
              },
          isError = uiState.errorMessage != null,
          enabled = !uiState.isRedeeming,
          singleLine = true,
          shape = RoundedCornerShape(PADDING_SMALL),
          colors =
              OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = darkGray,
                  unfocusedBorderColor = darkGray.copy(alpha = 0.4f),
                  errorBorderColor = MaterialTheme.colorScheme.error,
              ),
      )

      Button(
          onClick = { viewModel.onSubmit(onRedeemSuccess) },
          enabled = !uiState.isRedeeming,
          modifier =
              Modifier.fillMaxWidth(0.75f)
                  .height(BUTTON_HEIGHT)
                  .testTag(InvitationRedemptionTestTags.SUBMIT_BUTTON.name),
          shape = RoundedCornerShape(PADDING_SMALL),
          colors =
              ButtonDefaults.buttonColors(
                  containerColor = lightOrange,
                  contentColor = Color.Black,
              ),
      ) {
        Text(
            text = SUBMIT_LABEL,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
      }
    }

    Spacer(modifier = Modifier.weight(1f))

    Button(
        onClick = onBack,
        enabled = !uiState.isRedeeming,
        modifier =
            Modifier.fillMaxWidth(0.55f)
                .align(Alignment.CenterHorizontally)
                .height(BUTTON_HEIGHT)
                .testTag(InvitationRedemptionTestTags.BACK_BUTTON.name),
        shape = RoundedCornerShape(PADDING_SMALL),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = lightGray,
                contentColor = Color.Black,
            ),
    ) {
      Text(
          text = BACK_LABEL,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Medium,
          textAlign = TextAlign.Center,
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
fun InvitationRedemptionPreview() {
  InvitationRedemptionScreen()
}
