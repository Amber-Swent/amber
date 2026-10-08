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
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_SMALL

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
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    // TODO : create NavigationActions
    // navigationActions: NavigationActions? = null,

    goBack: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsState()
  Scaffold(
      topBar = { TopNavigationMenu(Modifier, TopNavigationAction.CHECKOUT, goBack) },
      // TODO: unExtract the following lines once Navigation is implement
      //      bottomBar = { BottomNavigationMenu(selectedTab = CaregiverTab.Home,
      //          onTabSelected = { tab -> navigationActions?.navigateTo(tab.destination) }, )
      //      },
      content = { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(PADDING_MEDIUM),
            verticalArrangement = Arrangement.spacedBy(PADDING_SMALL),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Spacer(modifier = Modifier.height(PADDING_SMALL))
          Text(
              text = "Welcome ! You are with " + uiState.patient.person.firstName,
              color = Color.Black,
              style = MaterialTheme.typography.bodyLarge,
              textAlign = TextAlign.Center,
          )
        }
      },
  )
}
