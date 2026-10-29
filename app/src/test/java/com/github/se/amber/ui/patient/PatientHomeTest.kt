// this code was written with the aid of AI
// Modified by Alaabrine, with assistance from Cursor.
package com.github.se.amber.ui.patient

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.theme.AmberAppTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
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
      AmberAppTheme {
        PatientHomeScreen(
            homeViewModel = viewModel,
            onTabSelected = { selectedTabs.add(it) },
        )
      }
    }
  }

  @Test
  fun displaysTitle() {
    setScreen()

    composeTestRule.onNodeWithTag(PatientHomeScreenTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(PatientHomeScreenTestTags.TITLE).assertTextEquals("Home")
  }

  @Test
  fun displaysGreetingWithFirstName() {
    setScreen()

    composeTestRule.onNodeWithTag(PatientHomeScreenTestTags.GREETINGS).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(PatientHomeScreenTestTags.GREETINGS)
        .assertTextEquals("Hello Alice !")
  }

  @Test
  fun greetingUpdatesWhenStateChanges() {
    setScreen()
    composeTestRule
        .onNodeWithTag(PatientHomeScreenTestTags.GREETINGS)
        .assertTextEquals("Hello Alice !")

    stateFlow.value = PatientHomeUIState(patientProfile = profileOf("Bob"))
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(PatientHomeScreenTestTags.GREETINGS)
        .assertTextEquals("Hello Bob !")
  }

  @Test
  fun navigationBarsAreDisplayed() {
    setScreen()

    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU)
        .assertIsDisplayed()
  }

  @Test
  fun noCallbackIsCalledAtStart() {
    setScreen()
    composeTestRule.waitForIdle()

    assertEquals(emptyList<Tab>(), selectedTabs)
  }

  @Test
  fun errorToastClearsErrorMsg() {
    stateFlow.value = stateFlow.value.copy(errorMsg = "oops")
    setScreen()

    composeTestRule.waitForIdle()

    io.mockk.verify { viewModel.clearErrorMsg() }
  }

  @Test
  fun noToastIsShownWhenThereIsNoError() {
    setScreen()

    composeTestRule.waitForIdle()

    assertEquals(0, ShadowToast.shownToastCount())
  }

  @Test
  fun screenUsesDefaultOnTabSelectedWhenOmitted() {
    composeTestRule.setContent { AmberAppTheme { PatientHomeScreen(homeViewModel = viewModel) } }

    composeTestRule.onNodeWithTag(PatientHomeScreenTestTags.TITLE).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU)
        .assertIsDisplayed()
  }

  @Test
  fun previewRendersWithoutCrashing() {
    composeTestRule.setContent { PatientHomeScreenPreview() }

    composeTestRule.onNodeWithTag(PatientHomeScreenTestTags.TITLE).assertIsDisplayed()
  }
}
