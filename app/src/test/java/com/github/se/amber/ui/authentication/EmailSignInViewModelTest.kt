// This file was written with the aid of AI
package com.github.se.amber.ui.authentication

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.github.se.amber.R
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthUser
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests [EmailSignInViewModel] directly. These tests call its methods and assert state changes,
 * validation results, and Google sign-in error handling with mocked external dependencies.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmailSignInViewModelTest {
  private val repository = mockk<AuthRepository>()
  private val context = mockk<Context>()
  private val credentialManager = mockk<CredentialManager>()
  private lateinit var viewModel: EmailSignInViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
    every { repository.currentUser } returns null
    every { context.getString(R.string.default_web_client_id) } returns "client-id"
    viewModel = EmailSignInViewModel(repository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun initialStateIsIdleAndEmpty() {
    assertEquals(EmailSignInUiState(), viewModel.uiState.value)
  }

  @Test
  fun initialStateIncludesExistingUser() {
    val user = AuthUser("existing-user")
    every { repository.currentUser } returns user

    assertEquals(user, EmailSignInViewModel(repository).uiState.value.user)
  }

  @Test
  fun emailChangeStoresExactInput() {
    viewModel.onEmailChange(" Person@example.com ")

    assertEquals(" Person@example.com ", viewModel.uiState.value.email)
  }

  @Test
  fun blankEmailIsRejectedWithError() {
    viewModel.onEmailChange("  \t")

    assertFalse(viewModel.validateEmail())
    assertEquals("Please enter your email address.", viewModel.uiState.value.errorMessage)
  }

  @Test
  fun nonblankEmailIsAccepted() {
    viewModel.onEmailChange("person@example.com")

    assertTrue(viewModel.validateEmail())
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun clearingErrorPreservesEmail() {
    viewModel.onEmailChange("  ")
    viewModel.validateEmail()

    viewModel.clearError()

    assertNull(viewModel.uiState.value.errorMessage)
    assertEquals("  ", viewModel.uiState.value.email)
  }

  @Test
  fun credentialFailureShowsErrorAndStopsLoading() = runTest {
    coEvery { credentialManager.getCredential(context, any<GetCredentialRequest>()) } throws
        IllegalStateException("Credential provider unavailable")

    viewModel.signInWithGoogle(context, credentialManager)
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals("Credential provider unavailable", viewModel.uiState.value.errorMessage)
    assertNull(viewModel.uiState.value.user)
  }

  @Test
  fun googleAttemptClearsPreviousErrorWhileLoading() = runTest {
    val pending = CompletableDeferred<Unit>()
    coEvery { credentialManager.getCredential(context, any<GetCredentialRequest>()) } coAnswers
        {
          pending.await()
          throw IllegalStateException("failed")
        }
    viewModel.validateEmail()

    viewModel.signInWithGoogle(context, credentialManager)
    runCurrent()

    assertTrue(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.errorMessage)
    pending.complete(Unit)
    advanceUntilIdle()
  }

  @Test
  fun secondGoogleAttemptWhileLoadingKeepsFirstAttempt() = runTest {
    val pending = CompletableDeferred<Unit>()
    coEvery { credentialManager.getCredential(context, any<GetCredentialRequest>()) } coAnswers
        {
          pending.await()
          throw IllegalStateException("failed")
        }

    viewModel.signInWithGoogle(context, credentialManager)
    runCurrent()
    viewModel.signInWithGoogle(context, credentialManager)

    assertTrue(viewModel.uiState.value.isLoading)
    pending.complete(Unit)
    advanceUntilIdle()
    assertEquals("failed", viewModel.uiState.value.errorMessage)
  }
}
