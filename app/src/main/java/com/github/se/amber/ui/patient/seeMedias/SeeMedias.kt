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
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PADDING_ENORMOUS
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.lightOrange

object SeeMediasTestTags {
    const val PICTURE_BUTTON = "see_picture_button"
    const val TEXT_BUTTON = "see_text_button"
    const val AUDIO_BUTTON = "see_audio_button"
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
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = PADDING_MEDIUM)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(PADDING_MEDIUM, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DisplayButton(
                    modifier = Modifier.testTag(SeeMediasTestTags.PICTURE_BUTTON),
                    icon = Icons.Outlined.PhotoCamera,
                    text = "See Picture",
                    description = "Picture",
                    onClick = onSeePictureClick,
                )

                DisplayButton(
                    modifier = Modifier.testTag(SeeMediasTestTags.TEXT_BUTTON),
                    icon = Icons.Outlined.Description,
                    text = "See Text",
                    description = "Text",
                    onClick = onSeeTextClick,
                )

                DisplayButton(
                    modifier = Modifier.testTag(SeeMediasTestTags.AUDIO_BUTTON),
                    icon = Icons.Outlined.Mic,
                    text = "See Audio",
                    description = "Audio",
                    onClick = onSeeAudioClick,
                )
            }
        },
    )
}

/**
 * Displays a button with an icon, label, and click action.
 *
 * @param modifier modifier applied to the button
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
        modifier = modifier.height(PADDING_ENORMOUS),
        colors = ButtonDefaults.buttonColors(containerColor = lightOrange),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                color = Color.Black,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Icon(
                icon,
                contentDescription = description,
                tint = Color.Black,
            )
        }
    }
}


/** Preview of [SeeMediasScreen] using the Amber application theme. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SeeMediasScreenPreview() {
    AmberAppTheme(dynamicColor = false) { SeeMediasScreen()}
}