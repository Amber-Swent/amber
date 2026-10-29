/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 * Modified by Yuhan YANG, with assistance from Cursor.
 */
package com.github.se.amber.ui.navigation

import com.github.se.amber.ui.theme.CAREGIVER_ICON_SIZE
import com.github.se.amber.ui.theme.PATIENT_ICON_SIZE
import org.junit.Assert.assertEquals
import org.junit.Test

class TabsTest {
  // Names must be unique within one menu.
  @Test
  fun caregiverTabs_haveUniqueNames() {
    val names = CaregiverTab.caregiverTabs.map { it.name }
    assertEquals(names.distinct(), names)
  }

  @Test
  fun patientTabs_haveUniqueNames() {
    val names = PatientTab.patientTabs.map { it.name }
    assertEquals(names.distinct(), names)
  }

  // ---------- Caregiver tabs ----------
  @Test
  fun caregiverHomeNameIsHome() {
    assertEquals("Home", CaregiverTab.Home.name)
  }

  @Test
  fun tabTagDefaultsToCaregiverHome() {
    assertEquals(
        BottomNavigationMenuTestTags.tabTag(CaregiverTab.Home),
        BottomNavigationMenuTestTags.tabTag(),
    )
  }

  @Test
  fun caregiverUploadNameIsUpload() {
    assertEquals("Upload", CaregiverTab.Upload.name)
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
    assertEquals("Setting", CaregiverTab.Setting.name)
    assertEquals(CaregiverScreen.Parameters, CaregiverTab.Setting.destination)
  }

  @Test
  fun caregiverTabsUseTheCaregiverIconSize() {
    assertEquals(CAREGIVER_ICON_SIZE, CaregiverTab.Home.iconSize)
    assertEquals(CAREGIVER_ICON_SIZE, CaregiverTab.Upload.iconSize)
    assertEquals(CAREGIVER_ICON_SIZE, CaregiverTab.Setting.iconSize)
  }

  // ---------- Patient tabs ----------
  @Test
  fun patientHomeNameIsHome() {
    assertEquals("Home", PatientTab.Home.name)
  }

  @Test
  fun patientSeePicturesNameIsPictures() {
    assertEquals("Pictures", PatientTab.SeePictures.name)
  }

  @Test
  fun patientSettingNameIsSetting() {
    assertEquals("Setting", PatientTab.Setting.name)
  }

  @Test
  fun patientHomeDestinationIsPatientHomeScreen() {
    assertEquals(PatientScreen.Home, PatientTab.Home.destination)
  }

  @Test
  fun patientSeePicturesDestinationIsPatientSeePicturesScreen() {
    assertEquals(PatientScreen.SeePictures, PatientTab.SeePictures.destination)
  }

  @Test
  fun patientSettingDestinationIsPatientParametersScreen() {
    assertEquals(PatientScreen.Parameters, PatientTab.Setting.destination)
  }

  @Test
  fun patientTabsUseThePatientIconSize() {
    assertEquals(PATIENT_ICON_SIZE, PatientTab.Home.iconSize)
    assertEquals(PATIENT_ICON_SIZE, PatientTab.SeePictures.iconSize)
    assertEquals(PATIENT_ICON_SIZE, PatientTab.Setting.iconSize)
  }
}
