// This file was written with the aid of AI.
package com.github.se.amber.ui.onboarding

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.se.amber.ui.theme.AmberAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tests the state-hoisted care circle form without a repository or emulator. */
@RunWith(RobolectricTestRunner::class)
class CreateCareCircleScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private var submittedCount = 0
  private var enteredName = ""

  private fun showContent(state: CreateCareCircleUiState = CreateCareCircleUiState()) {
    composeTestRule.setContent {
      AmberAppTheme(darkTheme = false, dynamicColor = false) {
        CreateCareCircleContent(
            uiState = state,
            onNameChange = { enteredName = it },
            onSubmit = { submittedCount++ },
        )
      }
    }
  }

  @Test
  fun showsEnteredName() {
    showContent(CreateCareCircleUiState(name = "Family"))
    composeTestRule.onNodeWithTag(CreateCareCircleTestTags.NAME_FIELD).assertTextContains("Family")
  }

  @Test
  fun typingNameCallsChangeCallback() {
    showContent()
    composeTestRule.onNodeWithTag(CreateCareCircleTestTags.NAME_FIELD).performTextInput("Family")
    assertEquals("Family", enteredName)
  }

  @Test
  fun createButtonCallsSubmitCallback() {
    showContent()
    composeTestRule.onNodeWithTag(CreateCareCircleTestTags.SUBMIT_BUTTON).performClick()
    assertEquals(1, submittedCount)
  }

  @Test
  fun showsValidationErrorAsSupportingText() {
    showContent(CreateCareCircleUiState(nameError = "Circle name cannot be empty"))
    composeTestRule
        .onNodeWithTag(CreateCareCircleTestTags.NAME_ERROR, useUnmergedTree = true)
        .assertTextEquals("Circle name cannot be empty")
  }

  @Test
  fun loadingDisablesCreateButton() {
    showContent(CreateCareCircleUiState(name = "Family", isLoading = true))
    composeTestRule.onNodeWithTag(CreateCareCircleTestTags.SUBMIT_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun loadingDisablesNameField() {
    showContent(CreateCareCircleUiState(name = "Family", isLoading = true))
    composeTestRule.onNodeWithTag(CreateCareCircleTestTags.NAME_FIELD).assertIsNotEnabled()
  }

  @Test
  fun loadingShowsCreationLabel() {
    showContent(CreateCareCircleUiState(name = "Family", isLoading = true))
    composeTestRule.onNodeWithText("Creation...").assertExists()
  }

  @Test
  fun createdStateKeepsCreateButtonDisabled() {
    showContent(CreateCareCircleUiState(name = "Family", isCreated = true))
    composeTestRule.onNodeWithTag(CreateCareCircleTestTags.SUBMIT_BUTTON).assertIsNotEnabled()
  }
}
