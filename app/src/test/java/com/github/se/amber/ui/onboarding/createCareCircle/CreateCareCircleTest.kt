// Written by MeiliV, with assistance from OpenAI Codex
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.onboarding

import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.data.onboarding.OnboardingError
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
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** Tests the care circle form with a local repository and no emulator. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CreateCareCircleScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** Text of [id] in the current test language. */
  private fun string(id: Int): String =
      ApplicationProvider.getApplicationContext<Context>().getString(id)

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
        .assertTextEquals(string(R.string.createCareCircleNameRequired))
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
    composeTestRule.onNodeWithText(string(R.string.createCareCircleCreating)).assertExists()
  }

  @Test
  fun createdStateKeepsCreateButtonDisabled() {
    viewModel.onNameChange("Family")
    composeTestRule.onNodeWithTag(DisplayCaregiverButtonTestTags.BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(DisplayCaregiverButtonTestTags.BUTTON).assertIsNotEnabled()
  }

  @Test
  @Config(qualifiers = "fr")
  fun validationErrorIsShownInFrench() {
    composeTestRule.onNodeWithTag(DisplayCaregiverButtonTestTags.BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(CreateCareCircleTestTags.NAME_ERROR, useUnmergedTree = true)
        .assertTextEquals("Le nom du cercle ne peut pas être vide")
  }

  @Test
  @Config(qualifiers = "fr")
  fun creationLabelIsShownInFrench() {
    repository.gate = CompletableDeferred()
    viewModel.onNameChange("Famille")
    composeTestRule.onNodeWithTag(DisplayCaregiverButtonTestTags.BUTTON).performClick()
    composeTestRule.onNodeWithText("Création…").assertExists()
  }

  @Test
  @Config(qualifiers = "fr")
  fun failureToastIsShownInFrench() {
    repository.failure = OnboardingError.Network
    viewModel.onNameChange("Famille")
    composeTestRule.onNodeWithTag(DisplayCaregiverButtonTestTags.BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(
        "Vous êtes hors ligne. Vérifiez votre connexion, puis réessayez.",
        ShadowToast.getTextOfLatestToast(),
    )
  }

  private class FakeOnboardingRepository : OnboardingRepository {
    val submittedNames = mutableListOf<String>()
    var gate: CompletableDeferred<Unit>? = null
    var failure: Throwable? = null

    override suspend fun createInitialCareCircle(name: String): Result<CareCircle> {
      submittedNames.add(name)
      gate?.await()
      failure?.let {
        return Result.failure(it)
      }
      return Result.success(CareCircle(id = "circle-1", name = name))
    }
  }
}
