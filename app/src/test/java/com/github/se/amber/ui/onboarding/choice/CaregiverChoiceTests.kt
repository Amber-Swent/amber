// this code was written by Alaabrine
// with the aid of AI coding agent
package com.github.se.amber.ui.createProfile.caregiverOnboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.onboarding.choice.CaregiverChoiceScreen
import com.github.se.amber.ui.onboarding.choice.CaregiverChoiceTestTags
import com.github.se.amber.ui.onboarding.choice.CaregiverChoiceViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CaregiverChoiceUITests {
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
      viewModel: CaregiverChoiceViewModel =
          CaregiverChoiceViewModel(
              profileRepository = profileRepository(displayName),
          ),
  ) {
    createCircleCount = 0
    joinCircleCount = 0
    backCount = 0
    composeTestRule.setContent {
      CaregiverChoiceScreen(
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
  fun backButton_click_invokesOnBackOnly() {
    setScreen()

    composeTestRule.onNodeWithTag(CaregiverChoiceTestTags.BACK_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, backCount)
    assertEquals(0, createCircleCount)
    assertEquals(0, joinCircleCount)
  }

  // ---------- Welcome (name from ViewModel) ----------

  @Test
  fun welcomeHeader_displaysNameFromViewModel() {
    setScreen(displayName = DISPLAY_NAME)

    composeTestRule
        .onNodeWithTag(CaregiverChoiceTestTags.WELCOME_HEADER)
        .assertIsDisplayed()
        .assertTextEquals("Welcome, $DISPLAY_NAME")
  }

  // ---------- Navigation callbacks (create → CreateCareCircle, join → EnterInvitation) ----------

  @Test
  fun createButton_click_invokesOnCreateCircleOnly() {
    setScreen()

    composeTestRule.onNodeWithTag(CaregiverChoiceTestTags.CREATE_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(0, backCount)
    assertEquals(1, createCircleCount)
    assertEquals(0, joinCircleCount)
  }

  @Test
  fun joinButton_click_invokesOnJoinCircleOnly() {
    setScreen()

    composeTestRule.onNodeWithTag(CaregiverChoiceTestTags.JOIN_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(0, backCount)
    assertEquals(0, createCircleCount)
    assertEquals(1, joinCircleCount)
  }

  private companion object {
    const val DISPLAY_NAME = "Sarah"
  }
}
