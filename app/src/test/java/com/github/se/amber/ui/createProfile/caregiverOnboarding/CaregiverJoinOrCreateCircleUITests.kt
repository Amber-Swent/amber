// this code was written by Alaabrine
// with the aid of AI coding agent
package com.github.se.amber.ui.createProfile.caregiverOnboarding

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.accountCreation.caregiverOnboarding.CaregiverJoinOrCreateCircle
import com.github.se.amber.ui.accountCreation.caregiverOnboarding.CaregiverJoinOrCreateCircleTestTags
import com.github.se.amber.ui.accountCreation.caregiverOnboarding.CaregiverJoinOrCreateCircleViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CaregiverJoinOrCreateCircleUITests {
  @get:Rule val composeTestRule = createComposeRule()

  private var createCircleCount = 0
  private var joinCircleCount = 0
  private var backCount = 0

  private fun profileRepository(displayName: String = DISPLAY_NAME): ProfileRepository =
      object : ProfileRepository {
        override suspend fun createProfile(person: Person, role: Role) = Result.success(Unit)

        override suspend fun getCurrentProfile() =
            Result.success(
                UserProfile(
                    uid = "uid",
                    role = Role.CAREGIVER,
                    person = Person(id = "uid", firstName = displayName, lastName = "Hopper"),
                ),
            )

        override suspend fun getProfile(uid: String) = Result.success(null)
      }

  private fun setScreen(
      displayName: String = DISPLAY_NAME,
      viewModel: CaregiverJoinOrCreateCircleViewModel =
          CaregiverJoinOrCreateCircleViewModel(
              profileRepository = profileRepository(displayName),
          ),
  ) {
    createCircleCount = 0
    joinCircleCount = 0
    backCount = 0
    composeTestRule.setContent {
      CaregiverJoinOrCreateCircle(
          viewModel = viewModel,
          onBack = { backCount++ },
          onCreateCircle = { createCircleCount++ },
          onJoinCircle = { joinCircleCount++ },
      )
    }
    composeTestRule.waitForIdle()
  }

  // ---------- Back (→ CreateProfile) ----------

  @Test
  fun backButton_isDisplayedAndClickable() {
    setScreen()

    composeTestRule
        .onNodeWithTag(CaregiverJoinOrCreateCircleTestTags.BACK_BUTTON.name)
        .assertIsDisplayed()
        .assertHasClickAction()
        .assertTextEquals(BACK_LABEL)
  }

  @Test
  fun backButton_click_invokesOnBackOnly() {
    setScreen()

    composeTestRule
        .onNodeWithTag(CaregiverJoinOrCreateCircleTestTags.BACK_BUTTON.name)
        .performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, backCount)
    assertEquals(0, createCircleCount)
    assertEquals(0, joinCircleCount)
  }

  // ---------- Branding ----------

  @Test
  fun logoAndBrandName_areDisplayed() {
    setScreen()

    composeTestRule.onNodeWithTag(CaregiverJoinOrCreateCircleTestTags.LOGO.name).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(CaregiverJoinOrCreateCircleTestTags.BRAND_NAME.name)
        .assertIsDisplayed()
        .assertTextEquals("Amber")
  }

  // ---------- Welcome / instruction ----------

  @Test
  fun welcomeHeader_displaysNameFromViewModel() {
    setScreen(displayName = DISPLAY_NAME)

    composeTestRule
        .onNodeWithTag(CaregiverJoinOrCreateCircleTestTags.WELCOME_HEADER.name)
        .assertIsDisplayed()
        .assertTextEquals("Welcome, $DISPLAY_NAME")
  }

  @Test
  fun instructionText_isDisplayed() {
    setScreen()

    composeTestRule
        .onNodeWithTag(CaregiverJoinOrCreateCircleTestTags.INSTRUCTION_TEXT.name)
        .assertIsDisplayed()
        .assertTextEquals(INSTRUCTION_LABEL)
  }

  // ---------- Create / Join buttons ----------

  @Test
  fun createAndJoinButtons_areBothDisplayed() {
    setScreen()

    composeTestRule
        .onNodeWithTag(CaregiverJoinOrCreateCircleTestTags.CREATE_BUTTON.name)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(CaregiverJoinOrCreateCircleTestTags.JOIN_BUTTON.name)
        .assertIsDisplayed()
  }

  @Test
  fun createAndJoinButtons_areDistinctAndClickable() {
    setScreen()

    assertNotEquals(
        CaregiverJoinOrCreateCircleTestTags.CREATE_BUTTON,
        CaregiverJoinOrCreateCircleTestTags.JOIN_BUTTON,
    )

    composeTestRule
        .onNodeWithTag(CaregiverJoinOrCreateCircleTestTags.CREATE_BUTTON.name)
        .assertHasClickAction()
    composeTestRule
        .onNodeWithTag(CaregiverJoinOrCreateCircleTestTags.JOIN_BUTTON.name)
        .assertHasClickAction()
  }

  @Test
  fun createButton_showsCreateLabel() {
    setScreen()

    composeTestRule
        .onNodeWithTag(CaregiverJoinOrCreateCircleTestTags.CREATE_BUTTON.name)
        .assertTextEquals(CREATE_CIRCLE_LABEL)
  }

  @Test
  fun joinButton_showsJoinLabel() {
    setScreen()

    composeTestRule
        .onNodeWithTag(CaregiverJoinOrCreateCircleTestTags.JOIN_BUTTON.name)
        .assertTextEquals(JOIN_CIRCLE_LABEL)
  }

  // ---------- Or divider / invite hint ----------

  @Test
  fun orDivider_isDisplayed() {
    setScreen()

    composeTestRule
        .onNodeWithTag(CaregiverJoinOrCreateCircleTestTags.OR_DIVIDER.name)
        .assertIsDisplayed()
  }

  @Test
  fun inviteLinkHint_isDisplayed() {
    setScreen()

    composeTestRule
        .onNodeWithTag(CaregiverJoinOrCreateCircleTestTags.INVITE_LINK_HINT.name)
        .assertIsDisplayed()
        .assertTextEquals(INVITE_LINK_HINT_LABEL)
  }

  // ---------- Navigation callbacks (create → CreateCareCircle, join → EnterInvitation) ----------

  @Test
  fun createButton_click_invokesOnCreateCircleOnly() {
    setScreen()

    composeTestRule
        .onNodeWithTag(CaregiverJoinOrCreateCircleTestTags.CREATE_BUTTON.name)
        .performClick()
    composeTestRule.waitForIdle()

    assertEquals(0, backCount)
    assertEquals(1, createCircleCount)
    assertEquals(0, joinCircleCount)
  }

  @Test
  fun joinButton_click_invokesOnJoinCircleOnly() {
    setScreen()

    composeTestRule
        .onNodeWithTag(CaregiverJoinOrCreateCircleTestTags.JOIN_BUTTON.name)
        .performClick()
    composeTestRule.waitForIdle()

    assertEquals(0, backCount)
    assertEquals(0, createCircleCount)
    assertEquals(1, joinCircleCount)
  }

  @Test
  fun initially_doesNotInvokeNavigationCallbacks() {
    setScreen()
    composeTestRule.waitForIdle()

    assertEquals(0, backCount)
    assertEquals(0, createCircleCount)
    assertEquals(0, joinCircleCount)
  }

  @Test
  fun screen_withDefaultCallbacks_rendersWithoutInvokingNavigation() {
    // Construct outside setContent: lint forbids ViewModel constructors inside composables.
    val viewModel =
        CaregiverJoinOrCreateCircleViewModel(profileRepository = profileRepository())
    composeTestRule.setContent { CaregiverJoinOrCreateCircle(viewModel = viewModel) }
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(CaregiverJoinOrCreateCircleTestTags.CREATE_BUTTON.name)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(CaregiverJoinOrCreateCircleTestTags.JOIN_BUTTON.name)
        .assertIsDisplayed()
  }

  private companion object {
    const val DISPLAY_NAME = "Sarah"
    const val CREATE_CIRCLE_LABEL = "Create a new care circle"
    const val JOIN_CIRCLE_LABEL = "Join an existing care circle"
    const val INSTRUCTION_LABEL = "How will you enter your Care Circle?"
    const val INVITE_LINK_HINT_LABEL = "Use your invite link"
    const val BACK_LABEL = "Back"
  }
}
