// this code was written by Alaabrine
// with the aid of AI coding agent
package com.github.se.amber.ui.onboarding.choice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
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

  private fun profileRepository(displayName: String = "Sahra"): ProfileRepository =
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
      displayName: String = "Sahra",
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

  @Test
  fun backButton_click_invokesOnBackOnly() {
    setScreen()

    composeTestRule.onNodeWithTag(CaregiverChoiceTestTags.BACK_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, backCount)
    assertEquals(0, createCircleCount)
    assertEquals(0, joinCircleCount)
  }

  @Test
  fun createCircleButton_click_invokesOnBackOnly() {
    setScreen()

    composeTestRule.onNodeWithTag(CaregiverChoiceTestTags.CREATE_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(0, backCount)
    assertEquals(1, createCircleCount)
    assertEquals(0, joinCircleCount)
  }

  @Test
  fun joinButton_click_invokesOnBackOnly() {
    setScreen()

    composeTestRule.onNodeWithTag(CaregiverChoiceTestTags.JOIN_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(0, backCount)
    assertEquals(0, createCircleCount)
    assertEquals(1, joinCircleCount)
  }

  // ---------- isDisplay ----------

  @Test
  fun welcomeHeader_isDisplay() {
    setScreen()

    composeTestRule.onNodeWithTag(CaregiverChoiceTestTags.WELCOME_HEADER).assertIsDisplayed()
  }

  @Test
  fun question_isDisplay() {
    setScreen()

    composeTestRule.onNodeWithTag(CaregiverChoiceTestTags.QUESTION).assertIsDisplayed()
  }

  @Test
  fun createCircleButton_isDisplay() {
    setScreen()

    composeTestRule.onNodeWithTag(CaregiverChoiceTestTags.CREATE_BUTTON).assertIsDisplayed()
  }

  @Test
  fun joinButton_isDisplay() {
    setScreen()

    composeTestRule.onNodeWithTag(CaregiverChoiceTestTags.JOIN_BUTTON).assertIsDisplayed()
  }

  @Test
  fun backButton_isDisplay() {
    setScreen()

    composeTestRule.onNodeWithTag(CaregiverChoiceTestTags.BACK_BUTTON).assertIsDisplayed()
  }

  @Test
  fun informationText_isDisplay() {
    setScreen()

    composeTestRule.onNodeWithTag(CaregiverChoiceTestTags.INFORMATION).assertIsDisplayed()
  }
}
