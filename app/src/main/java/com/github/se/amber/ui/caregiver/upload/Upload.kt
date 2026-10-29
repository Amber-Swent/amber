/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.caregiver.upload.UploadTestTags.AUDIO_BUTTON
import com.github.se.amber.ui.caregiver.upload.UploadTestTags.PICTURE_BUTTON
import com.github.se.amber.ui.caregiver.upload.UploadTestTags.TEXT_BUTTON
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.CaregiverTab
import com.github.se.amber.ui.navigation.CaregiverTab.Companion.caregiverTabs
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.lightOrange

object UploadTestTags {
  const val TITLE = "upload_title"
  const val TEXT_BUTTON = "upload_text_button"
  const val PICTURE_BUTTON = "upload_picture_button"
  const val AUDIO_BUTTON = "upload_audio_button"
}

/**
 * Displays the upload menu for text, picture, and audio stories.
 *
 * Note: The ViewModel is currently useless. It's here for possible upcoming implementation,
 * functionality or features.
 *
 * @param viewModel view model that stores the upload screen state.
 * @param onUploadTextClick callback invoked when text upload is selected.
 * @param onUploadPictureClick callback invoked when picture upload is selected.
 * @param onUploadAudioClick callback invoked when audio upload is selected.
 * @param checkout callback invoked when the user selects the checkout action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    viewModel: UploadViewModel = viewModel(),
    onUploadTextClick: () -> Unit = {},
    onUploadPictureClick: () -> Unit = {},
    onUploadAudioClick: () -> Unit = {},
    onTabSelected: (Tab) -> Unit = { tab -> },
    checkout: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsState()
  val errorMessage = uiState.errorMsg
  val context = LocalContext.current

  LaunchedEffect(errorMessage) {
    if (errorMessage != null) {
      Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
      viewModel.clearErrorMsg()
    }
  }

  Scaffold(
      topBar = {
        Column(modifier = Modifier.fillMaxWidth().padding(PADDING_SMALL)) {
          TopNavigationMenu(
              modifier = Modifier.testTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU),
              action = TopNavigationAction.CHECKOUT,
              onBackClick = checkout,
          )
          DisplayCaregiverTitle("Upload", modifier = Modifier.testTag(UploadTestTags.TITLE))
        }
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = CaregiverTab.Upload,
            onTabSelected = onTabSelected,
            tabs = caregiverTabs,
            modifier = Modifier.testTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU),
        )
      },
      content = { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PADDING_SMALL),
        ) {
          DisplayButton(
              modifier = Modifier.testTag(TEXT_BUTTON),
              icon = Icons.Outlined.Description,
              text = "Upload Text",
              description = "Text",
              onClick = { onUploadTextClick() },
          )

          DisplayButton(
              modifier = Modifier.testTag(PICTURE_BUTTON),
              icon = Icons.Outlined.PhotoCamera,
              text = "Upload Picture",
              description = "Picture",
              onClick = { onUploadPictureClick() },
          )

          DisplayButton(
              modifier = Modifier.testTag(AUDIO_BUTTON),
              icon = Icons.Outlined.Mic,
              text = "Upload Audio",
              description = "Audio",
              onClick = { onUploadAudioClick() },
          )
        }
      },
  )
}

/**
 * Displays a button with an icon, label, and click action.
 *
 * @param icon icon displayed on the button.
 * @param text label displayed on the button.
 * @param description content description for the icon.
 * @param onClick callback invoked when the button is selected.
 */
@Composable
fun DisplayButton(
    modifier: Modifier,
    icon: ImageVector,
    text: String,
    description: String,
    onClick: () -> Unit,
) {
  Button(
      onClick = onClick,
      modifier = modifier.fillMaxWidth(),
      colors = ButtonDefaults.buttonColors(containerColor = lightOrange),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text, color = Color.Black)
      Icon(icon, contentDescription = description, tint = Color.Black)
    }
  }
}

/** Preview of [UploadScreen] using the Amber application theme. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun UploadScreenPreview() {
  AmberAppTheme(dynamicColor = false) { UploadScreen() }
}
