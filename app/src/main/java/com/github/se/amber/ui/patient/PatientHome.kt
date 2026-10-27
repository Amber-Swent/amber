// this code was written with the aid of AI
// Modified with assistance from GitHub Copilot.
package com.github.se.amber.ui.patient

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PATIENT_PADDING_MEDIUM

object PatientHomeTestTags {
  const val GREETINGS = "home_greetings"
}

@Composable
fun PatientHomeScreen(
    homeViewModel: PatientHomeViewModel = viewModel(),
    onTabSelected: (Tab) -> Unit = {},
) {
  val context = LocalContext.current
  val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()

  // Show error message
  LaunchedEffect(uiState.errorMsg) {
    uiState.errorMsg?.let { message ->
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
      homeViewModel.clearErrorMsg()
    }
  }

  Scaffold(
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = PatientTab.Home,
            onTabSelected = onTabSelected,
            tabs = PatientTab.patientTabs,
            modifier = Modifier.testTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU),
        )
      },
      content = { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(top = PATIENT_PADDING_MEDIUM),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          DisplayPatientTitle(title = "Home")
          Text(
              modifier = Modifier.fillMaxWidth().testTag(PatientHomeTestTags.GREETINGS),
              text = "Hello ${uiState.patientProfile.person.firstName} !",
              textAlign = TextAlign.Center,
              color = MaterialTheme.colorScheme.onBackground,
              style = MaterialTheme.typography.bodyLarge,
          )
        }
      },
  )
}

@Preview(showBackground = true)
@Composable
fun PatientHomeScreenPreview() {
  AmberAppTheme(dynamicColor = false) {
    PatientHomeScreen(
        onTabSelected = {},
    )
  }
}
