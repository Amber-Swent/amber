// Written by Lou-Anne Maier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Alaabrine, with assistance from Cursor.
// Modified by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

  // Configuration-aware resources, so the toast follows the app language
  val resources = LocalResources.current

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
                  title = stringResource(R.string.caregiverHomeTitle),
                  modifier = Modifier.testTag(CaregiverHomeTestTags.TITLE),
              )
            }
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
                Modifier.fillMaxSize()
                    .padding(paddingValues)
                    .padding(
                        horizontal = CAREGIVER_PADDING_MEDIUM,
                        vertical = CAREGIVER_PADDING_SMALL,
                    ),
            verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
              text =
                  if (uiState.patient.uid.isNotBlank()) {
                    stringResource(
                        R.string.caregiverHomeWelcomePatient,
                        uiState.patient.person.firstName,
                    )
                  } else {
                    stringResource(R.string.caregiverHomeWelcomeNoPatient)
                  },
              modifier = Modifier.fillMaxWidth().testTag(CaregiverHomeTestTags.WELCOME_TEXT),
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
