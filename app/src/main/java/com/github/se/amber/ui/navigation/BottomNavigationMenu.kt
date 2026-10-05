package com.github.se.amber.ui.navigation

//noinspection UsingMaterialAndMaterial3Libraries

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.github.se.amber.ui.theme.lightGray
import com.github.se.amber.ui.theme.lightOrange
import com.github.se.amber.ui.theme.orange

/**
 * Base interface for all bottom navigation tabs.
 * Ensures consistent structure across all userView.
 *
 * @param name The label associated with the tab.
 * @param icon The icon displayed for the tab.
 * @param destination The screen opened when the tab is selected.
 */
sealed interface Tab {
    val name: String
    val icon: ImageVector
    val destination: Screen
}

/**
 * Caregiver's bottom navigation tabs.
 */
sealed class CaregiverTab(
    override val name: String,
    override val icon: ImageVector,
    override val destination: CaregiverScreen,
) : Tab {
    object Home : CaregiverTab("Home", Icons.Outlined.Home, CaregiverScreen.Home)
    object Upload : CaregiverTab("Upload", Icons.Outlined.PhotoCamera, CaregiverScreen.Upload)
}

/**
 * Patient's bottom navigation tabs.
 */
sealed class PatientTab(
    override val name: String,
    override val icon: ImageVector,
    override val destination: PatientScreen,
) : Tab {
    object Home : PatientTab("Home", Icons.Outlined.Home, PatientScreen.Home)
    object SeePictures : PatientTab("Pictures", Icons.Outlined.Photo, PatientScreen.SeePictures)
}

private val caregiverTabs = listOf(
    CaregiverTab.Home,
    CaregiverTab.Upload,
)

private val patientTabs = listOf(
    PatientTab.Home,
    PatientTab.SeePictures,
)

/**
 * Displays the application's bottom navigation bar.
 *
 * @param selectedTab The tab that is currently selected
 * @param onTabSelected Callback invoked with the tab selected by the user.
 * @param modifier Modifier applied to the navigation menu's outer container.
 */
@Composable
fun BottomNavigationMenu(
    selectedTab: Tab,
    onTabSelected: (Tab) -> Unit,
    tabs: List<Tab>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier, thickness = 1.dp, color = lightGray)

        NavigationBar(
            modifier =
                Modifier.fillMaxWidth().height(60.dp),
            containerColor = Color.White,
            content = {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                tab.icon,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = if (tab == selectedTab)
                                    orange
                                else
                                    lightOrange
                            )
                        },
                        selected = tab == selectedTab,
                        onClick = { onTabSelected(tab) },
                        modifier =
                            Modifier.clip(RoundedCornerShape(50.dp)),
                    )
                }
            },
        )
    }
}
