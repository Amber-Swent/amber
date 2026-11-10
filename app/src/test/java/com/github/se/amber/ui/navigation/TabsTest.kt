// Written by Lou-Anne Maier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.navigation

import com.github.se.amber.R
import com.github.se.amber.ui.theme.CAREGIVER_ICON_SIZE
import com.github.se.amber.ui.theme.PATIENT_ICON_SIZE
import org.junit.Assert.assertEquals
import org.junit.Test

class TabsTest {
  // Keys and labels must be unique within one menu.
  @Test
  fun caregiverTabs_haveUniqueKeysAndLabels() {
    val keys = CaregiverTab.caregiverTabs.map { it.key }
    val labels = CaregiverTab.caregiverTabs.map { it.label }
    assertEquals(keys.distinct(), keys)
    assertEquals(labels.distinct(), labels)
  }

  @Test
  fun patientTabs_haveUniqueKeysAndLabels() {
    val keys = PatientTab.patientTabs.map { it.key }
    val labels = PatientTab.patientTabs.map { it.label }
    assertEquals(keys.distinct(), keys)
    assertEquals(labels.distinct(), labels)
  }

  // The tag uses the key, which is not translated, so tags are the same in every language.
  @Test
  fun tabTagUsesTheKeyNotTheLabel() {
    assertEquals(
        "BottomNavigationMenu_test_tag_tab_caregiverId_Home",
        BottomNavigationMenuTestTags.tabTag(CaregiverTab.Home),
    )
    assertEquals(
        "BottomNavigationMenu_test_tag_tab_patientId_Medias",
        BottomNavigationMenuTestTags.tabTag(PatientTab.SeeMedias),
    )
  }

  // ---------- Caregiver tabs ----------
  @Test
  fun caregiverHomeIsLabelledHome() {
    assertEquals("Home", CaregiverTab.Home.key)
    assertEquals(R.string.navigationTabHome, CaregiverTab.Home.label)
  }

  @Test
  fun tabTagDefaultsToCaregiverHome() {
    assertEquals(
        BottomNavigationMenuTestTags.tabTag(CaregiverTab.Home),
        BottomNavigationMenuTestTags.tabTag(),
    )
  }

  @Test
  fun caregiverUploadIsLabelledUpload() {
    assertEquals("Upload", CaregiverTab.Upload.key)
    assertEquals(R.string.navigationTabUpload, CaregiverTab.Upload.label)
  }

  @Test
  fun caregiverHomeDestinationIsCaregiverHomeScreen() {
    assertEquals(CaregiverScreen.Home, CaregiverTab.Home.destination)
  }

  @Test
  fun caregiverUploadDestinationIsCaregiverUploadScreen() {
    assertEquals(CaregiverScreen.Upload, CaregiverTab.Upload.destination)
  }

  @Test
  fun caregiverSettingOpensParameters() {
    assertEquals("Setting", CaregiverTab.Setting.key)
    assertEquals(R.string.navigationTabSetting, CaregiverTab.Setting.label)
    assertEquals(CaregiverScreen.Parameters, CaregiverTab.Setting.destination)
  }

  @Test
  fun caregiverTabsUseTheCaregiverIconSize() {
    CaregiverTab.caregiverTabs.forEach { tab -> assertEquals(CAREGIVER_ICON_SIZE, tab.iconSize) }
  }

  // ---------- Patient tabs ----------
  @Test
  fun patientHomeIsLabelledHome() {
    assertEquals("Home", PatientTab.Home.key)
    assertEquals(R.string.navigationTabHome, PatientTab.Home.label)
  }

  @Test
  fun patientSeeMediasIsLabelledMedias() {
    assertEquals("Medias", PatientTab.SeeMedias.key)
    assertEquals(R.string.navigationTabMedias, PatientTab.SeeMedias.label)
  }

  @Test
  fun patientSettingIsLabelledSetting() {
    assertEquals("Setting", PatientTab.Setting.key)
    assertEquals(R.string.navigationTabSetting, PatientTab.Setting.label)
  }

  @Test
  fun patientHomeDestinationIsPatientHomeScreen() {
    assertEquals(PatientScreen.Home, PatientTab.Home.destination)
  }

  @Test
  fun patientSeeMediasDestinationIsPatientSeeMediasScreen() {
    assertEquals(PatientScreen.SeeMedias, PatientTab.SeeMedias.destination)
  }

  @Test
  fun patientSettingDestinationIsPatientParametersScreen() {
    assertEquals(PatientScreen.Parameters, PatientTab.Setting.destination)
  }

  @Test
  fun patientTabsUseThePatientIconSize() {
    PatientTab.patientTabs.forEach { tab -> assertEquals(PATIENT_ICON_SIZE, tab.iconSize) }
  }
}
