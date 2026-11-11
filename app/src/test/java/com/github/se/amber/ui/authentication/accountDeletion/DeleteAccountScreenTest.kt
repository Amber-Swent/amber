// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.authentication.accountDeletion

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.credentials.CredentialManager
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.utils.UiText
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
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private fun string(id: Int, vararg args: Any) =
      ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

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
    composeTestRule
        .onNodeWithTag(DeleteAccountTestTags.EXPLANATION)
        .assertTextEquals(string(R.string.deleteAccountExplanation))
    composeTestRule.onNodeWithText(string(R.string.deleteAccountCheckingLabel)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(DeleteAccountTestTags.BUTTON).assertDoesNotExist()
    verify(exactly = 1) { viewModel.loadWarnings() }
  }

  @Test
  fun warningLoadCompletionReplacesLoadingWithConfirmation() {
    setScreen()
    composeTestRule.onNodeWithTag(DeleteAccountTestTags.BUTTON).assertDoesNotExist()

    stateFlow.value =
        DeleteAccountUIState(
            isLoading = false,
            warningsLoaded = true,
            warnings =
                listOf(
                    UiText.Resource(
                        R.string.deleteAccountLastCaregiverWarning,
                        listOf("Arthur's family"),
                    )
                ),
        )
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText(string(R.string.deleteAccountCheckingLabel)).assertDoesNotExist()
    composeTestRule
        .onNodeWithText(string(R.string.deleteAccountLastCaregiverWarning, "Arthur's family"))
        .assertIsDisplayed()
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
  fun startedDeletionReplacesActionsButKeepsTitle() {
    stateFlow.value = DeleteAccountUIState(warningsLoaded = true, isLoading = false)
    setScreen()
    composeTestRule.onNodeWithTag(DeleteAccountTestTags.BUTTON).assertIsDisplayed()

    stateFlow.value = stateFlow.value.copy(isDeleting = true)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(DeleteAccountTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.deleteAccountProgress)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(DeleteAccountTestTags.BUTTON).assertDoesNotExist()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).assertDoesNotExist()
  }

  @Test
  fun systemBackIsConsumedWhileDeleting() {
    stateFlow.value =
        DeleteAccountUIState(warningsLoaded = true, isLoading = false, isDeleting = true)
    composeTestRule.activity.onBackPressedDispatcher.addCallback(
        composeTestRule.activity,
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            backCount++
          }
        },
    )
    setScreen()

    composeTestRule.onNodeWithText(string(R.string.deleteAccountProgress)).assertIsDisplayed()
    composeTestRule.runOnIdle { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }

    assertEquals(0, backCount)
  }

  @Test
  fun failedWarningLoadOffersRetry() {
    stateFlow.value = DeleteAccountUIState(isLoading = false)
    setScreen()

    composeTestRule.onNodeWithText(string(R.string.deleteAccountRetryLabel)).performClick()

    verify(exactly = 2) { viewModel.loadWarnings() }
  }

  @Test
  fun finishDeletingActionCallsViewModel() {
    stateFlow.value =
        DeleteAccountUIState(warningsLoaded = true, isLoading = false, localCleanupNeeded = true)
    setScreen()

    composeTestRule.onNodeWithText(string(R.string.deleteAccountCleanupLabel)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(DeleteAccountTestTags.BUTTON).performClick()

    verify(exactly = 1) { viewModel.deleteAccount(any(), credentialManager) }
  }

  @Test
  fun errorIsClearedAfterBeingDisplayed() {
    stateFlow.value = DeleteAccountUIState(errorMessageRes = R.string.deleteAccountNetworkError)
    setScreen()
    composeTestRule.waitForIdle()

    assertEquals(string(R.string.deleteAccountNetworkError), ShadowToast.getTextOfLatestToast())
    verify(exactly = 1) { viewModel.clearError() }
  }
}
