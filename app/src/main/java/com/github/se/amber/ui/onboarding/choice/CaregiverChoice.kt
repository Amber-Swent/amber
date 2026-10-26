/* Written by Alaabrine with the aid of AI coding agent.
 * Modify by LaMaier, with the assistance of Claude Code.
 */
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.ui.caregiver.DisplayCaregiverButton
import com.github.se.amber.ui.onboarding.AmberHeader
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_WIDTH
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_BIG
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_ENORMOUS
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.OrDivider
import com.github.se.amber.ui.theme.PADDING_MINI
import com.github.se.amber.ui.theme.WEIGHT_ONE

/** Test tags for [CaregiverChoiceScreen]. */
object CaregiverChoiceTestTags {
  const val BACK_BUTTON = "caregiver_choice_back_button"
  const val WELCOME_HEADER = "caregiver_choice_welcom_header"
  const val CREATE_BUTTON = "caregiver_choice_create_button"
  const val JOIN_BUTTON = "caregiver_choice_join_button"
}

internal const val CREATE_CIRCLE_LABEL = "Create a new care circle"
internal const val JOIN_CIRCLE_LABEL = "Join an existing care circle"
internal const val INSTRUCTION_LABEL = "How will you enter your Care Circle?"
internal const val INVITE_LINK_HINT_LABEL_CONDITION =
    "To join an existing care circle, you must have an invite link."
internal const val INVITE_LINK_HINT_LABEL_SOLUTION =
    "Any caregiver belonging to the circle can create a link for you."
internal const val BACK_LABEL = "Back"

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
            text =
                if (uiState.displayName.isEmpty()) "Welcome" else "Welcome, ${uiState.displayName}",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(CaregiverChoiceTestTags.WELCOME_HEADER),
        )
        Text(
            text = INSTRUCTION_LABEL,
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
            text = CREATE_CIRCLE_LABEL,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.testTag(CaregiverChoiceTestTags.CREATE_BUTTON),
        )

        OrDivider(modifier = Modifier.fillMaxWidth(CAREGIVER_BUTTON_WIDTH))

        DisplayCaregiverButton(
            onClick = onJoinCircle,
            text = JOIN_CIRCLE_LABEL,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.testTag(CaregiverChoiceTestTags.JOIN_BUTTON),
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(PADDING_MINI)) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Info",
            tint = MaterialTheme.colorScheme.outline,
        )
        Column {
          Text(
              text = INVITE_LINK_HINT_LABEL_CONDITION,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.outline,
          )
          Text(
              text = INVITE_LINK_HINT_LABEL_SOLUTION,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.outline,
          )
        }
      }
    }

    DisplayCaregiverButton(
        onClick = onBack,
        text = BACK_LABEL,
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
