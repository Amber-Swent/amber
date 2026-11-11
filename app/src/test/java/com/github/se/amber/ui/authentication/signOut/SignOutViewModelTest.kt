// Written by Alaabrine, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Alaabrine, with assistance from Cursor.

package com.github.se.amber.ui.authentication.signOut

import android.util.Log
import com.github.se.amber.R
import com.github.se.amber.data.session.SessionRepository
import com.github.se.amber.data.session.SessionRepositoryProvider
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) :
    TestWatcher() {
  override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

  override fun finished(description: Description) = Dispatchers.resetMain()
}

class SignOutViewModelTest {
  @get:Rule val mainRule = MainDispatcherRule()

  private lateinit var authRepo: AuthRepository
  private val sessionCalls = mutableListOf<String>()
  private var clearFailure: Exception? = null

  @Before
  fun setup() {
    authRepo = mockk(relaxed = true)
    sessionCalls.clear()
    clearFailure = null
    mockkStatic(Log::class)
    every { Log.e(any(), any(), any()) } returns 0
  }

  @After fun tearDown() = unmockkAll()

  @Test
  fun signOut_success_setsSignedOutAndStopsSigningOut() = runTest {
    coEvery { authRepo.signOut() } returns Result.success(Unit)

    val viewModel = signOutViewModel(authRepo)
    viewModel.signOut()

    assertTrue(viewModel.uiState.value.signedOut)
    assertFalse(viewModel.uiState.value.isSigningOut)
    assertEquals(listOf("signOut"), sessionCalls)
    coVerify(exactly = 1) { authRepo.signOut() }
  }

  @Test
  fun signOut_whileInProgress_setsIsSigningOutTrue() = runTest {
    val pending = CompletableDeferred<Result<Unit>>()
    coEvery { authRepo.signOut() } coAnswers { pending.await() }

    val viewModel = signOutViewModel(authRepo)
    viewModel.signOut()

    assertTrue(viewModel.uiState.value.isSigningOut)
    assertFalse(viewModel.uiState.value.signedOut)

    pending.complete(Result.success(Unit))
    advanceUntilIdle()
  }

  @Test
  fun signOut_calledTwiceWhileInProgress_signsOutOnlyOnce() = runTest {
    val pending = CompletableDeferred<Result<Unit>>()
    coEvery { authRepo.signOut() } coAnswers { pending.await() }

    val viewModel = signOutViewModel(authRepo)
    viewModel.signOut()
    viewModel.signOut()

    pending.complete(Result.success(Unit))
    advanceUntilIdle()

    coVerify(exactly = 1) { authRepo.signOut() }
  }

  @Test
  fun signOut_failureButUserAlreadySignedOut_treatedAsSignedOut() = runTest {
    every { authRepo.currentUser } returns null
    coEvery { authRepo.signOut() } returns Result.failure(RuntimeException("fail"))

    val viewModel = signOutViewModel(authRepo)
    viewModel.signOut()

    assertTrue(viewModel.uiState.value.signedOut)
    assertNull(viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun signOut_clearFails_keepsTheUserSignedInAndCanBeRetried() = runTest {
    every { authRepo.currentUser } returns AuthUser("uid")
    clearFailure = IOException("Media left")
    val viewModel = signOutViewModel(authRepo)

    viewModel.signOut()

    assertFalse(viewModel.uiState.value.signedOut)
    assertFalse(viewModel.uiState.value.isSigningOut)
    assertEquals(R.string.signOutError, viewModel.uiState.value.errorMsgRes)
    assertEquals(listOf("signOut"), sessionCalls)
    coVerify(exactly = 0) { authRepo.signOut() }

    clearFailure = null
    sessionCalls.clear()
    coEvery { authRepo.signOut() } returns Result.success(Unit)
    viewModel.signOut()

    assertTrue(viewModel.uiState.value.signedOut)
    assertFalse(viewModel.uiState.value.isSigningOut)
    coVerify(exactly = 1) { authRepo.signOut() }
  }

  @Test
  fun signOut_failureAndUserStillSignedIn_showsFixedErrorMessage() = runTest {
    every { authRepo.currentUser } returns AuthUser("uid")
    coEvery { authRepo.signOut() } returns Result.failure(RuntimeException("fail"))

    val viewModel = signOutViewModel(authRepo)
    viewModel.signOut()

    assertFalse(viewModel.uiState.value.signedOut)
    assertEquals(R.string.signOutError, viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun signOut_retryAfterFailure_isAllowed() = runTest {
    every { authRepo.currentUser } returns AuthUser("uid")
    coEvery { authRepo.signOut() } returns Result.failure(RuntimeException("fail"))

    val viewModel = signOutViewModel(authRepo)
    viewModel.signOut()
    assertFalse(viewModel.uiState.value.signedOut)

    coEvery { authRepo.signOut() } returns Result.success(Unit)
    viewModel.signOut()

    assertTrue(viewModel.uiState.value.signedOut)
    coVerify(exactly = 2) { authRepo.signOut() }
  }

  @Test
  fun clearErrorMsg_resetsTheError() = runTest {
    every { authRepo.currentUser } returns AuthUser("uid")
    coEvery { authRepo.signOut() } returns Result.failure(RuntimeException("fail"))

    val viewModel = signOutViewModel(authRepo)
    viewModel.signOut()
    assertEquals(R.string.signOutError, viewModel.uiState.value.errorMsgRes)

    viewModel.clearErrorMsg()

    assertNull(viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun setSignedOut_true_updatesUiState() {
    val viewModel = signOutViewModel(authRepo)

    viewModel.setSignedOut(true)

    assertTrue(viewModel.uiState.value.signedOut)
  }

  @Test
  fun setSignedOut_false_clearsSignedOutFlag() {
    val viewModel = signOutViewModel(authRepo)
    viewModel.setSignedOut(true)

    viewModel.setSignedOut(false)

    assertFalse(viewModel.uiState.value.signedOut)
  }

  private fun signOutViewModel(authRepository: AuthRepository) =
      SignOutViewModel(
          authRepository = authRepository,
          sessionRepositoryProvider =
              SessionRepositoryProvider {
                SessionRepository {
                  sessionCalls += "signOut"
                  val failure = clearFailure
                  if (failure != null) Result.failure(failure) else authRepository.signOut()
                }
              },
      )
}
