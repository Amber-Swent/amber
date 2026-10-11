package com.github.se.amber.ui.caregiver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.ui.caregiver.CaregiverHomeTestTags.WELCOME_TEXT
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.BottomNavigationTestTags
import com.github.se.amber.ui.navigation.CaregiverTab
import com.github.se.amber.ui.navigation.NavigationActions
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_SMALL

object CaregiverHomeTestTags {
  const val TITLE = "caregiver_home_title"
  const val WELCOME_TEXT = "caregiver_home_welcome_text"
}

/**
 * Displays the caregiver home screen
 *
 * It will evolve over the coming weeks. This basic structure is here to enable the implementation
 * of navigation.
 *
 * @param viewModel ViewModel providing the patient information displayed on the screen.
 * @param goBack Callback invoked when the user selects the back action in the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaregiverHomeScreen(
    viewModel: HomeViewModel = viewModel(),
    navigationActions: NavigationActions? = null,
    goBack: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsState()
  Scaffold(
      topBar = {
        TopNavigationMenu(
            modifier = Modifier.testTag(TopNavigationMenuTestTags.ROOT),
            action = TopNavigationAction.CHECKOUT,
            onBackClick = goBack,
        )
        // TODO: DisplayCaregiverTitle("Home", Modifier.testTag(HomeTestTags.TITLE))
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = CaregiverTab.Home,
            onTabSelected = { tab -> navigationActions?.navigateTo(tab.destination) },
            tabs = CaregiverTab.caregiverTabs,
            modifier = Modifier.testTag(BottomNavigationTestTags.BOTTOM_NAVIGATION_MENU),
        )
      },
      content = { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(PADDING_MEDIUM),
            verticalArrangement = Arrangement.spacedBy(PADDING_SMALL),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Spacer(modifier = Modifier.height(PADDING_SMALL))
          Text(
              text = "Welcome ! You are with " + uiState.patient.person.firstName,
              modifier = Modifier.testTag(WELCOME_TEXT),
              color = Color.Black,
              style = MaterialTheme.typography.bodyLarge,
              textAlign = TextAlign.Center,
          )
        }
      },
  )
}
