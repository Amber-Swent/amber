// This file was written with the aid of AI.
// Modified by Yuhan YANG, with assistance from Cursor.
package com.github.se.amber.ui.onboarding

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.se.amber.data.onboarding.OnboardingRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.ui.caregiver.DisplayCaregiverButtonTestTags
import com.github.se.amber.ui.onboarding.createCareCircle.CreateCareCircleScreen
import com.github.se.amber.ui.onboarding.createCareCircle.CreateCareCircleTestTags
import com.github.se.amber.ui.onboarding.createCareCircle.CreateCareCircleViewModel
import com.github.se.amber.ui.theme.AmberAppTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tests the care circle form with a local repository and no emulator. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CreateCareCircleScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var repository: FakeOnboardingRepository
  private lateinit var viewModel: CreateCareCircleViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    repository = FakeOnboardingRepository()
    viewModel = CreateCareCircleViewModel(repository)
    composeTestRule.setContent {
      AmberAppTheme(darkTheme = false, dynamicColor = false) {
        CreateCareCircleScreen(viewModel = viewModel)
      }
    }
  }

  @After
  fun tearDown() {
    repository.gate?.cancel()
    Dispatchers.resetMain()
  }

  @Test
  fun showsEnteredName() {
    viewModel.onNameChange("Family")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(CreateCareCircleTestTags.NAME_FIELD).assertTextContains("Family")
  }

  @Test
  fun typingNameUpdatesTheField() {
    composeTestRule.onNodeWithTag(CreateCareCircleTestTags.NAME_FIELD).performTextInput("Family")
    assertEquals("Family", viewModel.uiState.value.name)
  }

  @Test
  fun createButtonSubmitsTheName() {
    viewModel.onNameChange("Family")
    composeTestRule.onNodeWithTag(DisplayCaregiverButtonTestTags.BUTTON).performClick()
    assertEquals(listOf("Family"), repository.submittedNames)
  }

  @Test
  fun showsValidationErrorAsSupportingText() {
    composeTestRule.onNodeWithTag(DisplayCaregiverButtonTestTags.BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(CreateCareCircleTestTags.NAME_ERROR, useUnmergedTree = true)
        .assertTextEquals("Circle name cannot be empty")
  }

  @Test
  fun loadingDisablesCreateButton() {
    repository.gate = CompletableDeferred()
    viewModel.onNameChange("Family")
    composeTestRule.onNodeWithTag(DisplayCaregiverButtonTestTags.BUTTON).performClick()
    composeTestRule.onNodeWithTag(DisplayCaregiverButtonTestTags.BUTTON).assertIsNotEnabled()
  }

  @Test
  fun loadingDisablesNameField() {
    repository.gate = CompletableDeferred()
    viewModel.onNameChange("Family")
    composeTestRule.onNodeWithTag(DisplayCaregiverButtonTestTags.BUTTON).performClick()
    composeTestRule.onNodeWithTag(CreateCareCircleTestTags.NAME_FIELD).assertIsNotEnabled()
  }

  @Test
  fun loadingShowsCreationLabel() {
    repository.gate = CompletableDeferred()
    viewModel.onNameChange("Family")
    composeTestRule.onNodeWithTag(DisplayCaregiverButtonTestTags.BUTTON).performClick()
    composeTestRule.onNodeWithText("Creation...").assertExists()
  }

  @Test
  fun createdStateKeepsCreateButtonDisabled() {
    viewModel.onNameChange("Family")
    composeTestRule.onNodeWithTag(DisplayCaregiverButtonTestTags.BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(DisplayCaregiverButtonTestTags.BUTTON).assertIsNotEnabled()
  }

  private class FakeOnboardingRepository : OnboardingRepository {
    val submittedNames = mutableListOf<String>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun createInitialCareCircle(name: String): Result<CareCircle> {
      submittedNames.add(name)
      gate?.await()
      return Result.success(CareCircle(id = "circle-1", name = name))
    }
  }
}
