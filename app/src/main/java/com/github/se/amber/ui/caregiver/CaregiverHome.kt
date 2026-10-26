/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 * Modified by Alaabrine, with assistance from Cursor.
 */
package com.github.se.amber.ui.caregiver

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.ui.caregiver.CaregiverHomeTestTags.WELCOME_TEXT
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.CaregiverTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL

object CaregiverHomeTestTags {
  const val TITLE = "caregiver_home_title"
  const val WELCOME_TEXT = "caregiver_home_welcome_text"
}

/**
 * Displays the caregiver home screen.
 *
 * It will evolve over the coming weeks. This basic structure is here to enable the implementation
 * of navigation. Sign-out is available from the parameters screen.
 *
 * @param viewModel ViewModel providing the patient information displayed on the screen.
 * @param onTabSelected Callback invoked when the user selects a bottom navigation tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaregiverHomeScreen(
    viewModel: CaregiverHomeViewModel = viewModel(),
    onTabSelected: (Tab) -> Unit = {},
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(uiState.errorMsg) {
    uiState.errorMsg?.let { message ->
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
      viewModel.clearErrorMsg()
    }
  }

  Scaffold(
      topBar = {
        Text(
            text = "Home",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        top = CAREGIVER_PADDING_SMALL,
                        bottom = CAREGIVER_PADDING_MEDIUM,
                    )
                    .testTag(CaregiverHomeTestTags.TITLE),
        )
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = CaregiverTab.Home,
            onTabSelected = onTabSelected,
            tabs = CaregiverTab.caregiverTabs,
            modifier = Modifier.testTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU),
        )
      },
      content = { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize().padding(paddingValues).padding(CAREGIVER_PADDING_MEDIUM),
            verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
              text = "Welcome ! You are with " + uiState.patient.person.firstName,
              modifier = Modifier.testTag(WELCOME_TEXT),
              color = MaterialTheme.colorScheme.onBackground,
              style = MaterialTheme.typography.bodyLarge,
              textAlign = TextAlign.Center,
          )
        }
      },
  )
}

/** Preview of [CaregiverHomeScreen] using the Amber application theme. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CaregiverHomeScreenPreview() {
  AmberAppTheme(dynamicColor = false) { CaregiverHomeScreen() }
}
