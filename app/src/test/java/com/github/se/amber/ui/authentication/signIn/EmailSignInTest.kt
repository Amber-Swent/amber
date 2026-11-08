// Written by MeiliV, with assistance from OpenAI Codex
// Modified by nehachak1, with assistance from Claude Code
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.authentication

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.credentials.CredentialManager
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.github.se.amber.model.authentication.AuthUser
import com.github.se.amber.ui.authentication.signIn.AuthenticationComponentTestTags
import com.github.se.amber.ui.authentication.signIn.EmailSignInContent
import com.github.se.amber.ui.authentication.signIn.EmailSignInContentPreview
import com.github.se.amber.ui.authentication.signIn.EmailSignInScreen
import com.github.se.amber.ui.authentication.signIn.EmailSignInTestTags
import com.github.se.amber.ui.authentication.signIn.EmailSignInUIState
import com.github.se.amber.ui.authentication.signIn.EmailSignInViewModel
import com.github.se.amber.ui.onboarding.AmberHeaderTestTags
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val EMAIL_HIDDEN = "Email sign-in is commented out until it is implemented"

/**
 * Tests the email sign-in screen's rendering, input, loading state, and callbacks with Compose.
 *
 * A real [com.github.se.amber.ui.authentication.signIn.EmailSignInViewModel] covers email entry,
 * the Continue button state, and existing-user navigation. Mocked ViewModels isolate Google button
 * clicks and error-clearing behavior.
 */
@RunWith(RobolectricTestRunner::class)
class EmailSignInScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** Text of [id] in the current test language. */
  private fun string(id: Int): String =
      ApplicationProvider.getApplicationContext<Context>().getString(id)

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
    composeTestRule.onNodeWithTag(AmberHeaderTestTags.HEADER).assertIsDisplayed()
  }

  @Test
  fun screenShowsTermsFooter() {
    setScreen()
    composeTestRule.onNodeWithTag(AuthenticationComponentTestTags.TERMS_FOOTER).assertIsDisplayed()
  }

  @Ignore(EMAIL_HIDDEN)
  @Test
  fun screenShowsHeading() {
    setScreen()
    composeTestRule.onNodeWithTag(EmailSignInTestTags.HEADING).assertIsDisplayed()
  }

  @Ignore(EMAIL_HIDDEN)
  @Test
  fun screenShowsEmailField() {
    setScreen()
    composeTestRule.onNodeWithTag(EmailSignInTestTags.EMAIL_FIELD).assertIsDisplayed()
  }

  @Ignore(EMAIL_HIDDEN)
  @Test
  fun continueButtonIsDisabledWhileEmailIsBlank() {
    setScreen()
    composeTestRule.onNodeWithTag(EmailSignInTestTags.CONTINUE_BUTTON).assertIsNotEnabled()
  }

  @Ignore(EMAIL_HIDDEN)
  @Test
  fun continueButtonIsEnabledOnceEmailIsTyped() {
    setScreen()
    composeTestRule
        .onNodeWithTag(EmailSignInTestTags.EMAIL_FIELD)
        .performTextInput("person@example.com")

    composeTestRule.onNodeWithTag(EmailSignInTestTags.CONTINUE_BUTTON).assertIsEnabled()
  }

  @Ignore(EMAIL_HIDDEN)
  @Test
  fun continueButtonIsDisabledForWhitespaceEmail() {
    setScreen(mockViewModel(EmailSignInUIState(email = "  ")))
    composeTestRule.onNodeWithTag(EmailSignInTestTags.CONTINUE_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun theScreenWelcomesTheUserAndGoogleIsTheOnlySignInOption() {
    setScreen()

    composeTestRule
        .onNodeWithTag(EmailSignInTestTags.WELCOME)
        .assertTextEquals(string(R.string.signInWelcome))
    composeTestRule
        .onNodeWithTag(EmailSignInTestTags.INSTRUCTION)
        .assertTextEquals(string(R.string.signInInstruction))
    composeTestRule.onNodeWithTag(EmailSignInTestTags.GOOGLE_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EmailSignInTestTags.HEADING).assertDoesNotExist()
    composeTestRule.onNodeWithTag(EmailSignInTestTags.EMAIL_FIELD).assertDoesNotExist()
    composeTestRule.onNodeWithTag(EmailSignInTestTags.CONTINUE_BUTTON).assertDoesNotExist()
    composeTestRule.onNodeWithTag(EmailSignInTestTags.OR_DIVIDER).assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(AuthenticationComponentTestTags.TERMS_FOOTER)
        .assertTextEquals(string(R.string.signInTermsFooter))
  }

  @Test
  @Config(qualifiers = "fr")
  fun theScreenIsInFrenchWhenThePhoneIsInFrench() {
    setScreen()

    composeTestRule
        .onNodeWithTag(EmailSignInTestTags.WELCOME)
        .assertTextEquals("Bienvenue sur Amber")
    composeTestRule
        .onNodeWithTag(EmailSignInTestTags.GOOGLE_BUTTON)
        .assertTextEquals("Se connecter avec Google")
  }

  @Test
  fun googleButtonIsEnabledByDefault() {
    setScreen()
    composeTestRule.onNodeWithTag(EmailSignInTestTags.GOOGLE_BUTTON).assertIsEnabled()
  }

  @Ignore(EMAIL_HIDDEN)
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
        .assertTextEquals(string(R.string.signInGoogleLoading))
  }

  @Ignore(EMAIL_HIDDEN)
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

  @Ignore(EMAIL_HIDDEN)
  @Test
  fun continueWithBlankEmailDoesNotInvokeNavigationCallback() {
    setScreen()

    composeTestRule.onNodeWithTag(EmailSignInTestTags.CONTINUE_BUTTON).performClick()

    assertEquals(0, continueCount)
  }

  @Ignore(EMAIL_HIDDEN)
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

  @Ignore(EMAIL_HIDDEN)
  @Test
  fun continueButtonStaysEnabledDuringLoading() {
    setScreen(mockViewModel(EmailSignInUIState(email = "person@example.com", isLoading = true)))
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
    val model = mockViewModel(EmailSignInUIState(errorRes = R.string.signInErrorFailed))
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
      composeTestRule.setContent { EmailSignInScreen(onGoogleSignIn = { googleSignInCount++ }) }
      composeTestRule.waitForIdle()

      verify { repository.currentUser }
      assertEquals(1, googleSignInCount)
    } finally {
      unmockkObject(AuthRepositoryProvider)
    }
  }

  @Ignore(EMAIL_HIDDEN)
  @Test
  fun contentWithDefaultArgumentsShowsEmptyIdleForm() {
    composeTestRule.setContent { EmailSignInContent() }

    composeTestRule.onNodeWithTag(EmailSignInTestTags.CONTINUE_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(EmailSignInTestTags.GOOGLE_BUTTON).assertIsEnabled()
    composeTestRule.onNodeWithTag(EmailSignInTestTags.CONTINUE_BUTTON).performClick()
    composeTestRule.onNodeWithTag(EmailSignInTestTags.GOOGLE_BUTTON).performClick()
  }

  @Ignore(EMAIL_HIDDEN)
  @Test
  fun previewShowsEnteredEmail() {
    composeTestRule.setContent { EmailSignInContentPreview() }

    composeTestRule
        .onNodeWithTag(EmailSignInTestTags.EMAIL_FIELD)
        .assertTextContains("person@example.com")
    composeTestRule.onNodeWithTag(EmailSignInTestTags.CONTINUE_BUTTON).assertIsEnabled()
  }
}
