// this code was written with the aid of AI

package com.github.se.amber.ui.patient

import android.util.Log
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
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
  private lateinit var authRepo: AuthRepository

  @Before
  fun setup() {
    profileRepo = mockk()
    authRepo = mockk(relaxed = true)
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

    val vm = PatientHomeViewModel(profileRepo, authRepo) // init lance getProfile()

    assertEquals(profile, vm.uiState.value.patientProfile)
    assertNull(vm.uiState.value.errorMsg)
  }

  @Test
  fun `null profile sets error`() = runTest {
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(null)

    val vm = PatientHomeViewModel(profileRepo, authRepo)

    assertEquals("No profile found.", vm.uiState.value.errorMsg)
  }

  @Test
  fun `failure sets error with message`() = runTest {
    coEvery { profileRepo.getCurrentProfile() } returns Result.failure(Exception("boom"))

    val vm = PatientHomeViewModel(profileRepo, authRepo)

    assertEquals("Failed to load the profile: boom", vm.uiState.value.errorMsg)
  }
}
