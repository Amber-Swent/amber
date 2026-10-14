/* Written by Yuhan YANG, with assistance from
 * Cursor.
 */
package com.github.se.amber.ui.parameters

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.caregiver.upload.DisplayButton
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.CaregiverTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PADDING_ENORMOUS
import com.github.se.amber.ui.theme.PADDING_SMALL

object ParametersHomeTestTags {
  const val TITLE = "parameters_home_title"
  const val CREATE_INVITATION_BUTTON = "parameters_home_create_invitation_button"
  const val ENTER_INVITATION_BUTTON = "parameters_home_enter_invitation_button"
  const val INVITATION_HINT = "parameters_home_invitation_hint"
}

/** Width of the create and enter invitation buttons, in dp. */
private val invitationButtonWidth = 320.dp

/** Height of the create and enter invitation buttons, in dp. */
private val invitationButtonHeight = 100.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParametersHomeScreen(
    onTabSelected: (Tab) -> Unit = { tab -> },
    onCreateInvitationClick: () -> Unit = {},
    onEnterInvitationClick: () -> Unit = {},
    checkout: () -> Unit = {},
) {
  Scaffold(
      topBar = {
        Column(modifier = Modifier.fillMaxWidth().padding(PADDING_SMALL)) {
          TopNavigationMenu(
              modifier = Modifier.testTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU),
              action = TopNavigationAction.CHECKOUT,
              onBackClick = checkout,
          )
          DisplayCaregiverTitle("Parameters", Modifier.testTag(ParametersHomeTestTags.TITLE))
        }
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = CaregiverTab.Setting,
            onTabSelected = onTabSelected,
            tabs = CaregiverTab.caregiverTabs,
        )
      },
      content = { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
          Column(
              modifier =
                  Modifier.fillMaxWidth()
                      .align(Alignment.TopCenter)
                      .padding(top = PADDING_ENORMOUS),
              verticalArrangement = Arrangement.spacedBy(PADDING_ENORMOUS),
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            DisplayButton(
                modifier =
                    Modifier.width(invitationButtonWidth)
                        .height(invitationButtonHeight)
                        .testTag(ParametersHomeTestTags.CREATE_INVITATION_BUTTON),
                icon = Icons.Outlined.PersonAdd,
                text = "Create Invitation",
                description = "Create Invitation",
                onClick = onCreateInvitationClick,
            )

            DisplayButton(
                modifier =
                    Modifier.width(invitationButtonWidth)
                        .height(invitationButtonHeight)
                        .testTag(ParametersHomeTestTags.ENTER_INVITATION_BUTTON),
                icon = Icons.Outlined.Mail,
                text = "Enter Invitation",
                description = "Enter Invitation",
                onClick = onEnterInvitationClick,
            )
          }

          Text(
              text = "Invitation code is needed to add families/patient into the family circle",
              modifier =
                  Modifier.align(Alignment.BottomCenter)
                      .fillMaxWidth()
                      .padding(horizontal = PADDING_ENORMOUS)
                      .padding(bottom = PADDING_ENORMOUS)
                      .testTag(ParametersHomeTestTags.INVITATION_HINT),
              color = Color.Black,
              style = MaterialTheme.typography.bodyLarge,
              textAlign = TextAlign.Center,
          )
        }
      },
  )
}

/** Preview of [ParametersHomeScreen] using the Amber application theme. */
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun ParametersHomeScreenPreview() {
  AmberAppTheme(dynamicColor = false) { ParametersHomeScreen() }
}
