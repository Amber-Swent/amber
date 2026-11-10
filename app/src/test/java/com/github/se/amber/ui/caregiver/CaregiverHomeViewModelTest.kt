// Written by Lou-Anne Maier and +, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Alaabrine, with assistance from Cursor.
// Modified by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver

import android.util.Log
import com.github.se.amber.R
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
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

class CaregiverHomeViewModelTest {
  @get:Rule val mainRule = MainDispatcherRule()

  private lateinit var profileRepo: ProfileRepository
  private lateinit var circleRepo: CareCircleRepository

  private val caregiver =
      UserProfile(uid = "cg1", role = Role.CAREGIVER, circleIds = listOf("circle1"))
  private val patient = UserProfile(uid = "p1", role = Role.PATIENT)
  private val circle = CareCircle(id = "circle1", patientId = "p1")

  @Before
  fun setup() {
    profileRepo = mockk()
    circleRepo = mockk()
    mockkStatic(Log::class)
    every { Log.e(any(), any(), any()) } returns 0
  }

  @After fun tearDown() = unmockkAll()

  // The ViewModel loads in init, so stubs must be set before creating it.
  private fun createVm() = CaregiverHomeViewModel(profileRepo, circleRepo)

  @Test
  fun load_success_fillsCaregiverCircleAndPatient() {
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(caregiver)
    coEvery { circleRepo.getCircle("circle1") } returns Result.success(circle)
    coEvery { profileRepo.getProfile("p1") } returns Result.success(patient)

    val state = createVm().uiState.value

    assertEquals(caregiver, state.caregiver)
    assertEquals(circle, state.careCircle)
    assertEquals(patient, state.patient)
    assertEquals(Role.PATIENT, state.patient.role)
    assertNull(state.errorMsgRes)
  }

  @Test
  fun load_profileNull_setsError() {
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(null)

    assertEquals(R.string.caregiverHomeErrorNoProfile, createVm().uiState.value.errorMsgRes)
  }

  @Test
  fun load_profileFailure_setsError() {
    coEvery { profileRepo.getCurrentProfile() } returns Result.failure(Exception("boom"))

    assertEquals(R.string.caregiverHomeErrorLoadProfile, createVm().uiState.value.errorMsgRes)
  }

  @Test
  fun load_noCircleId_setsError() {
    coEvery { profileRepo.getCurrentProfile() } returns
        Result.success(caregiver.copy(circleIds = listOf("", " ")))

    val state = createVm().uiState.value

    assertEquals(R.string.caregiverHomeErrorNoCircle, state.errorMsgRes)
    assertEquals(caregiver.uid, "cg1") // sanity check on fixture
  }

  @Test
  fun load_circleFailure_setsError() {
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(caregiver)
    coEvery { circleRepo.getCircle("circle1") } returns Result.failure(Exception("nope"))

    assertEquals(R.string.caregiverHomeErrorLoadCircle, createVm().uiState.value.errorMsgRes)
  }

  @Test
  fun load_blankPatientId_setsError() {
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(caregiver)
    coEvery { circleRepo.getCircle("circle1") } returns Result.success(circle.copy(patientId = ""))

    assertEquals(
        R.string.caregiverHomeErrorNoPatient,
        createVm().uiState.value.errorMsgRes,
    )
  }

  @Test
  fun load_patientProfileNull_setsError() {
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(caregiver)
    coEvery { circleRepo.getCircle("circle1") } returns Result.success(circle)
    coEvery { profileRepo.getProfile("p1") } returns Result.success(null)

    assertEquals(R.string.caregiverHomeErrorNoPatientProfile, createVm().uiState.value.errorMsgRes)
  }

  @Test
  fun load_patientProfileFailure_setsError() {
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(caregiver)
    coEvery { circleRepo.getCircle("circle1") } returns Result.success(circle)
    coEvery { profileRepo.getProfile("p1") } returns Result.failure(Exception("down"))

    assertEquals(
        R.string.caregiverHomeErrorLoadPatientProfile,
        createVm().uiState.value.errorMsgRes,
    )
  }

  @Test
  fun clearErrorMsg_resetsError() {
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(null)
    val vm = createVm()

    vm.clearErrorMsg()

    assertNull(vm.uiState.value.errorMsgRes)
  }
}
