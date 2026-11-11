// Written by Yuhan YANG, with assistance from Cursor.
// Modify by Lou-Anne Maier, with assistance from Claude Code.
// Modified by Alaabrine, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.caregiver.parameters

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.caregiver.upload.DisplayButton
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.CaregiverTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_ENORMOUS
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM

/** Test tags of [CaregiverParameterScreen]. */
object CaregiverParametersTestTags {
  const val TITLE = "CaregiverParameter_test_tag_title"
  const val CREATE_CARE_CIRCLE_BUTTON = "CaregiverParameter_test_tag_create_care_circle_button"
  const val CREATE_INVITATION_BUTTON = "CaregiverParameter_test_tag_create_invitation_button"
  const val ENTER_INVITATION_BUTTON = "CaregiverParameter_test_tag_enter_invitation_button"
  const val SIGN_OUT_BUTTON = "CaregiverParameter_test_tag_sign_out_button"
  const val DELETE_ACCOUNT_BUTTON = "CaregiverParameter_test_tag_delete_account_button"
  const val INVITATION_HINT = "CaregiverParameter_test_tag_invitation_hint"
}

/**
 * Displays the caregiver parameters screen.
 *
 * Invitation actions stay on this screen. Sign-out is confirmed on a shared screen reached through
 * [onSignOutClick].
 *
 * @param viewModel ViewModel providing the UI state of the screen.
 * @param onTabSelected Callback invoked when the user selects a bottom navigation tab.
 * @param onCreateCareCircleClick Callback invoked when the user creates a new care circle.
 * @param onCreateInvitationClick Callback invoked when the user creates an invitation.
 * @param onEnterInvitationClick Callback invoked when the user enters an invitation.
 * @param onSignOutClick Callback invoked when the user opens the sign-out confirmation screen.
 * @param onDeleteAccountClick Callback invoked when the user opens account deletion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaregiverParameterScreen(
    viewModel: CaregiverParameterViewModel = viewModel(),
    onTabSelected: (Tab) -> Unit = {},
    onCreateCareCircleClick: () -> Unit = {},
    onCreateInvitationClick: () -> Unit = {},
    onEnterInvitationClick: () -> Unit = {},
    onSignOutClick: () -> Unit = {},
    onDeleteAccountClick: () -> Unit = {},
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  // Configuration-aware resources, so the toast follows the app language
  val resources = LocalResources.current
  val createCareCircleLabel = stringResource(R.string.caregiverParameterCreateCareCircle)
  val createInvitationLabel = stringResource(R.string.caregiverParameterCreateInvitation)
  val enterInvitationLabel = stringResource(R.string.caregiverParameterEnterInvitation)
  val signOutLabel = stringResource(R.string.caregiverParameterSignOut)
  val deleteAccountLabel = stringResource(R.string.caregiverParameterDeleteAccount)

  LaunchedEffect(uiState.errorMsgRes) {
    uiState.errorMsgRes?.let { errorMsgRes ->
      Toast.makeText(context, resources.getString(errorMsgRes), Toast.LENGTH_SHORT).show()
      viewModel.clearErrorMsg()
    }
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              DisplayCaregiverTitle(
                  modifier = Modifier.testTag(CaregiverParametersTestTags.TITLE),
                  title = stringResource(R.string.caregiverParameterTitle),
              )
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
                    .verticalScroll(rememberScrollState())
                    .padding(top = CAREGIVER_PADDING_ENORMOUS)
                    .padding(horizontal = CAREGIVER_PADDING_MEDIUM),
            verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_ENORMOUS),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          DisplayButton(
              modifier = Modifier.testTag(CaregiverParametersTestTags.CREATE_CARE_CIRCLE_BUTTON),
              icon = Icons.Outlined.Add,
              text = createCareCircleLabel,
              description = createCareCircleLabel,
              onClick = onCreateCareCircleClick,
          )
          DisplayButton(
              modifier = Modifier.testTag(CaregiverParametersTestTags.CREATE_INVITATION_BUTTON),
              icon = Icons.Outlined.PersonAdd,
              text = createInvitationLabel,
              description = createInvitationLabel,
              onClick = onCreateInvitationClick,
          )
          DisplayButton(
              modifier = Modifier.testTag(CaregiverParametersTestTags.ENTER_INVITATION_BUTTON),
              icon = Icons.Outlined.Mail,
              text = enterInvitationLabel,
              description = enterInvitationLabel,
              onClick = onEnterInvitationClick,
          )
          DisplayButton(
              modifier = Modifier.testTag(CaregiverParametersTestTags.SIGN_OUT_BUTTON),
              icon = Icons.AutoMirrored.Outlined.Logout,
              text = signOutLabel,
              description = signOutLabel,
              onClick = onSignOutClick,
          )
          DisplayButton(
              modifier = Modifier.testTag(CaregiverParametersTestTags.DELETE_ACCOUNT_BUTTON),
              icon = Icons.Outlined.Delete,
              text = deleteAccountLabel,
              description = deleteAccountLabel,
              onClick = onDeleteAccountClick,
          )

          // Tells a caregiver how to add someone else: it names the Create Invitation button on
          // this screen and says to send the code it creates.
          Text(
              text =
                  stringResource(R.string.caregiverParameterInvitationHint, createInvitationLabel),
              modifier =
                  Modifier.fillMaxWidth()
                      .padding(bottom = CAREGIVER_PADDING_ENORMOUS)
                      .testTag(CaregiverParametersTestTags.INVITATION_HINT),
              color = MaterialTheme.colorScheme.onBackground,
              style = MaterialTheme.typography.bodyLarge,
              textAlign = TextAlign.Center,
          )
        }
      },
  )
}

/** Preview of [CaregiverParameterScreen], including the create-care-circle button. */
@Preview(name = "Caregiver parameters", showBackground = true, showSystemUi = true)
@Composable
internal fun CaregiverParameterScreenPreview() {
  val viewModel = remember { CaregiverParameterViewModel() }
  AmberAppTheme(dynamicColor = false) { CaregiverParameterScreen(viewModel = viewModel) }
}
