// this code was written with the aid of AI
package com.github.se.amber.ui.patient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PADDING_BIG
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.orange

object PatientHomeScreenTestTags {
  const val TITLE = "home_title"
  const val GREETINGS = "home_greetings"
}

@Composable
fun PatientHomeScreen(
    homeViewModel: PatientHomeViewModel = viewModel(),
    checkout: () -> Unit,
    onTabSelected: (Tab) -> Unit,
) {
  val uiState by homeViewModel.uiState.collectAsState()

  Scaffold(
      topBar = {
        Column(modifier = Modifier.fillMaxWidth().padding(PADDING_SMALL)) {
          TopNavigationMenu(
              modifier = Modifier.testTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU),
              action = TopNavigationAction.CHECKOUT,
              onBackClick = checkout,
          )
          Spacer(modifier = Modifier.height(PADDING_SMALL))
          Text(
              modifier = Modifier.fillMaxWidth().testTag(PatientHomeScreenTestTags.TITLE),
              text = "Home",
              textAlign = TextAlign.Center,
              color = orange,
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
          )
          Spacer(modifier = Modifier.height(PADDING_BIG))
        }
      },
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
                Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PADDING_SMALL),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Spacer(modifier = Modifier.height(PADDING_SMALL))
          Text(
              modifier = Modifier.fillMaxWidth().testTag(PatientHomeScreenTestTags.GREETINGS),
              text = "Hello " + uiState.patientProfile.person.firstName + " !",
              textAlign = TextAlign.Center,
              color = Color.Black,
              style = MaterialTheme.typography.headlineMedium,
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
        checkout = {},
        onTabSelected = {},
    )
  }
}
