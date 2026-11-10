// Written by MeiliV, with assistance from OpenAI Codex.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.patient.seeMedias.audios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.patient.DisplayPatientTitle
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PATIENT_PADDING_MEDIUM

/** Test tags for the title and empty message on [SeeAudiosScreen]. */
object SeeAudiosTestTags {
  const val NO_AUDIOS = "see_audios_no_audios"
  const val TITLE = "see_audios_title"
}

/**
 * Displays the patient's audio screen. Only the empty state is available for now.
 *
 * @param audioViewModel provides the screen's UI state.
 * @param onBackClick called when the top navigation action is selected.
 * @param onTabSelected called when a bottom navigation tab is selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeeAudiosScreen(
    audioViewModel: SeeAudiosViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    onTabSelected: (Tab) -> Unit = {},
) {
  val uiState by audioViewModel.uiState.collectAsStateWithLifecycle()

  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Column {
                TopNavigationMenu(
                    modifier = Modifier.testTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU),
                    action = TopNavigationAction.CANCEL,
                    onBackClick = onBackClick,
                )
                DisplayPatientTitle(
                    title = stringResource(R.string.seeAudiosTitle),
                    testTag = SeeAudiosTestTags.TITLE,
                )
              }
            }
        )
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = PatientTab.SeeMedias,
            onTabSelected = onTabSelected,
            tabs = PatientTab.patientTabs,
            modifier = Modifier.testTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU),
        )
      },
      content = { paddingValues ->
        when (uiState) {
          SeeAudiosUIState.Empty -> {
            Box(
                modifier =
                    Modifier.fillMaxSize().padding(paddingValues).padding(PATIENT_PADDING_MEDIUM),
                contentAlignment = Alignment.Center,
            ) {
              Text(
                  modifier = Modifier.testTag(SeeAudiosTestTags.NO_AUDIOS),
                  text = stringResource(R.string.seeAudiosEmpty),
                  textAlign = TextAlign.Center,
                  color = MaterialTheme.colorScheme.onBackground,
                  style = MaterialTheme.typography.titleLarge,
              )
            }
          }
        }
      },
  )
}

/** Preview of the empty audio screen using the Amber theme. */
@Preview(showBackground = true)
@Composable
fun SeeAudiosScreenPreview() {
  AmberAppTheme { SeeAudiosScreen() }
}
