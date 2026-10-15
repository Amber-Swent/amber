// this code was written by Alaabrine
// with the aid of an AI coding agent
package com.github.se.amber.ui.createProfile.caregiverOnboarding

import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.accountCreation.caregiverOnboarding.CaregiverJoinOrCreateCircleUiState
import com.github.se.amber.ui.accountCreation.caregiverOnboarding.CaregiverJoinOrCreateCircleViewModel
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Unit tests for [CaregiverJoinOrCreateCircleViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class CaregiverJoinOrCreateCircleViewModelTests {

  private lateinit var profileRepository: ProfileRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
    profileRepository = mockk()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkAll()
  }

  @Test
  fun initialState_defaultsToEmptyDisplayName() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(null)

    val viewModel = CaregiverJoinOrCreateCircleViewModel(profileRepository = profileRepository)

    assertEquals(CaregiverJoinOrCreateCircleUiState(), viewModel.uiState.value)
  }

  @Test
  fun loadDisplayName_exposesFirstNameFromCurrentProfile() = runTest {
    coEvery { profileRepository.getCurrentProfile() } returns
        Result.success(
            UserProfile(
                uid = "uid",
                role = Role.CAREGIVER,
                person = Person(id = "uid", firstName = DISPLAY_NAME, lastName = "Hopper"),
            ),
        )

    val viewModel = CaregiverJoinOrCreateCircleViewModel(profileRepository = profileRepository)
    advanceUntilIdle()

    assertEquals(DISPLAY_NAME, viewModel.uiState.value.displayName)
  }

  @Test
  fun loadDisplayName_keepsEmptyNameWhenProfileMissing() = runTest {
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(null)

    val viewModel = CaregiverJoinOrCreateCircleViewModel(profileRepository = profileRepository)
    advanceUntilIdle()

    assertEquals("", viewModel.uiState.value.displayName)
  }

  @Test
  fun loadDisplayName_keepsEmptyNameWhenReadFails() = runTest {
    coEvery { profileRepository.getCurrentProfile() } returns
        Result.failure(IllegalStateException("offline"))

    val viewModel = CaregiverJoinOrCreateCircleViewModel(profileRepository = profileRepository)
    advanceUntilIdle()

    assertEquals("", viewModel.uiState.value.displayName)
  }

  private companion object {
    const val DISPLAY_NAME = "Sarah"
  }
}
