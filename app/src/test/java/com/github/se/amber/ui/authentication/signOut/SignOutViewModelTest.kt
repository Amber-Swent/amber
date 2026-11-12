// Written by Alaabrine, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Alaabrine, with assistance from Cursor (Grok).

package com.github.se.amber.ui.authentication.signOut

import android.util.Log
import com.github.se.amber.R
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.circle.ActiveCircleRepositoryProvider
import com.github.se.amber.data.session.SessionRepository
import com.github.se.amber.data.session.SessionRepositoryProvider
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.CancellationException
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
  private lateinit var activeCircles: ActiveCircleRepository
  private val sessionCalls = mutableListOf<String>()
  private var clearFailure: Exception? = null

  @Before
  fun setup() {
    authRepo = mockk(relaxed = true)
    activeCircles = mockk(relaxed = true)
    sessionCalls.clear()
    clearFailure = null
    mockkStatic(Log::class)
    every { Log.e(any(), any(), any()) } returns 0
  }

  @After fun tearDown() = unmockkAll()

  @Test
  fun signOut_success_setsSignedOutAndStopsSigningOut() = runTest {
    coEvery { authRepo.signOut() } returns Result.success(Unit)

    val viewModel = signOutViewModel()
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

    val viewModel = signOutViewModel()
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

    val viewModel = signOutViewModel()
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

    val viewModel = signOutViewModel()
    viewModel.signOut()

    assertTrue(viewModel.uiState.value.signedOut)
    assertNull(viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun signOut_clearFails_keepsTheUserSignedInAndCanBeRetried() = runTest {
    every { authRepo.currentUser } returns AuthUser("uid")
    clearFailure = IOException("Media left")
    val viewModel = signOutViewModel()

    viewModel.signOut()

    assertFalse(viewModel.uiState.value.signedOut)
    assertFalse(viewModel.uiState.value.isSigningOut)
    assertEquals(R.string.signOutError, viewModel.uiState.value.errorMsgRes)
    assertEquals(listOf("signOut"), sessionCalls)
    coVerify(exactly = 0) { authRepo.signOut() }
    coVerify(exactly = 0) { activeCircles.clear(any()) }

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

    val viewModel = signOutViewModel()
    viewModel.signOut()

    assertFalse(viewModel.uiState.value.signedOut)
    assertEquals(R.string.signOutError, viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun signOut_retryAfterFailure_isAllowed() = runTest {
    every { authRepo.currentUser } returns AuthUser("uid")
    coEvery { authRepo.signOut() } returns Result.failure(RuntimeException("fail"))

    val viewModel = signOutViewModel()
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

    val viewModel = signOutViewModel()
    viewModel.signOut()
    assertEquals(R.string.signOutError, viewModel.uiState.value.errorMsgRes)

    viewModel.clearErrorMsg()

    assertNull(viewModel.uiState.value.errorMsgRes)
  }

  @Test
  fun setSignedOut_true_updatesUiState() {
    val viewModel = signOutViewModel()

    viewModel.setSignedOut(true)

    assertTrue(viewModel.uiState.value.signedOut)
  }

  @Test
  fun setSignedOut_false_clearsSignedOutFlag() {
    val viewModel = signOutViewModel()
    viewModel.setSignedOut(true)

    viewModel.setSignedOut(false)

    assertFalse(viewModel.uiState.value.signedOut)
  }

  @Test
  fun signOut_success_clearsTheActiveCircle() = runTest {
    every { authRepo.currentUser } returns AuthUser("caregiver")
    coEvery { authRepo.signOut() } returns Result.success(Unit)

    signOutViewModel().signOut()

    coVerify(exactly = 1) { activeCircles.clear("caregiver") }
  }

  @Test
  fun signOut_failureWhileStillSignedIn_keepsTheActiveCircle() = runTest {
    every { authRepo.currentUser } returns AuthUser("caregiver")
    coEvery { authRepo.signOut() } returns Result.failure(RuntimeException("fail"))

    signOutViewModel().signOut()

    coVerify(exactly = 0) { activeCircles.clear(any()) }
  }

  @Test
  fun signOut_failureAfterTheSessionEnded_clearsTheActiveCircle() = runTest {
    var user: AuthUser? = AuthUser("caregiver")
    every { authRepo.currentUser } answers { user }
    coEvery { authRepo.signOut() } coAnswers
        {
          user = null
          Result.failure(RuntimeException("fail"))
        }

    val viewModel = signOutViewModel()
    viewModel.signOut()

    assertTrue(viewModel.uiState.value.signedOut)
    coVerify(exactly = 1) { activeCircles.clear("caregiver") }
  }

  @Test
  fun signOut_success_stillSignsOutWhenClearingTheActiveCircleFails() = runTest {
    every { authRepo.currentUser } returns AuthUser("caregiver")
    coEvery { authRepo.signOut() } returns Result.success(Unit)
    coEvery { activeCircles.clear("caregiver") } throws IOException("disk")

    val viewModel = signOutViewModel()
    viewModel.signOut()

    assertTrue(viewModel.uiState.value.signedOut)
    assertFalse(viewModel.uiState.value.isSigningOut)
  }

  @Test
  fun signOut_success_rethrowsWhenClearingTheActiveCircleIsCancelled() = runTest {
    every { authRepo.currentUser } returns AuthUser("caregiver")
    coEvery { authRepo.signOut() } returns Result.success(Unit)
    coEvery { activeCircles.clear("caregiver") } throws CancellationException("left")

    val viewModel = signOutViewModel()
    viewModel.signOut()

    assertFalse(viewModel.uiState.value.signedOut)
    assertTrue(viewModel.uiState.value.isSigningOut)
    coVerify(exactly = 1) { activeCircles.clear("caregiver") }
    verify(exactly = 0) { Log.e(any(), "Failed to clear the active circle", any()) }
  }

  @Test
  fun signOut_success_clearsTheRepositoryFromTheProviderDefault() = runTest {
    val provider = ActiveCircleRepositoryProvider { activeCircles }
    mockkObject(ActiveCircleRepositoryProvider.Companion)
    mockkObject(SessionRepositoryProvider.Companion)
    every { ActiveCircleRepositoryProvider.default } returns provider
    // The default session repository opens Firebase. This test only checks the circle provider.
    every { SessionRepositoryProvider.default } returns
        SessionRepositoryProvider { SessionRepository { authRepo.signOut() } }
    every { authRepo.currentUser } returns AuthUser("caregiver")
    coEvery { authRepo.signOut() } returns Result.success(Unit)
    try {
      val viewModel = SignOutViewModel(authRepo)
      viewModel.signOut()

      assertTrue(viewModel.uiState.value.signedOut)
      coVerify(exactly = 1) { activeCircles.clear("caregiver") }
    } finally {
      unmockkObject(ActiveCircleRepositoryProvider.Companion)
      unmockkObject(SessionRepositoryProvider.Companion)
    }
  }

  private fun signOutViewModel() =
      SignOutViewModel(
          authRepository = authRepo,
          sessionRepositoryProvider =
              SessionRepositoryProvider {
                SessionRepository {
                  sessionCalls += "signOut"
                  val failure = clearFailure
                  if (failure != null) Result.failure(failure) else authRepo.signOut()
                }
              },
          activeCircleRepository = activeCircles,
      )
}
