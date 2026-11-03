/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 * Modified by Alaabrine, with assistance from Cursor.
 * Modified by MeiliV, with assistance from OpenAI Codex.
 */
package com.github.se.amber.ui.caregiver.upload

import android.widget.Toast
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.ui.caregiver.DisplayCaregiverButton
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.caregiver.upload.UploadTestTags.AUDIO_BUTTON
import com.github.se.amber.ui.caregiver.upload.UploadTestTags.PICTURE_BUTTON
import com.github.se.amber.ui.caregiver.upload.UploadTestTags.TEXT_BUTTON
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.CaregiverTab
import com.github.se.amber.ui.navigation.CaregiverTab.Companion.caregiverTabs
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_WIDTH_ENORMOUS
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL

object UploadTestTags {
  const val TITLE = "upload_title"
  const val TEXT_BUTTON = "upload_text_button"
  const val PICTURE_BUTTON = "upload_picture_button"
  const val AUDIO_BUTTON = "upload_audio_button"
}

/**
 * Displays the caregiver upload menu for text, picture, and audio stories along with bottom navigation.
 *
 * @param viewModel view model that stores the upload screen state.
 * @param onUploadTextClick callback invoked when text upload is selected.
 * @param onUploadPictureClick callback invoked when picture upload is selected.
 * @param onUploadAudioClick callback invoked when audio upload is selected.
 * @param onTabSelected callback invoked when a bottom navigation tab is selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    viewModel: UploadViewModel = viewModel(),
    onUploadTextClick: () -> Unit = {},
    onUploadPictureClick: () -> Unit = {},
    onUploadAudioClick: () -> Unit = {},
    onTabSelected: (Tab) -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
          TopAppBar(
            title = {
              DisplayCaregiverTitle(
                  modifier = Modifier.testTag(UploadTestTags.TITLE),
                  title = "Upload Medias"
              )
          }
          )
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
                Modifier.fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(CAREGIVER_PADDING_SMALL),
            verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL),
        ) {

          /* Text upload is planned for a later sprint.
          DisplayCaregiverButton(
              modifier = Modifier.testTag(TEXT_BUTTON),
              widthPercentage = CAREGIVER_BUTTON_WIDTH_ENORMOUS,
              icon = Icons.Outlined.Description,
              text = "Upload Text",
              description = "Text",
              onClick = { onUploadTextClick() },
          )
           */

          DisplayCaregiverButton(
              modifier = Modifier.testTag(PICTURE_BUTTON),
              widthPercentage = CAREGIVER_BUTTON_WIDTH_ENORMOUS,
              icon = Icons.Outlined.PhotoCamera,
              text = "Upload Picture",
              description = "Picture",
              onClick = { onUploadPictureClick() },
          )

          DisplayCaregiverButton(
              modifier = Modifier.testTag(AUDIO_BUTTON),
              widthPercentage = CAREGIVER_BUTTON_WIDTH_ENORMOUS,
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
 * Compatibility wrapper for screens that still call [DisplayButton]. New caregiver buttons should
 * use [DisplayCaregiverButton] directly. To be removed when no more screens use it.
 *
 * @param modifier modifier applied to the button.
 * @param icon icon displayed beside the label.
 * @param text button label.
 * @param description accessibility description for [icon].
 * @param onClick callback invoked when the button is pressed.
 */
@Composable
fun DisplayButton(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Description,
    text: String = "",
    description: String = "Text",
    onClick: () -> Unit = {},
) {
  DisplayCaregiverButton(
      modifier = modifier,
      onClick = onClick,
      icon = icon,
      description = description,
      text = text,
      style = MaterialTheme.typography.labelLarge,
      widthPercentage = CAREGIVER_BUTTON_WIDTH_ENORMOUS,
  )
}

/** Preview of [UploadScreen] using the Amber application theme. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun UploadScreenPreview() {
  AmberAppTheme(dynamicColor = false) { UploadScreen() }
}
