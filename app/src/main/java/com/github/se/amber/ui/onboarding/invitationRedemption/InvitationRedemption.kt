// Written by Alaabrine with the aid of AI coding agent
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.onboarding.invitationRedemption

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.data.invitation.InvitationRedemptionResult
import com.github.se.amber.ui.caregiver.DisplayCaregiverButton
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.onboarding.AmberHeader
import com.github.se.amber.ui.onboarding.OnboardingOutlinedTextField
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_BIG
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.utils.asString

/** Stable tags for locating the invite form controls and validation message in UI tests. */
object InvitationRedemptionTestTags {
  const val CODE_INPUT = "InvitationRedemption_test_tag_code_input"
  const val CODE_PLACEHOLDER = "InvitationRedemption_test_tag_code_placeholder"
  const val CODE_ERROR = "InvitationRedemption_test_tag_code_error"
  const val SUBMIT_BUTTON = "InvitationRedemption_test_tag_submit_button"
}

/**
 * Screen where a patient or caregiver enters an invitation code to join an existing care circle.
 *
 * Validation and the repository call live in [viewModel]. [onRedeemSuccess] should navigate to the
 * home graph for the redeemed role. The top-bar Cancel calls [onBack] and leaves this step. It does
 * not remove a profile.
 *
 * @param modifier Modifier applied to the root invitation layout.
 * @param viewModel UI state and events for this screen. Defaults to a ViewModel scoped to the
 *   composition; tests can pass a custom instance with a fake repository.
 * @param onRedeemSuccess called with the redeemed circle and role after a successful redeem.
 * @param onBack called when the user taps Cancel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvitationRedemptionScreen(
    modifier: Modifier = Modifier,
    viewModel: InvitationRedemptionViewModel = viewModel(),
    onRedeemSuccess: (InvitationRedemptionResult) -> Unit = {},
    onBack: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  Scaffold(
      modifier = modifier.fillMaxSize(),
      containerColor = MaterialTheme.colorScheme.background,
      contentColor = MaterialTheme.colorScheme.onBackground,
      topBar = {
        TopAppBar(
            title = {
              TopNavigationMenu(
                  action = TopNavigationAction.CANCEL,
                  onBackClick = { if (!uiState.isRedeeming) onBack() },
              )
            }
        )
      },
  ) { innerPadding ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CAREGIVER_PADDING_BIG, vertical = CAREGIVER_PADDING_BIG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_BIG),
    ) {
      AmberHeader(horizontalAlignment = Alignment.CenterHorizontally)

      Text(
          text = stringResource(R.string.invitationRedemptionTitle),
          modifier = Modifier.fillMaxWidth().padding(top = CAREGIVER_PADDING_MEDIUM),
          style = MaterialTheme.typography.headlineSmall,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.primary,
      )

      OnboardingOutlinedTextField(
          value = uiState.inviteCode,
          onValueChange = viewModel::onInviteCodeChange,
          label = stringResource(R.string.invitationRedemptionCodeLabel),
          modifier = Modifier.testTag(InvitationRedemptionTestTags.CODE_INPUT),
          placeholder = stringResource(R.string.invitationRedemptionCodePlaceholder),
          placeholderTestTag = InvitationRedemptionTestTags.CODE_PLACEHOLDER,
          error = uiState.errorMessage?.asString(LocalResources.current),
          errorTestTag = InvitationRedemptionTestTags.CODE_ERROR,
          enabled = !uiState.isRedeeming,
          // Codes are in capitals: no suggestions, which would change a code into a word
          keyboardOptions =
              KeyboardOptions(
                  capitalization = KeyboardCapitalization.Characters,
                  autoCorrectEnabled = false,
                  keyboardType = KeyboardType.Ascii,
              ),
      )

      DisplayCaregiverButton(
          modifier = Modifier.testTag(InvitationRedemptionTestTags.SUBMIT_BUTTON),
          text = stringResource(R.string.invitationRedemptionSubmit),
          enabled = uiState.canSubmit,
          onClick = { viewModel.onSubmit(onRedeemSuccess) },
      )
    }
  }
}
