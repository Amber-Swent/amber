// Written by Lou-Anne Maier.
// Modified by Yuhan YANG, with assistance from Cursor.
package com.github.se.amber.ui.navigation

//noinspection UsingMaterialAndMaterial3Libraries

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.github.se.amber.ui.theme.DIVIDER_THICKNESS
import com.github.se.amber.ui.theme.ROUND_CORNER

internal const val HOME_TAB_LABEL = "Home"
internal const val UPLOAD_TAB_LABEL = "Upload"
internal const val SETTING_TAB_LABEL = "Setting"
internal const val PICTURES_TAB_LABEL = "Pictures"

/** Stable tags for the bottom bar, its divider, and one tag per [Tab]. */
object BottomNavigationMenuTestTags {
  const val BOTTOM_NAVIGATION_MENU = "BottomNavigationMenu_test_tag_menu"
  const val DIVIDER = "BottomNavigationMenu_test_tag_divider"

  /**
   * Tag for [tab], unique across caregiver and patient menus. The default is the caregiver home
   * tab, the same default selection as [BottomNavigationMenu].
   */
  fun tabTag(tab: Tab = CaregiverTab.Home) =
      "BottomNavigationMenu_test_tag_tab_${tab.id}${tab.name}"
}

/**
 * One destination in a bottom navigation menu.
 *
 * @property name Label used as the icon content description.
 * @property icon Icon drawn for this tab.
 * @property destination Screen opened when the tab is selected.
 * @property id Prefix that keeps a caregiver tag distinct from a patient tag with the same name.
 */
sealed interface Tab {
  val name: String
  val icon: ImageVector
  val destination: Screen
  val id: String
}

/**
 * Caregiver bottom navigation tabs.
 *
 * @property name Label used as the icon content description.
 * @property icon Icon drawn for this tab.
 * @property destination Screen opened when the tab is selected.
 * @property id Prefix shared by every caregiver tab.
 */
sealed class CaregiverTab(
    override val name: String,
    override val icon: ImageVector,
    override val destination: CaregiverScreen,
    override val id: String = "caregiverId_",
) : Tab {
  /** Caregiver home. */
  data object Home : CaregiverTab(HOME_TAB_LABEL, Icons.Outlined.Home, CaregiverScreen.Home)

  /** Caregiver upload. */
  data object Upload :
      CaregiverTab(UPLOAD_TAB_LABEL, Icons.Outlined.PhotoCamera, CaregiverScreen.Upload)

  /**
   * Caregiver parameters. The visible label stays [SETTING_TAB_LABEL]; the destination is
   * [CaregiverScreen.Parameters].
   */
  data object Setting :
      CaregiverTab(SETTING_TAB_LABEL, Icons.Outlined.Settings, CaregiverScreen.Parameters)

  companion object {
    /** Tabs shown on caregiver screens, in bar order. */
    val caregiverTabs: List<CaregiverTab>
      get() = listOf(Home, Upload, Setting)
  }
}

/**
 * Patient bottom navigation tabs.
 *
 * @property name Label used as the icon content description.
 * @property icon Icon drawn for this tab.
 * @property destination Screen opened when the tab is selected.
 * @property id Prefix shared by every patient tab.
 */
sealed class PatientTab(
    override val name: String,
    override val icon: ImageVector,
    override val destination: PatientScreen,
    override val id: String = "patientId_",
) : Tab {
  /** Patient home. */
  data object Home : PatientTab(HOME_TAB_LABEL, Icons.Outlined.Home, PatientScreen.Home)

  /** Patient picture list. */
  data object SeePictures :
      PatientTab(PICTURES_TAB_LABEL, Icons.Outlined.Photo, PatientScreen.SeePictures)

  companion object {
    /** Tabs shown on patient screens, in bar order. */
    val patientTabs: List<PatientTab>
      get() = listOf(Home, SeePictures)
  }
}

/**
 * Bottom bar for the tabs of one role.
 *
 * A selected icon uses `secondary`. An unselected icon uses `tertiary`. The selection indicator
 * uses `surface` so it does not fall back to the theme's unset container color.
 *
 * @param modifier Modifier applied to the column that holds the divider and the bar.
 * @param selectedTab Tab that is currently selected. Ignored when it is not in [tabs].
 * @param onTabSelected Called with the tab the user taps.
 * @param tabs Tabs to show, in order. Defaults to the caregiver tabs.
 */
@Composable
fun BottomNavigationMenu(
    modifier: Modifier = Modifier,
    selectedTab: Tab = CaregiverTab.Home,
    onTabSelected: (Tab) -> Unit = {},
    tabs: List<Tab> = CaregiverTab.caregiverTabs,
) {
  Column(
      modifier =
          modifier.fillMaxWidth().testTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU)
  ) {
    HorizontalDivider(
        modifier = Modifier.testTag(BottomNavigationMenuTestTags.DIVIDER),
        thickness = DIVIDER_THICKNESS,
        color = MaterialTheme.colorScheme.outline,
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        content = {
          tabs.forEach { tab ->
            NavigationBarItem(
                icon = { Icon(imageVector = tab.icon, contentDescription = tab.name) },
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                modifier =
                    Modifier.clip(RoundedCornerShape(ROUND_CORNER))
                        .testTag(BottomNavigationMenuTestTags.tabTag(tab)),
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.secondary,
                        unselectedIconColor = MaterialTheme.colorScheme.tertiary,
                        indicatorColor = MaterialTheme.colorScheme.surface,
                    ),
            )
          }
        },
    )
  }
}
