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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu

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
  Scaffold(
      topBar = { TopNavigationMenu(Modifier, TopNavigationAction.CHECKOUT, goBack) },
      // TODO: unExtract the following lines once Navigation is implement
      //      bottomBar = { BottomNavigationMenu(selectedTab = CaregiverTab.Home,
      //          onTabSelected = { tab -> navigationActions?.navigateTo(tab.destination) }, )
      //      },
      content = { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Spacer(modifier = Modifier.height(8.dp))

          Text(
              text = "Welcome ! You are with " + viewModel.getPatientName(),
              color = Color.Black,
              style = MaterialTheme.typography.bodyLarge,
              textAlign = TextAlign.Center,
          )

          Spacer(modifier = Modifier.height(16.dp))
        }
      },
  )
}
