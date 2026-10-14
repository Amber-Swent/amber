// this code was written with the aid of AI
package com.github.se.amber.ui.patient

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.credentials.CredentialManager
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PatientHomeScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var viewModel: PatientHomeViewModel
  private lateinit var stateFlow: MutableStateFlow<PatientHomeUIState>

  private val credentialManager = mockk<CredentialManager>(relaxed = true)

  // Spies: permettent de vérifier les appels aux callbacks
  private var signedOutCount = 0
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
    signedOutCount = 0
    selectedTabs.clear()
    stateFlow = MutableStateFlow(PatientHomeUIState(patientProfile = profileOf("Alice")))
    viewModel = mockk(relaxed = true)
    every { viewModel.uiState } returns stateFlow
  }

  private fun setScreen() {
    composeTestRule.setContent {
      PatientHomeScreen(
          homeViewModel = viewModel,
          credentialManager = credentialManager,
          onSignedOut = { signedOutCount++ },
          onTabSelected = { selectedTabs.add(it) },
      )
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

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU)
        .assertIsDisplayed()
  }

  @Test
  fun noCallbackIsCalledAtStart() {
    setScreen()
    composeTestRule.waitForIdle()

    assertEquals(0, signedOutCount)
    assertEquals(emptyList<Tab>(), selectedTabs)
  }

  @Test
  fun clickingTopBarButtonCallsSignOut() {
    setScreen()

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    verify { viewModel.signOut(credentialManager) }
  }

  @Test
  fun onSignedOutIsCalledWhenStateIsSignedOut() {
    setScreen()

    stateFlow.value = stateFlow.value.copy(signedOut = true)
    composeTestRule.waitForIdle()

    assertEquals(1, signedOutCount)
  }

  @Test
  fun errorMessageIsClearedAfterBeingShown() {
    setScreen()

    stateFlow.value = stateFlow.value.copy(errorMsg = "Oops")
    composeTestRule.waitForIdle()

    verify { viewModel.clearErrorMsg() }
  }
}
