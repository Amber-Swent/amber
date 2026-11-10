// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.patient.seeMedias.audios

import com.github.se.amber.R
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeeAudiosViewModelTest {
  private val mediaRepository = mockk<MediaRepository>()
  private val circleRepository = mockk<CareCircleRepository>()
  private val profileRepository = mockk<ProfileRepository>()
  private val profile =
      UserProfile(uid = "patient", role = Role.PATIENT, person = Person(), circleIds = listOf("circle1"))
  private val circle = CareCircle(id = "circle1")
  private val audio = MediaItem.Audio(id = "a1")

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(profile)
    coEvery { circleRepository.getCircle("circle1") } returns Result.success(circle)
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(emptyList())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel() =
      SeeAudiosViewModel(mediaRepository, circleRepository, profileRepository)

  @Test
  fun empty_when_there_are_no_audios() {
    assertTrue(createViewModel().uiState.value is SeeAudiosUIState.Empty)
  }

  @Test
  fun keeps_only_audios_in_repository_order() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flowOf(listOf(MediaItem.Picture(id = "p1"), audio, MediaItem.Audio(id = "a2")))

    val state = createViewModel().uiState.value as SeeAudiosUIState.Content
    assertEquals(listOf(audio, MediaItem.Audio(id = "a2")), state.allAudios)
    assertEquals(profile, state.patientProfile)
    assertEquals(circle, state.careCircle)
  }

  @Test
  fun missing_profile_uses_translated_error_resource() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(null)

    val state = createViewModel().uiState.value as SeeAudiosUIState.BlockingError
    assertEquals(R.string.seeAudiosErrorNoProfile, state.errorMsgRes)
  }
}
