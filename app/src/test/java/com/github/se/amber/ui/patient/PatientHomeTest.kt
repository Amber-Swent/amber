// this code was written with the aid of AI
package com.github.se.amber.ui.patient

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class PatientHomeScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var viewModel: PatientHomeViewModel
  private lateinit var stateFlow: MutableStateFlow<PatientHomeUIState>

  private val selectedTabs = mutableListOf<Tab>()

  private val inBottomBar =
      hasAnyAncestor(hasTestTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU))

  private fun profileOf(firstName: String) =
      UserProfile(
          uid = "1",
          role = Role.PATIENT,
          person = Person(firstName = firstName),
          circleIds = emptyList(),
      )

  @Before
  fun setUp() {
    selectedTabs.clear()
    stateFlow = MutableStateFlow(PatientHomeUIState(patientProfile = profileOf("Alice")))
    viewModel = mockk(relaxed = true)
    every { viewModel.uiState } returns stateFlow
  }

  private fun setScreen() {
    composeTestRule.setContent {
      PatientHomeScreen(
          homeViewModel = viewModel,
          onTabSelected = { selectedTabs.add(it) },
      )
    }
  }

  // ---------- Contenu ----------

  @Test
  fun displaysTitle() {
    setScreen()

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TITLE).assertTextEquals("Home")
  }

  @Test
  fun displaysGreetingWithFirstName() {
    setScreen()

    composeTestRule.onNodeWithTag(PatientHomeTestTags.GREETINGS).assertIsDisplayed()
    composeTestRule.onNodeWithTag(PatientHomeTestTags.GREETINGS).assertTextEquals("Hello Alice!")
  }

  @Test
  fun greetingUpdatesWhenStateChanges() {
    setScreen()
    composeTestRule.onNodeWithTag(PatientHomeTestTags.GREETINGS).assertTextEquals("Hello Alice!")

    stateFlow.value = PatientHomeUIState(patientProfile = profileOf("Bob"))
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(PatientHomeTestTags.GREETINGS).assertTextEquals("Hello Bob!")
  }

  @Test
  fun greetingWithEmptyFirstNameDoesNotCrash() {
    stateFlow.value = PatientHomeUIState(patientProfile = profileOf(""))
    setScreen()

    composeTestRule.onNodeWithTag(PatientHomeTestTags.GREETINGS).assertIsDisplayed()
    composeTestRule.onNodeWithTag(PatientHomeTestTags.GREETINGS).assertTextEquals("Hello !")
  }

  @Test
  fun greetingWithVeryLongFirstNameIsDisplayed() {
    stateFlow.value = PatientHomeUIState(patientProfile = profileOf("A".repeat(200)))
    setScreen()

    composeTestRule.onNodeWithTag(PatientHomeTestTags.GREETINGS).assertExists()
  }

  // ---------- Bottom navigation ----------

  @Test
  fun bottomNavigationBarIsDisplayed() {
    setScreen()

    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU)
        .assertIsDisplayed()
  }

  @Test
  fun bottomNavigationShowsAllPatientTabs() {
    setScreen()

    composeTestRule
        .onAllNodes(hasClickAction() and inBottomBar)
        .assertCountEquals(PatientTab.patientTabs.size)
  }

  @Test
  fun homeTabIsTheOnlySelectedTab() {
    setScreen()

    composeTestRule.onAllNodes(isSelected() and inBottomBar).assertCountEquals(1)
  }

  @Test
  fun clickingATabCallsOnTabSelected() {
    setScreen()

    // On clique sur le dernier onglet (différent de Home, qui est déjà sélectionné)
    val lastIndex = PatientTab.patientTabs.size - 1
    composeTestRule.onAllNodes(hasClickAction() and inBottomBar)[lastIndex].performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, selectedTabs.size)
    assertTrue(selectedTabs.single() in PatientTab.patientTabs)
  }

  @Test
  fun noCallbackIsCalledAtStart() {
    setScreen()
    composeTestRule.waitForIdle()

    assertEquals(emptyList<Tab>(), selectedTabs)
  }

  // ---------- Messages d'erreur ----------

  @Test
  fun noToastWhenThereIsNoError() {
    setScreen()
    composeTestRule.waitForIdle()

    assertNull(ShadowToast.getLatestToast())
    verify(exactly = 0) { viewModel.clearErrorMsg() }
  }

  @Test
  fun errorMessageIsClearedAfterBeingShown() {
    setScreen()

    stateFlow.value = stateFlow.value.copy(errorMsg = "Oops")
    composeTestRule.waitForIdle()

    assertEquals("Oops", ShadowToast.getTextOfLatestToast())
    verify { viewModel.clearErrorMsg() }
  }

  @Test
  fun secondDifferentErrorShowsANewToast() {
    setScreen()

    stateFlow.value = stateFlow.value.copy(errorMsg = "First")
    composeTestRule.waitForIdle()
    assertEquals("First", ShadowToast.getTextOfLatestToast())

    stateFlow.value = stateFlow.value.copy(errorMsg = "Second")
    composeTestRule.waitForIdle()
    assertEquals("Second", ShadowToast.getTextOfLatestToast())

    verify(exactly = 2) { viewModel.clearErrorMsg() }
  }
}
