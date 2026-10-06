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
    // ---------- Caregiver tabs ----------
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