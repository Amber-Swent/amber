// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Alaabrine, with assistance from Cursor.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Alaabrine, with assistance from Cursor.

package com.github.se.amber.ui.roleloading

import android.util.Log
import com.github.se.amber.R
import com.github.se.amber.data.account.AccountDeletionError
import com.github.se.amber.data.account.AccountDeletionRepository
import com.github.se.amber.data.account.AccountDeletionRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.session.SessionRepository
import com.github.se.amber.data.session.SessionRepositoryProvider
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthUser
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.authentication.roleLoading.CareCircleDestination
import com.github.se.amber.ui.authentication.roleLoading.RoleLoadingViewModel
import com.github.se.amber.ui.authentication.roleLoading.RoleState
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

  private val signOutErrorMsg = R.string.signOutError

  private lateinit var profileRepository: ProfileRepository
  private lateinit var authRepository: AuthRepository
  private lateinit var deletionRepository: AccountDeletionRepository
  private lateinit var viewModel: RoleLoadingViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())

    // android.util.Log is not available in plain JVM unit tests
    mockkStatic(Log::class)
    every { Log.e(any(), any(), any()) } returns 0

    profileRepository = mockk()
    authRepository = mockk()
    every { authRepository.currentUser } returns AuthUser("uid123")
    coEvery { authRepository.refreshCurrentUser() } returns AuthUser("uid123")
    deletionRepository = mockk()
    coEvery { deletionRepository.clearGoneAccount() } returns Result.success(Unit)

    viewModel =
        RoleLoadingViewModel(
            profileRepository,
            authRepository,
            AccountDeletionRepositoryProvider { deletionRepository },
            SessionRepositoryProvider { SessionRepository { authRepository.signOut() } },
        )
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
    assertNull(state.errorMsgRes)
  }

  // ---------- loadRole ----------

  @Test
  fun loadRole_patientProfile_emitsLoadedPatient() = runTest {
    stubProfile(UserProfile(uid = "uid123", role = Role.PATIENT, circleIds = listOf("circle-1")))

    viewModel.loadRole()
    advanceUntilIdle()

    assertEquals(RoleState.Loaded(Role.PATIENT), viewModel.state.value.roleState)
  }

  @Test
  fun loadRole_caregiverProfile_emitsLoadedCaregiver() = runTest {
    stubProfile(UserProfile(uid = "uid123", role = Role.CAREGIVER, circleIds = listOf("circle-1")))

    viewModel.loadRole()
    advanceUntilIdle()

    assertEquals(RoleState.Loaded(Role.CAREGIVER), viewModel.state.value.roleState)
  }

  @Test
  fun loadRole_accountGone_clearsTheDeviceAndSignsOutInsteadOfOnboarding() = runTest {
    stubProfile(null)
    coEvery { authRepository.refreshCurrentUser() } returns null

    viewModel.loadRole()
    advanceUntilIdle()

    coVerify(exactly = 1) { deletionRepository.clearGoneAccount() }
    assertTrue(viewModel.state.value.signedOut)
    assertEquals(RoleState.Loading, viewModel.state.value.roleState)
  }

  @Test
  fun loadRole_accountGoneAndDeviceNotCleared_stillSignsOut() = runTest {
    stubProfile(null)
    coEvery { authRepository.refreshCurrentUser() } returns null
    coEvery { deletionRepository.clearGoneAccount() } returns
        Result.failure(AccountDeletionError.LocalDataNotCleared(Exception("Files left")))

    viewModel.loadRole()
    advanceUntilIdle()

    assertTrue(viewModel.state.value.signedOut)
  }

  @Test
  fun loadRole_nobodySignedIn_signsOutWithoutClearingTheDevice() = runTest {
    stubProfile(null)
    every { authRepository.currentUser } returns null
    coEvery { authRepository.refreshCurrentUser() } returns null

    viewModel.loadRole()
    advanceUntilIdle()

    coVerify(exactly = 0) { deletionRepository.clearGoneAccount() }
    assertTrue(viewModel.state.value.signedOut)
  }

  @Test
  fun loadRole_noProfile_emitsNoProfile() = runTest {
    stubProfile(null)

    viewModel.loadRole()
    advanceUntilIdle()

    assertEquals(RoleState.NoProfile, viewModel.state.value.roleState)
  }

  @Test
  fun loadRole_profileWithNoCircle_sendsEachRoleToItsCareCircleStep() = runTest {
    stubProfile(UserProfile(uid = "uid123", role = Role.CAREGIVER))
    viewModel.loadRole()
    advanceUntilIdle()
    assertEquals(
        RoleState.NeedsCareCircle(CareCircleDestination.Choice),
        viewModel.state.value.roleState,
    )

    stubProfile(UserProfile(uid = "uid123", role = Role.PATIENT))
    viewModel.loadRole()
    advanceUntilIdle()
    assertEquals(
        RoleState.NeedsCareCircle(CareCircleDestination.Invitation),
        viewModel.state.value.roleState,
    )
  }

  @Test
  fun loadRole_blankCircleIds_countsAsNoCircle() = runTest {
    // Empty lists never call isNotBlank(). A non-empty list of blank ids must.
    stubProfile(
        UserProfile(uid = "uid123", role = Role.CAREGIVER, circleIds = listOf("", " ")),
    )

    viewModel.loadRole()
    advanceUntilIdle()

    assertEquals(
        RoleState.NeedsCareCircle(CareCircleDestination.Choice),
        viewModel.state.value.roleState,
    )
  }

  @Test
  fun loadRole_blankIdBeforeARealOne_emitsLoaded() = runTest {
    stubProfile(
        UserProfile(uid = "uid123", role = Role.CAREGIVER, circleIds = listOf("", "circle-1")),
    )

    viewModel.loadRole()
    advanceUntilIdle()

    assertEquals(RoleState.Loaded(Role.CAREGIVER), viewModel.state.value.roleState)
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
          Result.success(
              UserProfile(uid = "uid123", role = Role.PATIENT, circleIds = listOf("circle-1"))
          )
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
          Result.success(
              UserProfile(uid = "uid123", role = Role.CAREGIVER, circleIds = listOf("circle-1"))
          )
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
    assertNull(state.errorMsgRes)
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
    assertNull(state.errorMsgRes)
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
    assertEquals(signOutErrorMsg, state.errorMsgRes)
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
    assertEquals(signOutErrorMsg, viewModel.state.value.errorMsgRes)

    viewModel.clearErrorMsg()

    assertNull(viewModel.state.value.errorMsgRes)
  }
}
