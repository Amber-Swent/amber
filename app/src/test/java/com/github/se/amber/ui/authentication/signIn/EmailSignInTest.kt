/* This file was written with the aid of AI.
 * Modified by nehachak1, with assistance from
 * Claude Code
 */
package com.github.se.amber.ui.authentication

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.credentials.CredentialManager
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.github.se.amber.model.authentication.AuthUser
import com.github.se.amber.ui.authentication.signIn.AuthenticationComponentTestTags
import com.github.se.amber.ui.authentication.signIn.EmailSignInScreen
import com.github.se.amber.ui.authentication.signIn.EmailSignInTestTags
import com.github.se.amber.ui.authentication.signIn.EmailSignInUIState
import com.github.se.amber.ui.authentication.signIn.EmailSignInViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the email sign-in screen's rendering, input, loading state, and callbacks with Compose.
 *
 * A real [com.github.se.amber.ui.authentication.signIn.EmailSignInViewModel] covers email entry,
 * Continue validation, and existing-user navigation. Mocked ViewModels isolate Google button clicks
 * and error-clearing behavior.
 */
@RunWith(RobolectricTestRunner::class)
class EmailSignInScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val repository = mockk<AuthRepository>()
  private val credentialManager = mockk<CredentialManager>()
  private lateinit var viewModel: EmailSignInViewModel
  private var continueCount = 0
  private var googleSignInCount = 0

  /** Creates a ViewModel with a fixed [state] for UI states and interaction checks. */
  private fun mockViewModel(state: EmailSignInUIState): EmailSignInViewModel =
      mockk<EmailSignInViewModel>(relaxed = true).also {
        every { it.uiState } returns MutableStateFlow(state)
      }

  @Before
  fun setUp() {
    every { repository.currentUser } returns null
    viewModel = EmailSignInViewModel(repository)
    continueCount = 0
    googleSignInCount = 0
  }

  /** Composes the screen with [model] and counts navigation callback invocations. */
  private fun setScreen(model: EmailSignInViewModel = viewModel) {
    composeTestRule.setContent {
      EmailSignInScreen(
          credentialManager = credentialManager,
          emailSignInViewModel = model,
          onContinue = { continueCount++ },
          onGoogleSignIn = { googleSignInCount++ },
      )
    }
  }

  // Many small tests so that debugging is easier if several assertions fail
  @Test
  fun screenShowsHeader() {
    setScreen()
    composeTestRule.onNodeWithTag(AuthenticationComponentTestTags.HEADER).assertIsDisplayed()
  }

  @Test
  fun screenShowsTermsFooter() {
    setScreen()
    composeTestRule.onNodeWithTag(AuthenticationComponentTestTags.TERMS_FOOTER).assertIsDisplayed()
  }

  @Test
  fun screenShowsHeading() {
    setScreen()
    composeTestRule.onNodeWithTag(EmailSignInTestTags.HEADING).assertIsDisplayed()
  }

  @Test
  fun screenShowsEmailField() {
    setScreen()
    composeTestRule.onNodeWithTag(EmailSignInTestTags.EMAIL_FIELD).assertIsDisplayed()
  }

  @Test
  fun continueButtonIsEnabledByDefault() {
    setScreen()
    composeTestRule.onNodeWithTag(EmailSignInTestTags.CONTINUE_BUTTON).assertIsEnabled()
  }

  @Test
  fun googleButtonIsEnabledByDefault() {
    setScreen()
    composeTestRule.onNodeWithTag(EmailSignInTestTags.GOOGLE_BUTTON).assertIsEnabled()
  }

  @Test
  fun orDividerIsDisplayed() {
    setScreen()
    composeTestRule.onNodeWithTag(EmailSignInTestTags.OR_DIVIDER).assertIsDisplayed()
  }

  @Test
  fun googleButtonShowsSigningInTextDuringLoading() {
    setScreen(mockViewModel(EmailSignInUIState(isLoading = true)))
    composeTestRule
        .onNodeWithTag(EmailSignInTestTags.GOOGLE_BUTTON)
        .assertTextContains("Signing in…", substring = true)
  }

  @Test
  fun typingEmailPropagatesInputToViewModel() {
    setScreen()

    composeTestRule
        .onNodeWithTag(EmailSignInTestTags.EMAIL_FIELD)
        .performTextInput("person@example.com")

    composeTestRule
        .onNodeWithTag(EmailSignInTestTags.EMAIL_FIELD)
        .assertTextContains("person@example.com")
    assertEquals("person@example.com", viewModel.uiState.value.email)
  }

  @Test
  fun continueWithBlankEmailDoesNotInvokeNavigationCallback() {
    setScreen()

    composeTestRule.onNodeWithTag(EmailSignInTestTags.CONTINUE_BUTTON).performClick()

    assertEquals(0, continueCount)
  }

  @Test
  fun continueWithEmailInvokesNavigationCallbackOnce() {
    setScreen()
    composeTestRule
        .onNodeWithTag(EmailSignInTestTags.EMAIL_FIELD)
        .performTextInput("person@example.com")

    composeTestRule.onNodeWithTag(EmailSignInTestTags.CONTINUE_BUTTON).performClick()

    assertEquals(1, continueCount)
  }

  @Test
  fun googleButtonIsDisabledDuringLoading() {
    setScreen(mockViewModel(EmailSignInUIState(isLoading = true)))
    composeTestRule.onNodeWithTag(EmailSignInTestTags.GOOGLE_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun continueButtonStaysEnabledDuringLoading() {
    setScreen(mockViewModel(EmailSignInUIState(isLoading = true)))
    composeTestRule.onNodeWithTag(EmailSignInTestTags.CONTINUE_BUTTON).assertIsEnabled()
  }

  @Test
  fun existingUserInvokesGoogleNavigationCallback() {
    every { repository.currentUser } returns AuthUser("existing-user")
    setScreen(EmailSignInViewModel(repository))
    composeTestRule.waitForIdle()

    assertEquals(1, googleSignInCount)
  }

  @Test
  fun googleButtonClickStartsGoogleSignIn() {
    val model = mockViewModel(EmailSignInUIState())
    setScreen(model)

    composeTestRule.onNodeWithTag(EmailSignInTestTags.GOOGLE_BUTTON).performClick()

    verify(exactly = 1) { model.signInWithGoogle(any(), credentialManager) }
  }

  @Test
  fun displayedErrorIsClearedInViewModel() {
    val model = mockViewModel(EmailSignInUIState(errorMessage = "Something failed"))
    setScreen(model)
    composeTestRule.waitForIdle()

    verify(exactly = 1) { model.clearError() }
  }

  @Test
  fun noExistingUserDoesNotInvokeGoogleNavigationCallback() {
    setScreen()
    composeTestRule.waitForIdle()

    assertEquals(0, googleSignInCount)
  }

  @Test
  fun screenWithDefaultArgumentsUsesProviderRepository() {
    every { repository.currentUser } returns AuthUser("existing-user")
    mockkObject(AuthRepositoryProvider)
    every { AuthRepositoryProvider.repository } returns repository
    try {
      composeTestRule.setContent { EmailSignInScreen() }
      composeTestRule
          .onNodeWithTag(EmailSignInTestTags.EMAIL_FIELD)
          .performTextInput("person@example.com")
      composeTestRule.onNodeWithTag(EmailSignInTestTags.CONTINUE_BUTTON).performClick()

      composeTestRule.onNodeWithTag(EmailSignInTestTags.HEADING).assertIsDisplayed()
      verify { repository.currentUser }
    } finally {
      unmockkObject(AuthRepositoryProvider)
    }
  }
}
