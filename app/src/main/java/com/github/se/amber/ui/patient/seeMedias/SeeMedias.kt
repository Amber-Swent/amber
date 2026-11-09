// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.patient.seeMedias

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.PatientTab.Companion.patientTabs
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.patient.DisplayPatientButtonWithIcon
import com.github.se.amber.ui.patient.DisplayPatientTitle
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PATIENT_PADDING_SMALL

object SeeMediasTestTags {
  const val TITLE = "see_medias_title"
  const val PICTURES_BUTTON = "see_medias_pictures_button"
  const val TEXTS_BUTTON = "see_medias_texts_button"
  const val AUDIOS_BUTTON = "see_medias_audios_button"
}

/**
 * Displays the See Medias menu for text, picture, and audio stories.
 *
 * @param viewModel view model that stores the see medias screen state.
 * @param onSeePictureClick callback invoked when see pictures is selected.
 * @param onSeeTextClick callback invoked when see text is selected.
 * @param onSeeAudioClick callback invoked when see audio is selected.
 * @param onTabSelected callback invoked when one of the tab in the bottomBarMenu is selected
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeeMediasScreen(
    viewModel: SeeMediasViewModel = viewModel(),
    onSeePictureClick: () -> Unit = {},
    onSeeTextClick: () -> Unit = {},
    onSeeAudioClick: () -> Unit = {},
    onTabSelected: (Tab) -> Unit = {},
) {

  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              DisplayPatientTitle(
                  title = stringResource(R.string.seeMediasTitle),
                  testTag = SeeMediasTestTags.TITLE,
              )
            }
        )
      },
      bottomBar = {
        BottomNavigationMenu(
            Modifier.testTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU),
            selectedTab = PatientTab.SeeMedias,
            onTabSelected = onTabSelected,
            tabs = patientTabs,
        )
      },
      content = { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = PATIENT_PADDING_SMALL)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          DisplayPatientButtonWithIcon(
              text = stringResource(R.string.seeMediasPictures),
              icon = Icons.Outlined.PhotoCamera,
              description = stringResource(R.string.seeMediasPicturesIcon),
              onClick = onSeePictureClick,
              testTag = SeeMediasTestTags.PICTURES_BUTTON,
          )
          DisplayPatientButtonWithIcon(
              text = stringResource(R.string.seeMediasTexts),
              icon = Icons.Outlined.Description,
              description = stringResource(R.string.seeMediasTextsIcon),
              onClick = onSeeTextClick,
              testTag = SeeMediasTestTags.TEXTS_BUTTON,
          )
          DisplayPatientButtonWithIcon(
              text = stringResource(R.string.seeMediasAudios),
              icon = Icons.Outlined.Mic,
              description = stringResource(R.string.seeMediasAudiosIcon),
              onClick = onSeeAudioClick,
              testTag = SeeMediasTestTags.AUDIOS_BUTTON,
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
