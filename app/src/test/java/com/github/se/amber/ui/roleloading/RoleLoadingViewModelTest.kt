// this code was written with the aid of AI
package com.github.se.amber.ui.roleloading

import android.util.Log
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthUser
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
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

@OptIn(ExperimentalCoroutinesApi::class)
class RoleLoadingViewModelTest {

  private val signOutErrorMsg = "Could not sign out. Please try again."

  private lateinit var profileRepository: ProfileRepository
  private lateinit var authRepository: AuthRepository
  private lateinit var viewModel: RoleLoadingViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())

    // android.util.Log is not available in plain JVM unit tests
    mockkStatic(Log::class)
    every { Log.e(any(), any(), any()) } returns 0

    profileRepository = mockk()
    authRepository = mockk()

    viewModel = RoleLoadingViewModel(profileRepository, authRepository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkAll()
  }

  private fun stubProfile(profile: UserProfile?) {
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(profile)
  }

  // ---------- Initial state ----------

  @Test
  fun initialState_isLoadingAndNotSigningOut() {
    val state = viewModel.state.value

    assertEquals(RoleState.Loading, state.roleState)
    assertFalse(state.isSigningOut)
    assertFalse(state.signedOut)
    assertNull(state.errorMsg)
  }

  // ---------- loadRole ----------

  @Test
  fun loadRole_patientProfile_emitsLoadedPatient() = runTest {
    stubProfile(UserProfile(uid = "uid123", role = Role.PATIENT))

    viewModel.loadRole()
    advanceUntilIdle()

    assertEquals(RoleState.Loaded(Role.PATIENT), viewModel.state.value.roleState)
  }

  @Test
  fun loadRole_caregiverProfile_emitsLoadedCaregiver() = runTest {
    stubProfile(UserProfile(uid = "uid123", role = Role.CAREGIVER))

    viewModel.loadRole()
    advanceUntilIdle()

    assertEquals(RoleState.Loaded(Role.CAREGIVER), viewModel.state.value.roleState)
  }

  @Test
  fun loadRole_noProfile_emitsNoProfile() = runTest {
    stubProfile(null)

    viewModel.loadRole()
    advanceUntilIdle()

    assertEquals(RoleState.NoProfile, viewModel.state.value.roleState)
  }

  @Test
  fun loadRole_repositoryFails_emitsError() = runTest {
    coEvery { profileRepository.getCurrentProfile() } returns
        Result.failure(RuntimeException("Firestore down"))

    viewModel.loadRole()
    advanceUntilIdle()

    assertEquals(RoleState.Error, viewModel.state.value.roleState)
  }

  @Test
  fun loadRole_calledTwiceWhileLoading_secondCallIsIgnored() = runTest {
    // The repository call stays suspended, so the first call is guaranteed to be in flight.
    val gate = CompletableDeferred<Unit>()
    coEvery { profileRepository.getCurrentProfile() } coAnswers
        {
          gate.await()
          Result.success(UserProfile(uid = "uid123", role = Role.PATIENT))
        }

    viewModel.loadRole()
    runCurrent() // the first call starts and suspends on the gate
    viewModel.loadRole() // happens while the first call is still in flight
    runCurrent()

    coVerify(exactly = 1) { profileRepository.getCurrentProfile() }
    assertEquals(RoleState.Loading, viewModel.state.value.roleState)

    // Let the first call finish
    gate.complete(Unit)
    advanceUntilIdle()

    assertEquals(RoleState.Loaded(Role.PATIENT), viewModel.state.value.roleState)
  }

  @Test
  fun loadRole_retryAfterError_emitsLoadingThenCanSucceed() = runTest {
    coEvery { profileRepository.getCurrentProfile() } returns
        Result.failure(RuntimeException("Firestore down"))
    viewModel.loadRole()
    advanceUntilIdle()
    assertEquals(RoleState.Error, viewModel.state.value.roleState)

    val gate = CompletableDeferred<Unit>()
    coEvery { profileRepository.getCurrentProfile() } coAnswers
        {
          gate.await()
          Result.success(UserProfile(uid = "uid123", role = Role.CAREGIVER))
        }
    viewModel.loadRole()
    runCurrent()
    assertEquals(RoleState.Loading, viewModel.state.value.roleState)

    gate.complete(Unit)
    advanceUntilIdle()
    assertEquals(RoleState.Loaded(Role.CAREGIVER), viewModel.state.value.roleState)
  }

  // ---------- signOut ----------

  @Test
  fun signOut_success_setsSignedOutAndStopsSigningOut() = runTest {
    coEvery { authRepository.signOut() } returns Result.success(Unit)

    viewModel.signOut()
    advanceUntilIdle()

    val state = viewModel.state.value
    assertTrue(state.signedOut)
    assertFalse(state.isSigningOut)
    assertNull(state.errorMsg)
    coVerify(exactly = 1) { authRepository.signOut() }
  }

  @Test
  fun signOut_whileInProgress_setsIsSigningOutTrue() = runTest {
    val gate = CompletableDeferred<Unit>()
    coEvery { authRepository.signOut() } coAnswers
        {
          gate.await()
          Result.success(Unit)
        }

    viewModel.signOut()
    runCurrent()

    assertTrue(viewModel.state.value.isSigningOut)
    assertFalse(viewModel.state.value.signedOut)

    gate.complete(Unit)
    advanceUntilIdle()
    assertFalse(viewModel.state.value.isSigningOut)
  }

  @Test
  fun signOut_calledTwiceWhileInProgress_signsOutOnlyOnce() = runTest {
    val gate = CompletableDeferred<Unit>()
    coEvery { authRepository.signOut() } coAnswers
        {
          gate.await()
          Result.success(Unit)
        }

    viewModel.signOut()
    runCurrent()
    viewModel.signOut() // double tap while the first sign-out is in flight
    runCurrent()

    coVerify(exactly = 1) { authRepository.signOut() }

    gate.complete(Unit)
    advanceUntilIdle()
    assertTrue(viewModel.state.value.signedOut)
  }

  @Test
  fun signOut_failureButUserAlreadySignedOut_treatedAsSignedOut() = runTest {
    coEvery { authRepository.signOut() } returns
        Result.failure(RuntimeException("Credential error"))
    every { authRepository.currentUser } returns null

    viewModel.signOut()
    advanceUntilIdle()

    val state = viewModel.state.value
    assertTrue(state.signedOut)
    assertFalse(state.isSigningOut)
    assertNull(state.errorMsg)
  }

  @Test
  fun signOut_failureAndUserStillSignedIn_showsFixedErrorMessage() = runTest {
    coEvery { authRepository.signOut() } returns
        Result.failure(RuntimeException("raw firebase text"))
    every { authRepository.currentUser } returns AuthUser("uid123")
    viewModel.signOut()
    advanceUntilIdle()

    val state = viewModel.state.value
    assertFalse(state.signedOut)
    assertFalse(state.isSigningOut)
    // the raw exception text must not be shown to the user
    assertEquals(signOutErrorMsg, state.errorMsg)
  }

  @Test
  fun signOut_retryAfterFailure_isAllowed() = runTest {
    coEvery { authRepository.signOut() } returns Result.failure(RuntimeException("fail"))
    every { authRepository.currentUser } returns AuthUser("uid123")
    viewModel.signOut()
    advanceUntilIdle()
    assertFalse(viewModel.state.value.isSigningOut)

    coEvery { authRepository.signOut() } returns Result.success(Unit)
    viewModel.signOut()
    advanceUntilIdle()

    assertTrue(viewModel.state.value.signedOut)
    coVerify(exactly = 2) { authRepository.signOut() }
  }

  // ---------- Error message ----------

  @Test
  fun clearErrorMsg_resetsErrorMessage() = runTest {
    coEvery { authRepository.signOut() } returns Result.failure(RuntimeException("fail"))
    every { authRepository.currentUser } returns AuthUser("uid123")
    viewModel.signOut()
    advanceUntilIdle()
    assertEquals(signOutErrorMsg, viewModel.state.value.errorMsg)

    viewModel.clearErrorMsg()

    assertNull(viewModel.state.value.errorMsg)
  }
}
