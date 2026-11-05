/* Written by Lou-Anne Maier and +, with assistance from
 * Claude (Anthropic) via Claude Code.
 * Modified by Alaabrine, with assistance from Cursor.
 */
package com.github.se.amber.ui.caregiver

import android.util.Log
import com.github.se.amber.data.circle.CareCircleError
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileReadError
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.OutboxEntry
import com.github.se.amber.model.media.SendState
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.offline.OFFLINE_SHOWING_SAVED
import com.github.se.amber.ui.offline.OFFLINE_TRY_AGAIN
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
  private lateinit var mediaRepo: MediaRepository

  private val caregiver =
      UserProfile(uid = "cg1", role = Role.CAREGIVER, circleIds = listOf("circle1"))
  private val patient = UserProfile(uid = "p1", role = Role.PATIENT)
  private val circle = CareCircle(id = "circle1", patientId = "p1")

  @Before
  fun setup() {
    profileRepo = mockk()
    circleRepo = mockk()
    mediaRepo = mockk()
    every { mediaRepo.observeOutbox(any()) } returns flowOf(emptyList())
    mockkStatic(Log::class)
    every { Log.e(any(), any(), any()) } returns 0
  }

  @After fun tearDown() = unmockkAll()

  // The ViewModel loads in init, so stubs must be set before creating it.
  private fun createVm() = CaregiverHomeViewModel(profileRepo, circleRepo, mediaRepo)

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
    assertNull(state.errorMsg)
  }

  @Test
  fun load_profileNull_setsError() {
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(null)

    assertEquals("No profile found.", createVm().uiState.value.errorMsg)
  }

  @Test
  fun load_profileFailure_setsError() {
    coEvery { profileRepo.getCurrentProfile() } returns Result.failure(Exception("boom"))

    assertEquals("Failed to load the profile: boom", createVm().uiState.value.errorMsg)
  }

  @Test
  fun load_noCircleId_setsError() {
    coEvery { profileRepo.getCurrentProfile() } returns
        Result.success(caregiver.copy(circleIds = listOf("", " ")))

    val state = createVm().uiState.value

    assertEquals("No care circle found for this caregiver.", state.errorMsg)
    assertEquals(caregiver.uid, "cg1") // sanity check on fixture
  }

  @Test
  fun load_circleFailure_setsError() {
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(caregiver)
    coEvery { circleRepo.getCircle("circle1") } returns Result.failure(Exception("nope"))

    assertEquals("Failed to load the care circle: nope", createVm().uiState.value.errorMsg)
  }

  @Test
  fun load_blankPatientId_setsError() {
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(caregiver)
    coEvery { circleRepo.getCircle("circle1") } returns Result.success(circle.copy(patientId = ""))

    assertEquals(
        "No patient is linked to this care circle yet.",
        createVm().uiState.value.errorMsg,
    )
  }

  @Test
  fun load_patientProfileNull_setsError() {
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(caregiver)
    coEvery { circleRepo.getCircle("circle1") } returns Result.success(circle)
    coEvery { profileRepo.getProfile("p1") } returns Result.success(null)

    assertEquals("No patient profile found.", createVm().uiState.value.errorMsg)
  }

  @Test
  fun load_patientProfileFailure_setsError() {
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(caregiver)
    coEvery { circleRepo.getCircle("circle1") } returns Result.success(circle)
    coEvery { profileRepo.getProfile("p1") } returns Result.failure(Exception("down"))

    assertEquals("Failed to load the patient profile: down", createVm().uiState.value.errorMsg)
  }

  @Test
  fun clearErrorMsg_resetsError() {
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(null)
    val vm = createVm()

    vm.clearErrorMsg()

    assertNull(vm.uiState.value.errorMsg)
  }

  @Test
  fun load_profileNetworkFailure_usesOfflineMessage() {
    coEvery { profileRepo.getCurrentProfile() } returns Result.failure(ProfileReadError.Network)

    assertEquals(OFFLINE_TRY_AGAIN, createVm().uiState.value.errorMsg)
  }

  @Test
  fun load_patientNetworkFailure_keepsWhatWasSaved() {
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(caregiver)
    coEvery { circleRepo.getCircle("circle1") } returns Result.success(circle)
    coEvery { profileRepo.getProfile("p1") } returns Result.failure(ProfileReadError.Network)

    val state = createVm().uiState.value

    assertEquals(OFFLINE_SHOWING_SAVED, state.errorMsg)
    assertEquals(circle, state.careCircle)
  }

  @Test
  fun pendingUploadCountFollowsTheOutbox() {
    val outbox = MutableStateFlow(emptyList<OutboxEntry>())
    every { mediaRepo.observeOutbox("circle1") } returns outbox
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(caregiver)
    coEvery { circleRepo.getCircle("circle1") } returns Result.success(circle)
    coEvery { profileRepo.getProfile("p1") } returns Result.success(patient)

    val vm = createVm()
    assertEquals(0, vm.uiState.value.pendingUploadCount)

    outbox.value = listOf(OutboxEntry(MediaItem.Text(id = "t1"), SendState.WAITING))

    assertEquals(1, vm.uiState.value.pendingUploadCount)
  }

  @Test
  fun load_circleNetworkFailure_usesOfflineMessage() {
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(caregiver)
    coEvery { circleRepo.getCircle("circle1") } returns Result.failure(CareCircleError.Network)

    assertEquals(OFFLINE_TRY_AGAIN, createVm().uiState.value.errorMsg)
  }
}
