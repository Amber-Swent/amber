// Written by MeiliV, with assistance from OpenAI Codex.
package com.github.se.amber.ui.authentication.accountDeletion

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.credentials.CredentialManager
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
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
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class DeleteAccountScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val credentialManager = mockk<CredentialManager>()
  private lateinit var viewModel: DeleteAccountViewModel
  private lateinit var stateFlow: MutableStateFlow<DeleteAccountUIState>
  private var backCount = 0

  @Before
  fun setUp() {
    stateFlow = MutableStateFlow(DeleteAccountUIState())
    viewModel = mockk(relaxed = true)
    every { viewModel.uiState } returns stateFlow
    backCount = 0
  }

  private fun setScreen() {
    composeTestRule.setContent {
      AmberAppTheme {
        DeleteAccountScreen(
            viewModel = viewModel,
            credentialManager = credentialManager,
            onBack = { backCount++ },
        )
      }
    }
  }

  @Test
  fun loadingShowsExplanationBeforeConfirmation() {
    setScreen()

    composeTestRule.onNodeWithTag(DeleteAccountTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(DeleteAccountTestTags.EXPLANATION).assertIsDisplayed()
    composeTestRule.onNodeWithText(DELETE_ACCOUNT_CHECKING_LABEL).assertIsDisplayed()
    composeTestRule.onNodeWithTag(DeleteAccountTestTags.BUTTON).assertDoesNotExist()
    verify(exactly = 1) { viewModel.loadWarnings() }
  }

  @Test
  fun loadedWarningsAndConfirmationAreShown() {
    stateFlow.value =
        DeleteAccountUIState(warningsLoaded = true, isLoading = false, warnings = listOf("Last caregiver"))
    setScreen()

    composeTestRule.onNodeWithText("Last caregiver").assertIsDisplayed()
    composeTestRule.onNodeWithTag(DeleteAccountTestTags.BUTTON).assertIsDisplayed()
  }

  @Test
  fun explanationStatesThePermanentFamilyConsequences() {
    setScreen()

    composeTestRule
        .onNodeWithTag(DeleteAccountTestTags.EXPLANATION)
        .assertTextContains("Deletion is permanent", substring = true)
        .assertTextContains("Your uploads and stories are removed", substring = true)
        .assertTextContains("Photos of you uploaded by others stay", substring = true)
        .assertTextContains("removed from appointments", substring = true)
  }

  @Test
  fun warningLoadCompletionReplacesLoadingWithConfirmation() {
    setScreen()
    composeTestRule.onNodeWithTag(DeleteAccountTestTags.BUTTON).assertDoesNotExist()

    stateFlow.value =
        DeleteAccountUIState(
            isLoading = false,
            warningsLoaded = true,
            warnings = listOf("The family will continue with no one to manage it."),
        )
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText(DELETE_ACCOUNT_CHECKING_LABEL).assertDoesNotExist()
    composeTestRule.onNodeWithText("The family will continue with no one to manage it.").assertIsDisplayed()
    composeTestRule.onNodeWithTag(DeleteAccountTestTags.BUTTON).assertIsDisplayed()
  }

  @Test
  fun confirmationCallsViewModel() {
    stateFlow.value = DeleteAccountUIState(warningsLoaded = true, isLoading = false)
    setScreen()

    composeTestRule.onNodeWithTag(DeleteAccountTestTags.BUTTON).performClick()

    verify(exactly = 1) { viewModel.deleteAccount(any(), credentialManager) }
  }

  @Test
  fun cancelCallsBackBeforeDeletion() {
    setScreen()

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    assertEquals(1, backCount)
  }

  @Test
  fun deletingShowsProgressAndHidesCancelAndConfirm() {
    stateFlow.value = DeleteAccountUIState(warningsLoaded = true, isLoading = false, isDeleting = true)
    setScreen()

    composeTestRule.onNodeWithText(DELETE_ACCOUNT_PROGRESS).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).assertDoesNotExist()
    composeTestRule.onNodeWithTag(DeleteAccountTestTags.BUTTON).assertDoesNotExist()
  }

  @Test
  fun startedDeletionReplacesActionsButKeepsTitle() {
    stateFlow.value = DeleteAccountUIState(warningsLoaded = true, isLoading = false)
    setScreen()
    composeTestRule.onNodeWithTag(DeleteAccountTestTags.BUTTON).assertIsDisplayed()

    stateFlow.value = stateFlow.value.copy(isDeleting = true)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(DeleteAccountTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithText(DELETE_ACCOUNT_PROGRESS).assertIsDisplayed()
    composeTestRule.onNodeWithTag(DeleteAccountTestTags.BUTTON).assertDoesNotExist()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).assertDoesNotExist()
  }

  @Test
  fun failedWarningLoadOffersRetry() {
    stateFlow.value = DeleteAccountUIState(isLoading = false)
    setScreen()

    composeTestRule.onNodeWithText(DELETE_ACCOUNT_RETRY_LABEL).performClick()

    verify(exactly = 2) { viewModel.loadWarnings() }
  }

  @Test
  fun localCleanupShowsFinishDeletingAction() {
    stateFlow.value =
        DeleteAccountUIState(warningsLoaded = true, isLoading = false, localCleanupNeeded = true)
    setScreen()

    composeTestRule.onNodeWithText(DELETE_ACCOUNT_CLEANUP_LABEL).assertIsDisplayed()
  }

  @Test
  fun finishDeletingActionCallsViewModel() {
    stateFlow.value =
        DeleteAccountUIState(warningsLoaded = true, isLoading = false, localCleanupNeeded = true)
    setScreen()

    composeTestRule.onNodeWithTag(DeleteAccountTestTags.BUTTON).performClick()

    verify(exactly = 1) { viewModel.deleteAccount(any(), credentialManager) }
  }

  @Test
  fun errorIsClearedAfterBeingDisplayed() {
    stateFlow.value = DeleteAccountUIState(errorMessage = "Network error")
    setScreen()
    composeTestRule.waitForIdle()

    assertEquals("Network error", ShadowToast.getTextOfLatestToast())
    verify(exactly = 1) { viewModel.clearError() }
  }
}
