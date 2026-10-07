/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.package
 */
package com.github.se.amber.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.ui.navigation.PatientTab.Companion.patientTabs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomNavigationMenuTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** Fake callback: records every tab passed to onTabSelected, in order. */
  private class FakeTabSelectionHandler {
    val selectedTabs = mutableListOf<Tab>()

    fun onTabSelected(tab: Tab) {
      selectedTabs.add(tab)
    }
  }

  // ---------- Setup helpers ----------

  /** Menu whose selected tab never changes: isolates the composable from any state holder. */
  private fun setMenu(
      selectedTab: Tab = CaregiverTab.Home,
      tabs: List<Tab> = CaregiverTab.caregiverTabs,
      handler: FakeTabSelectionHandler = FakeTabSelectionHandler(),
  ) {
    composeTestRule.setContent {
      BottomNavigationMenu(
          selectedTab = selectedTab,
          onTabSelected = handler::onTabSelected,
          tabs = tabs,
      )
    }
  }

  /** Menu hosted like in the real app: the selected tab follows the user's clicks. */
  private fun setStatefulMenu(
      initialTab: Tab = CaregiverTab.Home,
      tabs: List<Tab> = CaregiverTab.caregiverTabs,
  ) {
    composeTestRule.setContent {
      var selectedTab by remember { mutableStateOf(initialTab) }
      BottomNavigationMenu(
          selectedTab = selectedTab,
          onTabSelected = { selectedTab = it },
          tabs = tabs,
      )
    }
  }

  // ---------- Check helpers ----------
  private fun tabNode(tab: Tab): SemanticsNodeInteraction =
      composeTestRule.onNodeWithTag(BottomNavigationTestTags.tabTag(tab))

  private fun checkTabIsDisplayed(tab: Tab) {
    tabNode(tab).assertIsDisplayed()
  }

  private fun checkTabIsNotDisplayed(tab: Tab) {
    tabNode(tab).assertDoesNotExist()
  }

  private fun checkTabIsSelected(tab: Tab) {
    tabNode(tab).assertIsSelected()
  }

  private fun checkTabIsNotSelected(tab: Tab) {
    tabNode(tab).assertIsNotSelected()
  }

  // ---------- Container & divider ----------

  @Test
  fun menuIsDisplayed() {
    setMenu()
    composeTestRule
        .onNodeWithTag(BottomNavigationTestTags.BOTTOM_NAVIGATION_MENU)
        .assertIsDisplayed()
  }

  @Test
  fun dividerIsDisplayed() {
    setMenu()
    composeTestRule.onNodeWithTag(BottomNavigationTestTags.DIVIDER).assertIsDisplayed()
  }

  // ---------- Caregiver tabs display ----------

  @Test
  fun caregiverTabAreDisplayed() {
    setMenu(tabs = CaregiverTab.caregiverTabs)
    CaregiverTab.caregiverTabs.forEach { checkTabIsDisplayed(it) }
  }

  @Test
  fun patientTabsAreNotDisplayedInCaregiverMenu() {
    setMenu(tabs = CaregiverTab.caregiverTabs)
    PatientTab.patientTabs.forEach { checkTabIsNotDisplayed(it) }
  }

  // ---------- Patient tabs display ----------

  @Test
  fun patientTabAreDisplayed() {
    setMenu(selectedTab = PatientTab.Home, tabs = PatientTab.patientTabs)
    PatientTab.patientTabs.forEach { checkTabIsDisplayed(it) }
  }

  @Test
  fun caregiverTabAreNotDisplayedInPatientMenu() {
    setMenu(selectedTab = PatientTab.Home, tabs = PatientTab.patientTabs)
    CaregiverTab.caregiverTabs.forEach { checkTabIsNotDisplayed(it) }
  }

  // ---------- Empty list ----------

  @Test
  fun emptyTabListDisplaysNoTab() {
    setMenu(tabs = emptyList())

    checkTabIsNotDisplayed(CaregiverTab.Home)
    checkTabIsNotDisplayed(CaregiverTab.Upload)
    checkTabIsNotDisplayed(PatientTab.Home)
    checkTabIsNotDisplayed(PatientTab.SeePictures)
  }

  // ---------- Selection state (fixed selectedTab) ----------

  @Test
  fun selectedTabIsSelected() {
    setMenu(selectedTab = CaregiverTab.Home)
    checkTabIsSelected(CaregiverTab.Home)
  }

  @Test
  fun unselectedTabIsNotSelected() {
    setMenu(selectedTab = CaregiverTab.Home)
    checkTabIsNotSelected(CaregiverTab.Upload)
  }

  @Test
  fun selectionFollowsSelectedTabParameter() {
    setMenu(selectedTab = CaregiverTab.Upload)
    checkTabIsSelected(CaregiverTab.Upload)
    checkTabIsNotSelected(CaregiverTab.Home)
  }

  @Test
  fun patientSelectedTabIsSelected() {
    setMenu(selectedTab = PatientTab.Home, tabs = PatientTab.patientTabs)
    checkTabIsSelected(PatientTab.Home)
    checkTabIsNotSelected(PatientTab.SeePictures)
  }

  // ---------- Selection state (real state holder) ----------

  @Test
  fun clickingCareGiverTabSelectsIt() {
    setStatefulMenu(initialTab = CaregiverTab.Home)
    tabNode(CaregiverTab.Upload).performClick()
    checkTabIsSelected(CaregiverTab.Upload)
  }

  @Test
  fun clickingPatientTabSelectsIt() {
    setStatefulMenu(initialTab = PatientTab.Home, tabs = PatientTab.patientTabs)

    tabNode(PatientTab.SeePictures).performClick()

    checkTabIsSelected(PatientTab.SeePictures)
  }

  @Test
  fun clickingTabDeselectsPreviousTab() {
    setStatefulMenu(initialTab = CaregiverTab.Home)
    tabNode(CaregiverTab.Upload).performClick()
    checkTabIsNotSelected(CaregiverTab.Home)
  }

  @Test
  fun clickingBackToFirstTabSelectsItAgain() {
    setStatefulMenu(initialTab = CaregiverTab.Home)
    tabNode(CaregiverTab.Upload).performClick()
    tabNode(CaregiverTab.Home).performClick()
    checkTabIsSelected(CaregiverTab.Home)
  }

  @Test
  fun clickingAlreadySelectedTabKeepsItSelected() {
    setStatefulMenu(initialTab = CaregiverTab.Home)
    tabNode(CaregiverTab.Home).performClick()
    checkTabIsSelected(CaregiverTab.Home)
  }

  // ---------- Accessibility ----------

  @Test
  fun tabHasItsNameAsContentDescription() {
    setMenu(tabs = CaregiverTab.caregiverTabs)
    tabNode(CaregiverTab.Upload).assertContentDescriptionEquals("Upload")
  }

  @Test
  fun tabHasClickAction() {
    setMenu()
    tabNode(CaregiverTab.Upload).assertHasClickAction()
  }
}
