// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.patient

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.theme.AmberAppTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class PatientHomeScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  /** Text of [id] in the current test language, with [args] for its placeholders. */
  private fun string(id: Int, vararg args: Any): String =
      ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

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
    composeTestRule
        .onNodeWithTag(PatientUtilsTestTags.TITLE)
        .assertTextEquals(string(R.string.patientHomeTitle))
  }

  @Test
  fun displaysGreetingWithFirstName() {
    setScreen()

    composeTestRule.onNodeWithTag(PatientHomeTestTags.GREETINGS).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(PatientHomeTestTags.GREETINGS)
        .assertTextEquals(string(R.string.patientHomeGreeting, "Alice"))
  }

  @Test
  fun greetingUpdatesWhenStateChanges() {
    setScreen()
    composeTestRule
        .onNodeWithTag(PatientHomeTestTags.GREETINGS)
        .assertTextEquals(string(R.string.patientHomeGreeting, "Alice"))

    stateFlow.value = PatientHomeUIState(patientProfile = profileOf("Bob"))
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(PatientHomeTestTags.GREETINGS)
        .assertTextEquals(string(R.string.patientHomeGreeting, "Bob"))
  }

  @Test
  fun greetingWithEmptyFirstNameDoesNotCrash() {
    stateFlow.value = PatientHomeUIState(patientProfile = profileOf(""))
    setScreen()

    composeTestRule.onNodeWithTag(PatientHomeTestTags.GREETINGS).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(PatientHomeTestTags.GREETINGS)
        .assertTextEquals(string(R.string.patientHomeGreeting, ""))
  }

  @Test
  fun greetingWithLongFirstNameIsDisplayed() {
    stateFlow.value = PatientHomeUIState(patientProfile = profileOf("A".repeat(30)))
    setScreen()

    composeTestRule.onNodeWithTag(PatientHomeTestTags.GREETINGS).assertIsDisplayed()
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
  fun screenUsesDefaultOnTabSelectedWhenOmitted() {
    composeTestRule.setContent { AmberAppTheme { PatientHomeScreen(homeViewModel = viewModel) } }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TITLE).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU)
        .assertIsDisplayed()
  }

  @Test
  fun bottomNavigationAssert_RightNumberOfPatientTabs_andHasClick() {
    setScreen()

    composeTestRule
        .onAllNodes(hasClickAction() and inBottomBar)
        .assertCountEquals(PatientTab.patientTabs.size)
  }

  @Test
  fun homeTabIsTheOnlySelectedTab() {
    setScreen()

    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.tabTag(PatientTab.Home))
        .assertIsSelected()

    composeTestRule.onAllNodes(isSelected() and inBottomBar).assertCountEquals(1)
  }

  @Test
  fun clickingEachTabCallsOnTabSelectedWithThatTab() {
    setScreen()

    PatientTab.patientTabs.indices.forEach { index ->
      composeTestRule.onAllNodes(hasClickAction() and inBottomBar)[index].performClick()
    }
    composeTestRule.waitForIdle()

    assertEquals(PatientTab.patientTabs, selectedTabs)
  }

  @Test
  fun clickingATabCallsOnTabSelected() {
    setScreen()

    // Click the last tab (different from Home, which is already selected)
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

    stateFlow.value = stateFlow.value.copy(errorMsgRes = R.string.patientHomeErrorNoProfile)
    composeTestRule.waitForIdle()

    assertEquals(string(R.string.patientHomeErrorNoProfile), ShadowToast.getTextOfLatestToast())
    verify { viewModel.clearErrorMsg() }
  }

  @Test
  fun secondDifferentErrorShowsANewToast() {
    setScreen()

    stateFlow.value = stateFlow.value.copy(errorMsgRes = R.string.patientHomeErrorNoProfile)
    composeTestRule.waitForIdle()
    assertEquals(string(R.string.patientHomeErrorNoProfile), ShadowToast.getTextOfLatestToast())

    stateFlow.value = stateFlow.value.copy(errorMsgRes = R.string.patientHomeErrorLoadProfile)
    composeTestRule.waitForIdle()
    assertEquals(string(R.string.patientHomeErrorLoadProfile), ShadowToast.getTextOfLatestToast())

    verify(exactly = 2) { viewModel.clearErrorMsg() }
  }

  @Test
  fun errorPresentAtStartIsShownAndCleared() {
    stateFlow.value = stateFlow.value.copy(errorMsgRes = R.string.patientHomeErrorLoadProfile)
    setScreen()
    composeTestRule.waitForIdle()

    assertEquals(string(R.string.patientHomeErrorLoadProfile), ShadowToast.getTextOfLatestToast())
    verify { viewModel.clearErrorMsg() }
  }

  @Test
  fun sameErrorShownAgainAfterBeingCleared() {
    setScreen()

    repeat(2) {
      stateFlow.value = stateFlow.value.copy(errorMsgRes = R.string.patientHomeErrorNoProfile)
      composeTestRule.waitForIdle()
      stateFlow.value = stateFlow.value.copy(errorMsgRes = null)
      composeTestRule.waitForIdle()
    }

    verify(exactly = 2) { viewModel.clearErrorMsg() }
  }

  // ---------- Preview ----------

  @Test
  fun previewRendersWithoutCrashing() {
    composeTestRule.setContent { PatientHomeScreenPreview() }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TITLE).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU)
        .assertIsDisplayed()
  }

  @Test
  fun previewRepository_acceptsProfileCreation() = runBlocking {
    val created =
        PreviewPatientHomeProfileRepository.createProfile(
            Person(firstName = "Ada", lastName = "Lovelace"),
            Role.PATIENT,
        )

    assertTrue(created.isSuccess)
  }

  @Test
  fun previewRepository_acceptsARoleCorrection() = runBlocking {
    val updated =
        PreviewPatientHomeProfileRepository.updateOnboardingProfile(
            Person(firstName = "Ada", lastName = "Lovelace"),
            Role.CAREGIVER,
        )

    assertTrue(updated.isSuccess)
  }

  @Test
  fun previewRepository_readsNoCurrentProfile() = runBlocking {
    val current = PreviewPatientHomeProfileRepository.getCurrentProfile().getOrNull()

    assertEquals(null, current)
  }

  @Test
  fun previewRepository_readsNoProfileForAnotherAccount() = runBlocking {
    val other = PreviewPatientHomeProfileRepository.getProfile("other").getOrNull()

    assertEquals(null, other)
  }

  @Test
  @Config(qualifiers = "fr")
  fun screenIsShownInFrench() {
    setScreen()

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TITLE).assertTextEquals("Accueil")
    composeTestRule
        .onNodeWithTag(PatientHomeTestTags.GREETINGS)
        .assertTextEquals("Bonjour Alice\u00A0!")
  }
}
