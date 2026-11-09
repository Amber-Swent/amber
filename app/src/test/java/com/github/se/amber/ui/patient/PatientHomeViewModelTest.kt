// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.patient

import android.util.Log
import com.github.se.amber.R
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

class PatientHomeViewModelTest {

  @get:Rule val mainRule = MainDispatcherRule()

  private lateinit var profileRepo: ProfileRepository

  @Before
  fun setup() {
    profileRepo = mockk()
    mockkStatic(Log::class)
    every { Log.e(any(), any(), any()) } returns 0
  }

  @After fun tearDown() = unmockkAll()

  private val profile =
      UserProfile(
          uid = "123",
          role = Role.PATIENT,
          person = Person(firstName = "Alice"),
          circleIds = emptyList(),
      )

  @Test
  fun `success updates the profile`() = runTest {
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(profile)

    val vm = PatientHomeViewModel(profileRepo) // init lance getProfile()

    assertEquals(profile, vm.uiState.value.patientProfile)
    assertNull(vm.uiState.value.errorMsgRes)
  }

  @Test
  fun `null profile sets error`() = runTest {
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(null)

    val vm = PatientHomeViewModel(profileRepo)

    assertEquals(R.string.patientHomeErrorNoProfile, vm.uiState.value.errorMsgRes)
  }

  @Test
  fun `failure sets the load profile error`() = runTest {
    coEvery { profileRepo.getCurrentProfile() } returns Result.failure(Exception("boom"))

    val vm = PatientHomeViewModel(profileRepo)

    assertEquals(R.string.patientHomeErrorLoadProfile, vm.uiState.value.errorMsgRes)
  }

  @Test
  fun `clearErrorMsg resets the error`() = runTest {
    coEvery { profileRepo.getCurrentProfile() } returns Result.failure(Exception("boom"))

    val vm = PatientHomeViewModel(profileRepo)
    assertEquals(R.string.patientHomeErrorLoadProfile, vm.uiState.value.errorMsgRes)

    vm.clearErrorMsg()

    assertNull(vm.uiState.value.errorMsgRes)
  }

  @Test
  fun `initial state is the default state while the profile is loading`() = runTest {
    val deferred = CompletableDeferred<Result<UserProfile?>>()
    coEvery { profileRepo.getCurrentProfile() } coAnswers { deferred.await() }

    val vm = PatientHomeViewModel(profileRepo)

    assertEquals(PatientHomeUIState(), vm.uiState.value)

    deferred.complete(Result.success(profile))

    assertEquals(profile, vm.uiState.value.patientProfile)
    assertNull(vm.uiState.value.errorMsgRes)
  }

  @Test
  fun `init fetches the profile exactly once`() = runTest {
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(profile)

    PatientHomeViewModel(profileRepo)

    coVerify(exactly = 1) { profileRepo.getCurrentProfile() }
  }

  @Test
  fun `null profile keeps the default profile`() = runTest {
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(null)

    val vm = PatientHomeViewModel(profileRepo)

    assertEquals(PatientHomeUIState().patientProfile, vm.uiState.value.patientProfile)
  }

  @Test
  fun `failure keeps the default profile`() = runTest {
    coEvery { profileRepo.getCurrentProfile() } returns Result.failure(Exception("boom"))

    val vm = PatientHomeViewModel(profileRepo)

    assertEquals(PatientHomeUIState().patientProfile, vm.uiState.value.patientProfile)
  }

  @Test
  fun `failure logs the error`() = runTest {
    val error = Exception("boom")
    coEvery { profileRepo.getCurrentProfile() } returns Result.failure(error)

    PatientHomeViewModel(profileRepo)

    verify { Log.e("PatientHomeViewModel", "Error fetching profile", error) }
  }

  @Test
  fun `success does not log an error`() = runTest {
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(profile)

    PatientHomeViewModel(profileRepo)

    verify(exactly = 0) { Log.e(any(), any(), any()) }
  }

  @Test
  fun `failure without message sets the same error`() = runTest {
    coEvery { profileRepo.getCurrentProfile() } returns Result.failure(Exception())

    val vm = PatientHomeViewModel(profileRepo)

    assertEquals(R.string.patientHomeErrorLoadProfile, vm.uiState.value.errorMsgRes)
  }

  @Test
  fun `clearErrorMsg without error does nothing`() = runTest {
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(profile)
    val vm = PatientHomeViewModel(profileRepo)

    vm.clearErrorMsg()

    assertNull(vm.uiState.value.errorMsgRes)
    assertEquals(profile, vm.uiState.value.patientProfile)
  }

  @Test
  fun `clearErrorMsg keeps the profile`() = runTest {
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(null)
    val vm = PatientHomeViewModel(profileRepo)
    assertEquals(R.string.patientHomeErrorNoProfile, vm.uiState.value.errorMsgRes)

    vm.clearErrorMsg()

    assertNull(vm.uiState.value.errorMsgRes)
    assertEquals(PatientHomeUIState().patientProfile, vm.uiState.value.patientProfile)
  }
}
