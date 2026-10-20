// This file was written with the aid of AI
package com.github.se.amber.ui.authentication

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.credentials.Credential
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthUser
import com.github.se.amber.ui.authentication.signIn.EmailSignInScreen
import com.github.se.amber.ui.authentication.signIn.EmailSignInTestTags
import com.github.se.amber.ui.authentication.signIn.EmailSignInViewModel
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies that email input remains visible after a real Android activity recreation. */
@RunWith(AndroidJUnit4::class)
class EmailSignInInstrumentedTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val repository =
      object : AuthRepository {
        override val currentUser: AuthUser? = null

        override suspend fun signInWithGoogle(credential: Credential): Result<AuthUser> =
            error("Google sign-in is outside this test")

        override suspend fun signOut(): Result<Unit> = error("Sign-out is outside this test")
      }

  private val factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EmailSignInViewModel(repository) as T
      }

  private fun showScreen(activity: ComponentActivity): EmailSignInViewModel {
    val viewModel = ViewModelProvider(activity, factory)[EmailSignInViewModel::class.java]
    activity.setContent { EmailSignInScreen(emailSignInViewModel = viewModel) }
    return viewModel
  }

  @Test
  fun emailRemainsVisibleAfterActivityRecreation() {
    lateinit var originalViewModel: EmailSignInViewModel
    composeTestRule.activityRule.scenario.onActivity { originalViewModel = showScreen(it) }
    composeTestRule
        .onNodeWithTag(EmailSignInTestTags.EMAIL_FIELD)
        .performTextInput("person@example.com")

    composeTestRule.activityRule.scenario.recreate()

    lateinit var recreatedViewModel: EmailSignInViewModel
    composeTestRule.activityRule.scenario.onActivity { recreatedViewModel = showScreen(it) }
    composeTestRule
        .onNodeWithTag(EmailSignInTestTags.EMAIL_FIELD)
        .assertTextContains("person@example.com")
    assertSame(originalViewModel, recreatedViewModel)
  }
}
