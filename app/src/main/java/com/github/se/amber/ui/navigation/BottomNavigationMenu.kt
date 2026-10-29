package com.github.se.amber.ui.navigation

//noinspection UsingMaterialAndMaterial3Libraries

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.github.se.amber.ui.theme.BIG_ROUND_CORNER
import com.github.se.amber.ui.theme.DIVIDER_THICKNESS
import com.github.se.amber.ui.theme.PATIENT_ICON_SIZE
import com.github.se.amber.ui.theme.lightGray
import com.github.se.amber.ui.theme.lightOrange
import com.github.se.amber.ui.theme.orange

object BottomNavigationMenuTestTags {
  const val BOTTOM_NAVIGATION_MENU = "bottom_navigation_menu"
  const val DIVIDER = "bottom_navigation_menu_divider"

  fun tabTag(tab: Tab) = "bottom_navigation_menu_tab_${tab.id}${tab.name}"
}

/**
 * Base interface for all bottom navigation tabs. Ensures consistent structure across all userView.
 *
 * @property name The label associated with the tab.
 * @property icon The icon displayed for the tab.
 * @property destination The screen opened when the tab is selected.
 */
sealed interface Tab {
  val name: String
  val icon: ImageVector
  val destination: Screen
  val id: String
}

/** Caregiver's bottom navigation tabs. */
sealed class CaregiverTab(
    override val name: String,
    override val icon: ImageVector,
    override val destination: CaregiverScreen,
    override val id: String = "caregiverId_",
) : Tab {
  data object Home : CaregiverTab("Home", Icons.Outlined.Home, CaregiverScreen.Home)

  data object Upload : CaregiverTab("Upload", Icons.Outlined.PhotoCamera, CaregiverScreen.Upload)

  data object Setting : CaregiverTab("Setting", Icons.Outlined.Settings, CaregiverScreen.Parameters)

  companion object {
    val caregiverTabs: List<CaregiverTab>
      get() = listOf(Home, Upload, Setting)
  }
}

/** Patient's bottom navigation tabs. */
sealed class PatientTab(
    override val name: String,
    override val icon: ImageVector,
    override val destination: PatientScreen,
    override val id: String = "patientId_",
) : Tab {
  data object Home : PatientTab("Home", Icons.Outlined.Home, PatientScreen.Home)

  data object SeePictures : PatientTab("Pictures", Icons.Outlined.Photo, PatientScreen.SeePictures)

  companion object {
    val patientTabs: List<PatientTab>
      get() = listOf(Home, SeePictures)
  }
}

/**
 * Displays the application's bottom navigation bar.
 *
 * @param selectedTab The tab that is currently selected
 * @param onTabSelected Callback invoked with the tab selected by the user.
 * @param tabs All tabs in the bottom navigation menu
 * @param modifier Modifier applied to the navigation menu's outer container.
 */
@Composable
fun BottomNavigationMenu(
    selectedTab: Tab,
    onTabSelected: (Tab) -> Unit,
    tabs: List<Tab>,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier =
          modifier.fillMaxWidth().testTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU)
  ) {
    HorizontalDivider(
        modifier = Modifier.testTag(BottomNavigationMenuTestTags.DIVIDER),
        thickness = DIVIDER_THICKNESS,
        color = lightGray,
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        content = {
          tabs.forEach { tab ->
            val iconModifier = if (tab is PatientTab) Modifier.size(PATIENT_ICON_SIZE) else Modifier
            NavigationBarItem(
                icon = {
                  Icon(
                      imageVector = tab.icon,
                      contentDescription = tab.name,
                      modifier = iconModifier,
                      tint = if (tab == selectedTab) orange else lightOrange,
                  )
                },
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                modifier =
                    Modifier.clip(RoundedCornerShape(BIG_ROUND_CORNER))
                        .testTag(BottomNavigationMenuTestTags.tabTag(tab)),
            )
          }
        },
    )
  }
}
