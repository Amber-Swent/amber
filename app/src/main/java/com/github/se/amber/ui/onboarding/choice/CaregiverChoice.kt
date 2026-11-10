// Written by Alaabrine with the aid of AI coding agent.
// Modified by LaMaier, with the assistance of Claude Code.
// Modified by MeiliV, with assistance from OpenAI Codex.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.onboarding.choice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.ui.caregiver.DisplayCaregiverButton
import com.github.se.amber.ui.onboarding.AmberHeader
import com.github.se.amber.ui.onboarding.Divider
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_WIDTH_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_BIG
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_ENORMOUS
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_MINI
import com.github.se.amber.ui.theme.WEIGHT_ONE

/** Test tags for [CaregiverChoiceScreen]. */
object CaregiverChoiceTestTags {
  const val BACK_BUTTON = "caregiver_choice_back_button"
  const val WELCOME_HEADER = "caregiver_choice_welcom_header"
  const val QUESTION = "caregiver_choice_question"
  const val CREATE_BUTTON = "caregiver_choice_create_button"
  const val JOIN_BUTTON = "caregiver_choice_join_button"
  const val INFORMATION = "caregiver_choice_information_text"
}

/**
 * Caregiver onboarding step: choose to create a new care circle or join an existing one.
 *
 * [onBack] should navigate to CreateProfileScreen. [onCreateCircle] and [onJoinCircle] are invoked
 * directly from the buttons so a navigation owner can route to CreateCareCircle or EnterInvitation
 * respectively.
 */
@Composable
fun CaregiverChoiceScreen(
    viewModel: CaregiverChoiceViewModel = viewModel(),
    onBack: () -> Unit = {},
    onCreateCircle: () -> Unit = {},
    onJoinCircle: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .padding(
                  horizontal = CAREGIVER_PADDING_MEDIUM,
                  vertical = CAREGIVER_PADDING_MEDIUM,
              ),
      horizontalAlignment = Alignment.CenterHorizontally,
      // space between the Header and the "Welcome"-Button part
      verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_ENORMOUS),
  ) {
    AmberHeader()

    Column(
        // the weight ensure the Back button is at the bottom
        modifier = Modifier.fillMaxWidth().weight(WEIGHT_ONE),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_BIG),
    ) {
      Column(
          horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
            modifier = Modifier.testTag(CaregiverChoiceTestTags.WELCOME_HEADER),
            text =
                if (uiState.displayName.isEmpty()) stringResource(R.string.caregiverChoiceWelcome)
                else stringResource(R.string.caregiverChoiceWelcomeName, uiState.displayName),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            modifier = Modifier.testTag(CaregiverChoiceTestTags.QUESTION),
            text = stringResource(R.string.caregiverChoiceQuestion),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
      }

      Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(PADDING_MINI),
      ) {
        DisplayCaregiverButton(
            onClick = onCreateCircle,
            text = stringResource(R.string.caregiverChoiceCreateCircle),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.testTag(CaregiverChoiceTestTags.CREATE_BUTTON),
        )

        Divider(modifier = Modifier.fillMaxWidth(CAREGIVER_BUTTON_WIDTH_MEDIUM))

        DisplayCaregiverButton(
            onClick = onJoinCircle,
            text = stringResource(R.string.caregiverChoiceJoinCircle),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.testTag(CaregiverChoiceTestTags.JOIN_BUTTON),
        )
      }

      Row(
          modifier = Modifier.testTag(CaregiverChoiceTestTags.INFORMATION),
          horizontalArrangement = Arrangement.spacedBy(PADDING_MINI),
      ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = stringResource(R.string.caregiverChoiceInfo),
            tint = MaterialTheme.colorScheme.outline,
        )
        Column {
          Text(
              text = stringResource(R.string.caregiverChoiceInviteHintCondition),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.outline,
          )
          Text(
              text = stringResource(R.string.caregiverChoiceInviteHintSolution),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.outline,
          )
        }
      }
    }

    DisplayCaregiverButton(
        onClick = onBack,
        text = stringResource(R.string.caregiverChoiceBack),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.testTag(CaregiverChoiceTestTags.BACK_BUTTON),
    )
  }
}

@Preview(showBackground = true)
@Composable
fun CaregiverChoicePreview() {
  AmberAppTheme(darkTheme = false) { CaregiverChoiceScreen() }
}
