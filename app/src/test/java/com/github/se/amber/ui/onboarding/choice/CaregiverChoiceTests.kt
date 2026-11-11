// Written by Alaabrine with the aid of AI coding agent
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.onboarding.choice

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class CaregiverChoiceUITests {
  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  private var createCircleCount = 0
  private var joinCircleCount = 0
  private var backCount = 0

  private fun profileRepository(displayName: String = "Sahra"): ProfileRepository =
      object : ProfileRepository {
        override suspend fun createProfile(person: Person, role: Role) = Result.success(Unit)

        override suspend fun updateOnboardingProfile(person: Person, role: Role) =
            Result.success(Unit)

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
  fun cancel_callsOnBackOnceAndDoesNotCreateOrJoin() {
    setScreen()

    composeTestRule.onNodeWithText(context.getString(R.string.navigationCancel)).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON)
        .assertIsDisplayed()
        .performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, backCount)
    assertEquals(0, createCircleCount)
    assertEquals(0, joinCircleCount)
  }

  @Test
  fun createCircleButton_click_invokesOnCreateCirclekOnly() {
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
  fun previewShowsWelcomeAndCancel() {
    composeTestRule.setContent { CaregiverChoicePreview() }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText(context.getString(R.string.navigationCancel)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(CaregiverChoiceTestTags.WELCOME_HEADER).assertIsDisplayed()
  }

  @Test
  fun informationText_isDisplay() {
    setScreen()

    composeTestRule.onNodeWithTag(CaregiverChoiceTestTags.INFORMATION).assertIsDisplayed()
  }

  // ---------- Text and translation ----------

  @Test
  fun welcomeHeader_putsTheFirstNameInTheSentence() {
    setScreen(displayName = "Sahra")

    composeTestRule
        .onNodeWithTag(CaregiverChoiceTestTags.WELCOME_HEADER)
        .assertTextEquals(context.getString(R.string.caregiverChoiceWelcomeName, "Sahra"))
  }

  @Test
  fun welcomeHeader_withoutAName_showsWelcomeAlone() {
    setScreen(displayName = "")

    composeTestRule
        .onNodeWithTag(CaregiverChoiceTestTags.WELCOME_HEADER)
        .assertTextEquals(context.getString(R.string.caregiverChoiceWelcome))
  }

  @Test
  @Config(qualifiers = "fr")
  fun welcomeHeader_isInFrenchWhenThePhoneIsInFrench() {
    setScreen(displayName = "Sahra")

    composeTestRule
        .onNodeWithTag(CaregiverChoiceTestTags.WELCOME_HEADER)
        .assertTextEquals("Bienvenue, Sahra")
  }
}
