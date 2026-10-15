// this code was written by Alaabrine
// with the aid of AI coding agent
package com.github.se.amber.ui.accountCreation.caregiverOnboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.ui.theme.PADDING_BIG
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.darkGray
import com.github.se.amber.ui.theme.lightGray
import com.github.se.amber.ui.theme.lightOrange

/** Test tags for [CaregiverJoinOrCreateCircle]. */
enum class CaregiverJoinOrCreateCircleTestTags {
  BACK_BUTTON,
  LOGO,
  BRAND_NAME,
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
fun CaregiverJoinOrCreateCircle(
    viewModel: CaregiverJoinOrCreateCircleViewModel = viewModel(),
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
  ) {
    Column(horizontalAlignment = Alignment.Start) {
      Box(
          modifier =
              Modifier.size(56.dp)
                  .background(lightOrange, CircleShape)
                  .testTag(CaregiverJoinOrCreateCircleTestTags.LOGO.name),
          contentAlignment = Alignment.Center,
      ) {
        Text(text = "logo", fontSize = 12.sp, color = Color.Black)
      }
      Text(
          text = "Amber",
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          modifier =
              Modifier.padding(top = PADDING_SMALL)
                  .testTag(CaregiverJoinOrCreateCircleTestTags.BRAND_NAME.name),
      )
    }

    Spacer(modifier = Modifier.height(40.dp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
          text = "Welcome, ${uiState.displayName}",
          fontWeight = FontWeight.Bold,
          fontSize = 28.sp,
          color = Color.Black,
          textAlign = TextAlign.Center,
          modifier = Modifier.testTag(CaregiverJoinOrCreateCircleTestTags.WELCOME_HEADER.name),
      )
      Spacer(modifier = Modifier.height(PADDING_SMALL))
      Text(
          text = INSTRUCTION_LABEL,
          fontWeight = FontWeight.Normal,
          fontSize = 16.sp,
          color = Color.Black,
          textAlign = TextAlign.Center,
          modifier = Modifier.testTag(CaregiverJoinOrCreateCircleTestTags.INSTRUCTION_TEXT.name),
      )

      Spacer(modifier = Modifier.height(PADDING_BIG * 2))

      Button(
          onClick = onCreateCircle,
          modifier =
              Modifier.fillMaxWidth(0.75f)
                  .height(72.dp)
                  .testTag(CaregiverJoinOrCreateCircleTestTags.CREATE_BUTTON.name),
          shape = RoundedCornerShape(12.dp),
          colors =
              ButtonDefaults.buttonColors(
                  containerColor = lightOrange,
                  contentColor = Color.Black,
              ),
      ) {
        Text(
            text = CREATE_CIRCLE_LABEL,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
      }

      Spacer(modifier = Modifier.height(PADDING_MEDIUM))

      OrDivider(
          modifier =
              Modifier.fillMaxWidth(0.75f)
                  .testTag(CaregiverJoinOrCreateCircleTestTags.OR_DIVIDER.name),
      )

      Spacer(modifier = Modifier.height(PADDING_MEDIUM))

      Button(
          onClick = onJoinCircle,
          modifier =
              Modifier.fillMaxWidth(0.75f)
                  .height(72.dp)
                  .testTag(CaregiverJoinOrCreateCircleTestTags.JOIN_BUTTON.name),
          shape = RoundedCornerShape(12.dp),
          colors =
              ButtonDefaults.buttonColors(
                  containerColor = lightOrange,
                  contentColor = Color.Black,
              ),
      ) {
        Text(
            text = JOIN_CIRCLE_LABEL,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
      }

      Spacer(modifier = Modifier.height(PADDING_SMALL))

      Text(
          text = INVITE_LINK_HINT_LABEL,
          fontSize = 14.sp,
          color = Color.Black,
          modifier = Modifier.testTag(CaregiverJoinOrCreateCircleTestTags.INVITE_LINK_HINT.name),
      )
    }

    Spacer(modifier = Modifier.weight(1f))

    Button(
        onClick = onBack,
        modifier =
            Modifier.fillMaxWidth(0.55f)
                .align(Alignment.CenterHorizontally)
                .height(56.dp)
                .testTag(CaregiverJoinOrCreateCircleTestTags.BACK_BUTTON.name),
        shape = RoundedCornerShape(12.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = lightGray,
                contentColor = Color.Black,
            ),
    ) {
      Text(
          text = BACK_LABEL,
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
        fontSize = 18.sp,
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
fun CaregiverJoinOrCreateCirclePreview() {
  CaregiverJoinOrCreateCircle()
}
