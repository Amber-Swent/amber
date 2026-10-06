/* Written by Lou-Anne Maier, with assistance from
* Claude (Anthropic) via Claude Code
*/
package com.github.se.amber.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
}

class TabDefinitionsTest {

    // ---------- Test tags ----------

    @Test
    fun tabTagIsBuiltFromTabName() {
        assertEquals("bottom_navigation_tab_Home", NavigationTestTags.tabTag(CaregiverTab.Home))
    }

    @Test
    fun tabTagsDifferBetweenTabsWithDifferentNames() {
        assertNotEquals(
            NavigationTestTags.tabTag(CaregiverTab.Home),
            NavigationTestTags.tabTag(CaregiverTab.Upload),
        )
    }

    // ---------- Caregiver tabs ----------

    @Test
    fun caregiverTabsContainsHomeThenUpload() {
        assertEquals(listOf(CaregiverTab.Home, CaregiverTab.Upload), CaregiverTab.caregiverTabs)
    }

    @Test
    fun caregiverHomeNameIsHome() {
        assertEquals("Home", CaregiverTab.Home.name)
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

    // ---------- Patient tabs ----------

    @Test
    fun patientTabsContainsHomeThenSeePictures() {
        assertEquals(listOf(PatientTab.Home, PatientTab.SeePictures), PatientTab.patientTabs)
    }

    @Test
    fun patientHomeNameIsHome() {
        assertEquals("Home", PatientTab.Home.name)
    }

    @Test
    fun patientSeePicturesNameIsPictures() {
        assertEquals("Pictures", PatientTab.SeePictures.name)
    }

    @Test
    fun patientHomeDestinationIsPatientHomeScreen() {
        assertEquals(PatientScreen.Home, PatientTab.Home.destination)
    }

    @Test
    fun patientSeePicturesDestinationIsPatientSeePicturesScreen() {
        assertEquals(PatientScreen.SeePictures, PatientTab.SeePictures.destination)
    }
}