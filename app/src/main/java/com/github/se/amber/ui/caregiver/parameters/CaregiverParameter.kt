/* Written by Yuhan YANG, with assistance from
 * Cursor.
 */
package com.github.se.amber.ui.caregiver.parameters

import android.content.res.Configuration
import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthUser
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.caregiver.upload.DisplayButton
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.CaregiverTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.INVITATION_BUTTON_HEIGHT
import com.github.se.amber.ui.theme.INVITATION_BUTTON_WIDTH
import com.github.se.amber.ui.theme.PADDING_ENORMOUS
import com.github.se.amber.ui.theme.PADDING_SMALL

object CaregiverParametersTestTags {
  const val TITLE = "caregiver_parameters_title"
  const val CREATE_INVITATION_BUTTON = "caregiver_parameters_create_invitation_button"
  const val ENTER_INVITATION_BUTTON = "caregiver_parameters_enter_invitation_button"
  const val INVITATION_HINT = "caregiver_parameters_invitation_hint"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaregiverParameterScreen(
    viewModel: CaregiverParameterViewModel = viewModel(),
    credentialManager: CredentialManager = CredentialManager.create(LocalContext.current),
    onSignedOut: () -> Unit = {},
    onTabSelected: (Tab) -> Unit = { tab -> },
    onCreateInvitationClick: () -> Unit = {},
    onEnterInvitationClick: () -> Unit = {},
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsState()

  LaunchedEffect(uiState.errorMsg) {
    uiState.errorMsg?.let { message ->
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
      viewModel.clearErrorMsg()
    }
  }

  LaunchedEffect(uiState.signedOut) {
    if (uiState.signedOut) {
      onSignedOut()
    }
  }

  Scaffold(
      topBar = {
        Column(modifier = Modifier.fillMaxWidth().padding(PADDING_SMALL)) {
          TopNavigationMenu(
              modifier = Modifier.testTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU),
              action = TopNavigationAction.CHECKOUT,
              onBackClick = { viewModel.signOut(credentialManager) },
          )
          DisplayCaregiverTitle("Parameters", Modifier.testTag(CaregiverParametersTestTags.TITLE))
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
                modifier = Modifier.testTag(CaregiverParametersTestTags.CREATE_INVITATION_BUTTON),
                icon = Icons.Outlined.PersonAdd,
                text = "Create Invitation",
                description = "Create Invitation",
                onClick = onCreateInvitationClick,
            )

            DisplayButton(
                modifier = Modifier.testTag(CaregiverParametersTestTags.ENTER_INVITATION_BUTTON),
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
                      .testTag(CaregiverParametersTestTags.INVITATION_HINT),
              color = Color.Black,
              style = MaterialTheme.typography.bodyLarge,
              textAlign = TextAlign.Center,
          )
        }
      },
  )
}

/** Preview of [CaregiverParameterScreen] using the Amber application theme. */
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_TYPE_NORMAL)
@Composable
internal fun CaregiverParameterScreenPreview() {
  val fakeAuthRepository =
      object : AuthRepository {
        override val currentUser: AuthUser? = null

        override suspend fun signInWithGoogle(credential: Credential): Result<AuthUser> =
            Result.failure(UnsupportedOperationException())

        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
      }
  val viewModel = remember { CaregiverParameterViewModel(authRepository = fakeAuthRepository) }
  AmberAppTheme(dynamicColor = false) { CaregiverParameterScreen(viewModel = viewModel) }
}
