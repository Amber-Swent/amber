// Written by Alaabrine with the aid of AI coding agent.
// Modified by LaMaier, with the assistance of Claude Code.
// Modified by MeiliV, with assistance from OpenAI Codex.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.onboarding.choice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.caregiver.DisplayCaregiverButton
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.onboarding.AmberHeader
import com.github.se.amber.ui.onboarding.Divider
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_WIDTH_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_BIG
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_ENORMOUS
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_MINI

/** Test tags for [CaregiverChoiceScreen]. */
object CaregiverChoiceTestTags {
  const val WELCOME_HEADER = "caregiver_choice_welcom_header"
  const val QUESTION = "caregiver_choice_question"
  const val CREATE_BUTTON = "caregiver_choice_create_button"
  const val JOIN_BUTTON = "caregiver_choice_join_button"
  const val INFORMATION = "caregiver_choice_information_text"
}

/**
 * Caregiver onboarding step: choose to create a new care circle or join an existing one.
 *
 * The top Cancel calls [onBack], which returns to the previous screen so a wrong role can be chosen
 * again. [onCreateCircle] and [onJoinCircle] are invoked directly from the buttons so a navigation
 * owner can route to CreateCareCircle or EnterInvitation respectively.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaregiverChoiceScreen(
    viewModel: CaregiverChoiceViewModel = viewModel(),
    onBack: () -> Unit = {},
    onCreateCircle: () -> Unit = {},
    onJoinCircle: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      contentColor = MaterialTheme.colorScheme.onBackground,
      topBar = {
        TopAppBar(
            title = {
              TopNavigationMenu(
                  action = TopNavigationAction.CANCEL,
                  onBackClick = onBack,
              )
            }
        )
      },
  ) { innerPadding ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(innerPadding)
                .padding(
                    horizontal = CAREGIVER_PADDING_MEDIUM,
                    vertical = CAREGIVER_PADDING_MEDIUM,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_ENORMOUS),
    ) {
      AmberHeader()

      Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_BIG),
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
    }
  }
}

@Preview(showBackground = true)
@Composable
fun CaregiverChoicePreview() {
  val viewModel = remember {
    CaregiverChoiceViewModel(profileRepository = PreviewChoiceProfileRepository)
  }
  AmberAppTheme { CaregiverChoiceScreen(viewModel = viewModel) }
}

private object PreviewChoiceProfileRepository : ProfileRepository {
  override suspend fun createProfile(person: Person, role: Role): Result<Unit> =
      Result.success(Unit)

  override suspend fun updateOnboardingProfile(person: Person, role: Role): Result<Unit> =
      Result.success(Unit)

  override suspend fun getCurrentProfile(): Result<UserProfile?> =
      Result.success(
          UserProfile(
              uid = "preview",
              role = Role.CAREGIVER,
              person = Person(id = "preview", firstName = "Ada", lastName = "Lovelace"),
          ),
      )

  override suspend fun getProfile(uid: String): Result<UserProfile?> = Result.success(null)
}
