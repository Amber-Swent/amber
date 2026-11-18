// Written by Yuhan YANG, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Lou-Anne Maier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by MeiliV, with assistance from OpenAI Codex
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.caregiver.parameters.createInvitation

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.data.invitation.InvitationStatus
import com.github.se.amber.ui.caregiver.DisplayCaregiverButton
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.CaregiverTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_HEIGHT_BIG
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_WIDTH_ENORMOUS
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_BIG
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_ENORMOUS
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.DIVIDER_THICKNESS
import com.github.se.amber.ui.theme.ROUND_CORNER
import com.github.se.amber.ui.theme.WEIGHT_ONE
import java.text.DateFormat
import java.util.Date
import java.util.Locale

object CreateInvitationTestTags {
  const val TITLE = "create_invitation_title"
  const val CREATE_CARD = "create_invitation_card"
  const val HINT_BOX = "create_invitation_hint_box"
  const val USAGE_TEXT = "create_invitation_usage_text"
  const val CODE_LIST = "create_invitation_code_list"
  const val ERROR_MESSAGE = "create_invitation_error_message"

  // one per invitation, its text starting with the code
  fun codeRow(code: String) = "create_invitation_code_row_$code"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateInvitationScreen(
    viewModel: CreateInvitationViewModel = viewModel(),
    onTabSelected: (Tab) -> Unit = { _ -> },
    checkout: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val locale = LocalConfiguration.current.locales[0]
  val colors = MaterialTheme.colorScheme
  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Column(
                  modifier = Modifier.fillMaxWidth().padding(CAREGIVER_PADDING_SMALL),
              ) {
                TopNavigationMenu(
                    modifier = Modifier.testTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU),
                    action = TopNavigationAction.CANCEL,
                    onBackClick = checkout,
                )
                DisplayCaregiverTitle(
                    Modifier.testTag(CreateInvitationTestTags.TITLE),
                    stringResource(R.string.createInvitationTitle),
                )
              }
            }
        )
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = CaregiverTab.Setting,
            onTabSelected = onTabSelected,
            tabs = CaregiverTab.caregiverTabs,
            modifier = Modifier.testTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU),
        )
      },
      content = { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = CAREGIVER_PADDING_BIG)
                    .padding(top = CAREGIVER_PADDING_BIG),
        ) {
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL),
              verticalAlignment = Alignment.Top,
          ) {
            DisplayCaregiverButton(
                modifier = Modifier.weight(WEIGHT_ONE),
                onClick = viewModel::createInvitation,
                enabled = !uiState.isCreating,
                icon = Icons.Outlined.GroupAdd,
                description = stringResource(R.string.createInvitationButton),
                text = stringResource(R.string.createInvitationButton),
                height = CAREGIVER_BUTTON_HEIGHT_BIG,
                widthPercentage = CAREGIVER_BUTTON_WIDTH_ENORMOUS,
                buttonTestTag = CreateInvitationTestTags.CREATE_CARD,
                iconAboveText = true,
            )
            Text(
                text =
                    uiState.generatedCode ?: stringResource(R.string.createInvitationPlaceholder),
                modifier =
                    Modifier.weight(WEIGHT_ONE)
                        .border(
                            DIVIDER_THICKNESS,
                            colors.outline,
                            RoundedCornerShape(ROUND_CORNER),
                        )
                        .padding(CAREGIVER_PADDING_SMALL)
                        .testTag(CreateInvitationTestTags.HINT_BOX),
                color = colors.onBackground,
                style = MaterialTheme.typography.bodyMedium,
            )
          }
          Text(
              text = stringResource(R.string.createInvitationUsage),
              modifier =
                  Modifier.fillMaxWidth()
                      .padding(top = CAREGIVER_PADDING_ENORMOUS)
                      .testTag(CreateInvitationTestTags.USAGE_TEXT),
              color = colors.onBackground,
              style = MaterialTheme.typography.bodyMedium,
          )
          uiState.errorMsgRes?.let { errorMsgRes ->
            Text(
                text = stringResource(errorMsgRes),
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(top = CAREGIVER_PADDING_SMALL)
                        .testTag(CreateInvitationTestTags.ERROR_MESSAGE),
                color = colors.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
          }
          Column(
              modifier =
                  Modifier.padding(top = CAREGIVER_PADDING_ENORMOUS)
                      .fillMaxWidth()
                      .weight(WEIGHT_ONE)
                      .border(
                          DIVIDER_THICKNESS,
                          colors.outline,
                          RoundedCornerShape(ROUND_CORNER),
                      )
                      .verticalScroll(rememberScrollState())
                      .padding(top = CAREGIVER_PADDING_MEDIUM)
                      .testTag(CreateInvitationTestTags.CODE_LIST),
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
                text = stringResource(R.string.createInvitationListHeader),
                color = colors.onBackground,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            uiState.invitations.forEach { invitation ->
              Text(
                  text =
                      stringResource(
                          R.string.createInvitationListRow,
                          invitation.code,
                          formatCreatedAt(invitation.createdAt, locale),
                          stringResource(invitation.status.labelRes()),
                      ),
                  modifier = Modifier.testTag(CreateInvitationTestTags.codeRow(invitation.code)),
                  color = colors.onBackground,
                  style = MaterialTheme.typography.bodyMedium,
                  textAlign = TextAlign.Center,
              )
            }
          }
        }
      },
  )
}

/**
 * Formats when an invitation was created, in the app language.
 *
 * Both arguments are required. [Locale.getDefault] does not follow the app language, and a creation
 * time always comes from the invitation.
 *
 * @param createdAtMillis the creation time, in milliseconds since the epoch.
 * @param locale the app locale, from `LocalConfiguration.current.locales[0]`.
 */
internal fun formatCreatedAt(createdAtMillis: Long, locale: Locale): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
        .format(Date(createdAtMillis))

/** The status shown in the invitation list, in the app language. */
@StringRes
internal fun InvitationStatus.labelRes(): Int =
    when (this) {
      InvitationStatus.ACTIVE -> R.string.createInvitationStatusActive
      InvitationStatus.EXPIRED -> R.string.createInvitationStatusExpired
      InvitationStatus.USED -> R.string.createInvitationStatusUsed
    }

/** Preview of [CreateInvitationScreen] using the Amber application theme. */
@Preview(showBackground = true, showSystemUi = false, name = "Create invitation light")
@Composable
internal fun CreateInvitationScreenPreview() {
  AmberAppTheme(dynamicColor = false) { CreateInvitationScreen() }
}

/** Preview of [CreateInvitationScreen] in the current dark color scheme. */
@Preview(
    showBackground = true,
    showSystemUi = false,
    name = "Create invitation dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
internal fun CreateInvitationDarkPreview() {
  AmberAppTheme(dynamicColor = false, darkTheme = true) { CreateInvitationScreen() }
}
