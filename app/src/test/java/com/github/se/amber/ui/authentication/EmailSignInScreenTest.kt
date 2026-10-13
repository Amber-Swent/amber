// This file was written with the aid of AI
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
import com.github.se.amber.model.authentication.AuthUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the Compose screen and shared authentication UI. These tests find elements by test tag and
 * verify rendering and user interactions. A real [EmailSignInViewModel] is used where needed to
 * check that UI input and clicks reach the ViewModel and invoke the screen callbacks.
 */
@RunWith(RobolectricTestRunner::class)
class EmailSignInScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val repository = mockk<AuthRepository>()
  private val credentialManager = mockk<CredentialManager>()
  private lateinit var viewModel: EmailSignInViewModel
  private var continueCount = 0
  private var googleSignInCount = 0

  @Before
  fun setUp() {
    every { repository.currentUser } returns null
    viewModel = EmailSignInViewModel(repository)
    continueCount = 0
    googleSignInCount = 0
  }

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

  @Test
  fun screenShowsSharedAuthenticationContent() {
    setScreen()

    composeTestRule.onNodeWithTag(AuthenticationComponentTestTags.HEADER).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EmailSignInTestTags.HEADING).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AuthenticationComponentTestTags.TERMS_FOOTER).assertIsDisplayed()
  }

  @Test
  fun screenShowsEmailAndBothActions() {
    setScreen()

    composeTestRule.onNodeWithTag(EmailSignInTestTags.EMAIL_FIELD).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EmailSignInTestTags.CONTINUE_BUTTON).assertIsEnabled()
    composeTestRule.onNodeWithTag(EmailSignInTestTags.GOOGLE_BUTTON).assertIsEnabled()
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
    val state = MutableStateFlow(EmailSignInUiState(isLoading = true))
    val loadingModel = mockk<EmailSignInViewModel>(relaxed = true)
    every { loadingModel.uiState } returns state
    setScreen(loadingModel)

    composeTestRule.onNodeWithTag(EmailSignInTestTags.GOOGLE_BUTTON).assertIsNotEnabled()
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
  fun screenShowsSignUpPrompt() {
    setScreen()

    composeTestRule.onNodeWithTag(EmailSignInTestTags.SIGN_UP_PROMPT).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EmailSignInTestTags.SIGN_UP_LINK).assertIsDisplayed()
  }
}
