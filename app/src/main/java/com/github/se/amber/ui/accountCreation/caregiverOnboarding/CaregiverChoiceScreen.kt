// this code was written by Alaabrine
// with the aid of AI coding agent
package com.github.se.amber.ui.accountCreation.caregiverOnboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.github.se.amber.ui.components.AmberHeader
import com.github.se.amber.ui.theme.BUTTON_HEIGHT
import com.github.se.amber.ui.theme.BUTTON_HEIGHT_TALL
import com.github.se.amber.ui.theme.PADDING_BIG
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.darkGray
import com.github.se.amber.ui.theme.lightGray
import com.github.se.amber.ui.theme.lightOrange

/** Test tags for [CaregiverChoiceScreen]. */
enum class CaregiverChoiceTestTags {
  BACK_BUTTON,
  WELCOME_HEADER,
  INSTRUCTION_TEXT,
  CREATE_BUTTON,
  OR_DIVIDER,
  JOIN_BUTTON,
  INVITE_LINK_HINT,
}

internal const val CREATE_CIRCLE_LABEL = "Create a new care circle"
internal const val JOIN_CIRCLE_LABEL = "Join an existing care circle"
internal const val INSTRUCTION_LABEL = "How will you enter your Care Circle?"
internal const val INVITE_LINK_HINT_LABEL = "Use your invite link"
internal const val OR_LABEL = "or"
internal const val BACK_LABEL = "Back"

/**
 * Caregiver onboarding step: choose to create a new care circle or join an existing one.
 *
 * [onBack] should navigate to [OnboardingScreen.CreateProfile]. [onCreateCircle] and [onJoinCircle]
 * are invoked directly from the buttons so a navigation owner can route to
 * [OnboardingScreen.CreateCareCircle] or [OnboardingScreen.EnterInvitation].
 */
@Composable
fun CaregiverChoiceScreen(
    viewModel: CaregiverChoiceViewModel = viewModel(),
    onBack: () -> Unit = {},
    onCreateCircle: () -> Unit = {},
    onJoinCircle: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsState()

  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(Color.White)
              .padding(horizontal = PADDING_BIG, vertical = PADDING_MEDIUM),
      verticalArrangement = Arrangement.spacedBy(PADDING_BIG + PADDING_MEDIUM),
  ) {
    AmberHeader()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PADDING_BIG),
    ) {
      Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(PADDING_SMALL),
      ) {
        Text(
            text = "Welcome, ${uiState.displayName}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(CaregiverChoiceTestTags.WELCOME_HEADER.name),
        )
        Text(
            text = INSTRUCTION_LABEL,
            style = MaterialTheme.typography.titleMedium,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(CaregiverChoiceTestTags.INSTRUCTION_TEXT.name),
        )
      }

      Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(PADDING_MEDIUM),
      ) {
        Button(
            onClick = onCreateCircle,
            modifier =
                Modifier.fillMaxWidth(0.75f)
                    .height(BUTTON_HEIGHT_TALL)
                    .testTag(CaregiverChoiceTestTags.CREATE_BUTTON.name),
            shape = RoundedCornerShape(PADDING_SMALL),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = lightOrange,
                    contentColor = Color.Black,
                ),
        ) {
          Text(
              text = CREATE_CIRCLE_LABEL,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Medium,
              textAlign = TextAlign.Center,
          )
        }

        OrDivider(
            modifier =
                Modifier.fillMaxWidth(0.75f).testTag(CaregiverChoiceTestTags.OR_DIVIDER.name),
        )

        Button(
            onClick = onJoinCircle,
            modifier =
                Modifier.fillMaxWidth(0.75f)
                    .height(BUTTON_HEIGHT_TALL)
                    .testTag(CaregiverChoiceTestTags.JOIN_BUTTON.name),
            shape = RoundedCornerShape(PADDING_SMALL),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = lightOrange,
                    contentColor = Color.Black,
                ),
        ) {
          Text(
              text = JOIN_CIRCLE_LABEL,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Medium,
              textAlign = TextAlign.Center,
          )
        }

        Text(
            text = INVITE_LINK_HINT_LABEL,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black,
            modifier = Modifier.testTag(CaregiverChoiceTestTags.INVITE_LINK_HINT.name),
        )
      }
    }

    Spacer(modifier = Modifier.weight(1f))

    Button(
        onClick = onBack,
        modifier =
            Modifier.fillMaxWidth(0.55f)
                .align(Alignment.CenterHorizontally)
                .height(BUTTON_HEIGHT)
                .testTag(CaregiverChoiceTestTags.BACK_BUTTON.name),
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

@Composable
private fun OrDivider(modifier: Modifier = Modifier) {
  Row(
      modifier = modifier,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    HorizontalDivider(
        modifier = Modifier.weight(1f),
        color = lightGray,
    )
    Text(
        text = OR_LABEL,
        color = darkGray,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = PADDING_MEDIUM),
    )
    HorizontalDivider(
        modifier = Modifier.weight(1f),
        color = lightGray,
    )
  }
}

@Preview(showBackground = true)
@Composable
fun CaregiverChoicePreview() {
  CaregiverChoiceScreen()
}
