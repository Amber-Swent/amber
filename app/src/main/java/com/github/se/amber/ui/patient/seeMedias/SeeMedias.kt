// this code was written with the aid of AI

package com.github.se.amber.ui.patient.seeMedias

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.PatientTab.Companion.patientTabs
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.patient.DisplayPatientButtonWithIcon
import com.github.se.amber.ui.patient.DisplayPatientTitle
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PADDING_ENORMOUS
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.WEIGHT_ONE
import com.github.se.amber.ui.theme.lightOrange

/**
 * Displays the See Medias menu for text, picture, and audio stories.
 *
 * @param viewModel view model that stores the see medias screen state.
 * @param onSeePictureClick callback invoked when see pictures is selected.
 * @param onSeeTextClick callback invoked when see text is selected.
 * @param onSeeAudioClick callback invoked when see audio is selected.
 * @param onTabSelected callback invoked when one of the tab in the bottomBarMenu is selected
 */
@Composable
fun SeeMediasScreen(
    viewModel: SeeMediasViewModel = viewModel(),
    onSeePictureClick: () -> Unit = {},
    onSeeTextClick: () -> Unit = {},
    onSeeAudioClick: () -> Unit = {},
    onTabSelected: (Tab) -> Unit = { tab -> },
) {
  val uiState by viewModel.uiState.collectAsState()

  Scaffold(
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = PatientTab.SeeMedias,
            onTabSelected = onTabSelected,
            tabs = patientTabs,
            Modifier.testTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU),
        )
      },
      content = { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = PADDING_MEDIUM)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PADDING_MEDIUM),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          DisplayPatientTitle(
              title = "See Medias"
          )
          DisplayPatientButtonWithIcon(
              text = "See Pictures",
              icon = Icons.Outlined.PhotoCamera,
              description = "Picture",
              onClick = onSeePictureClick
          )
          DisplayPatientButtonWithIcon(
              text = "See Texts",
              icon = Icons.Outlined.Description,
              description = "Text",
              onClick = onSeeTextClick,
          )
          DisplayPatientButtonWithIcon(
              text = "See Audio",
              icon = Icons.Outlined.Mic,
              description = "Audios",
              onClick = onSeeAudioClick,
          )
        }
      },
  )
}

/** Preview of [SeeMediasScreen] using the Amber application theme. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SeeMediasScreenPreview() {
  AmberAppTheme(dynamicColor = false) { SeeMediasScreen() }
}
