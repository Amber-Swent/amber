// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.patient.seeMedias.audios

import com.github.se.amber.R
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.media.audio.AudioState
import com.github.se.amber.data.media.audio.FakeAudioPlayer
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.media.audio.AudioPlaybackState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeeAudiosViewModelTest {
  private val mediaRepository = mockk<MediaRepository>()
  private val circleRepository = mockk<CareCircleRepository>()
  private val profileRepository = mockk<ProfileRepository>()
  private val profile =
      UserProfile(
          uid = "patient",
          role = Role.PATIENT,
          person = Person(),
          circleIds = listOf("circle1"),
      )
  private val circle = CareCircle(id = "circle1")
  private val audio = MediaItem.Audio(id = "a1")
  private val secondAudio = MediaItem.Audio(id = "a2")
  private val picture = MediaItem.Picture(id = "p1")
  private val text = MediaItem.Text(id = "t1")
  private lateinit var audioPlayer: FakeAudioPlayer
  private val testDispatcher = UnconfinedTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    audioPlayer = FakeAudioPlayer()
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(profile)
    coEvery { circleRepository.getCircle("circle1") } returns Result.success(circle)
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(emptyList())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel() =
      SeeAudiosViewModel(mediaRepository, circleRepository, profileRepository, audioPlayer)

  @Test
  fun play_pause_resume_and_restart_loaded_audio() {
    val file = File("audio.m4a")
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(audio))
    coEvery { mediaRepository.getFile(circle, audio) } returns file
    val viewModel = createViewModel()

    viewModel.onPlayPause(audio)
    assertEquals(listOf(file), audioPlayer.played)
    assertTrue(audioPlayer.state.value.isPlaying)
    assertTrue(viewModel.playbackStates.value[audio.id] is AudioPlaybackState.Playing)

    viewModel.onPlayPause(audio)
    assertEquals(false, audioPlayer.state.value.isPlaying)
    assertTrue(viewModel.playbackStates.value[audio.id] is AudioPlaybackState.Paused)
    viewModel.onPlayPause(audio)
    assertTrue(audioPlayer.state.value.isPlaying)
    coVerify(exactly = 1) { mediaRepository.getFile(circle, audio) }

    audioPlayer.seekTo(2_000L)
    assertEquals(2_000L, viewModel.playbackStates.value[audio.id]?.positionMs)
    viewModel.onRestart(audio)
    assertEquals(0L, audioPlayer.state.value.positionMs)
    assertEquals(0L, viewModel.playbackStates.value[audio.id]?.positionMs)
  }

  @Test
  fun switching_audio_loads_and_plays_the_selected_file() {
    val firstFile = File("first.m4a")
    val secondFile = File("second.m4a")
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flowOf(listOf(audio, secondAudio))
    coEvery { mediaRepository.getFile(circle, audio) } returns firstFile
    coEvery { mediaRepository.getFile(circle, secondAudio) } returns secondFile
    val viewModel = createViewModel()

    viewModel.onPlayPause(audio)
    viewModel.onPlayPause(secondAudio)
    viewModel.onRestart(audio)

    assertEquals(listOf(firstFile, secondFile), audioPlayer.played)
    assertEquals(secondFile, audioPlayer.state.value.file)
    assertTrue(viewModel.playbackStates.value[audio.id] is AudioPlaybackState.Idle)
    assertTrue(viewModel.playbackStates.value[secondAudio.id] is AudioPlaybackState.Playing)
  }

  @Test
  fun download_failure_does_not_crash_or_replace_content() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(audio))
    coEvery { mediaRepository.getFile(circle, audio) } throws IOException("offline")
    val viewModel = createViewModel()

    viewModel.onPlayPause(audio)

    assertTrue(viewModel.uiState.value is SeeAudiosUIState.Content)
    assertTrue(audioPlayer.played.isEmpty())
    assertTrue(viewModel.playbackStates.value[audio.id] is AudioPlaybackState.Error)
  }

  @Test
  fun runtime_download_failure_shows_card_error_and_can_be_retried() {
    val file = File("recovered.m4a")
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(audio))
    coEvery { mediaRepository.getFile(circle, audio) } throws RuntimeException("storage offline")
    val viewModel = createViewModel()

    viewModel.onPlayPause(audio)

    assertTrue(viewModel.uiState.value is SeeAudiosUIState.Content)
    assertTrue(viewModel.playbackStates.value[audio.id] is AudioPlaybackState.Error)
    assertTrue(audioPlayer.played.isEmpty())

    coEvery { mediaRepository.getFile(circle, audio) } returns file
    viewModel.onPlayPause(audio)

    assertEquals(listOf(file), audioPlayer.played)
    assertTrue(viewModel.playbackStates.value[audio.id] is AudioPlaybackState.Playing)
  }

  @Test
  fun failed_download_leaves_previous_audio_paused_and_can_be_retried() {
    val firstFile = File("first.m4a")
    val secondFile = File("second.m4a")
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flowOf(listOf(audio, secondAudio))
    coEvery { mediaRepository.getFile(circle, audio) } returns firstFile
    coEvery { mediaRepository.getFile(circle, secondAudio) } throws IOException("offline")
    val viewModel = createViewModel()
    viewModel.onPlayPause(audio)

    viewModel.onPlayPause(secondAudio)
    assertTrue(viewModel.playbackStates.value[audio.id] is AudioPlaybackState.Paused)
    assertTrue(viewModel.playbackStates.value[secondAudio.id] is AudioPlaybackState.Error)

    coEvery { mediaRepository.getFile(circle, secondAudio) } returns secondFile
    viewModel.onPlayPause(secondAudio)
    assertTrue(viewModel.playbackStates.value[audio.id] is AudioPlaybackState.Idle)
    assertTrue(viewModel.playbackStates.value[secondAudio.id] is AudioPlaybackState.Playing)
  }

  @Test
  fun failed_play_shows_an_error_on_its_card() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(audio))
    coEvery { mediaRepository.getFile(circle, audio) } returns File("broken.m4a")
    audioPlayer.playError = IOException("broken")
    val viewModel = createViewModel()

    viewModel.onPlayPause(audio)

    assertTrue(viewModel.playbackStates.value[audio.id] is AudioPlaybackState.Error)
    assertTrue(viewModel.uiState.value is SeeAudiosUIState.Content)
  }

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

  @Test
  fun non_audio_media_still_produces_empty_state() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flowOf(listOf(picture, text))

    val state = createViewModel().uiState.value as SeeAudiosUIState.Empty
    assertEquals(profile, state.patientProfile)
    assertEquals(circle, state.careCircle)
    assertNull(state.errorMsgRes)
  }

  @Test
  fun remains_loading_until_media_emits() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flow { awaitCancellation() }

    assertTrue(createViewModel().uiState.value is SeeAudiosUIState.Loading)
  }

  @Test
  fun reflects_live_audio_updates_and_removal() {
    val media = MutableStateFlow<List<MediaItem>>(emptyList())
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns media
    val viewModel = createViewModel()
    assertTrue(viewModel.uiState.value is SeeAudiosUIState.Empty)

    media.value = listOf(picture, audio)
    assertEquals(listOf(audio), (viewModel.uiState.value as SeeAudiosUIState.Content).allAudios)

    media.value = listOf(secondAudio, audio)
    assertEquals(
        listOf(secondAudio, audio),
        (viewModel.uiState.value as SeeAudiosUIState.Content).allAudios,
    )

    media.value = listOf(text)
    assertTrue(viewModel.uiState.value is SeeAudiosUIState.Empty)
  }

  @Test
  fun profile_failure_is_a_blocking_error() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.failure(Exception("offline"))

    val state = createViewModel().uiState.value as SeeAudiosUIState.BlockingError
    assertEquals(R.string.seeAudiosErrorLoadProfile, state.errorMsgRes)
  }

  @Test
  fun missing_circle_id_stops_before_loading_circle_or_media() {
    coEvery { profileRepository.getCurrentProfile() } returns
        Result.success(profile.copy(circleIds = listOf("", "  ")))

    val state = createViewModel().uiState.value as SeeAudiosUIState.BlockingError
    assertEquals(R.string.seeAudiosErrorNoCircle, state.errorMsgRes)
    coVerify(exactly = 0) { circleRepository.getCircle(any()) }
    coVerify(exactly = 0) { mediaRepository.observeApprovedMedia(any()) }
  }

  @Test
  fun first_nonblank_circle_id_is_used() {
    coEvery { profileRepository.getCurrentProfile() } returns
        Result.success(profile.copy(circleIds = listOf("", "circle1")))

    assertTrue(createViewModel().uiState.value is SeeAudiosUIState.Empty)
    coVerify(exactly = 1) { circleRepository.getCircle("circle1") }
    coVerify(exactly = 1) { mediaRepository.observeApprovedMedia("circle1") }
  }

  @Test
  fun circle_failure_is_a_blocking_error() {
    coEvery { circleRepository.getCircle("circle1") } returns Result.failure(Exception("offline"))

    val state = createViewModel().uiState.value as SeeAudiosUIState.BlockingError
    assertEquals(R.string.seeAudiosErrorLoadCircle, state.errorMsgRes)
    coVerify(exactly = 0) { mediaRepository.observeApprovedMedia(any()) }
  }

  @Test
  fun media_failure_before_first_emission_is_blocking() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow { throw RuntimeException("offline") }

    val state = createViewModel().uiState.value as SeeAudiosUIState.BlockingError
    assertEquals(R.string.seeAudiosErrorLoadMedia, state.errorMsgRes)
  }

  @Test
  fun media_failure_after_content_keeps_audio_until_error_is_cleared() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow {
          emit(listOf(picture, audio))
          throw RuntimeException("offline")
        }
    val viewModel = createViewModel()

    val stateWithError = viewModel.uiState.value as SeeAudiosUIState.Content
    assertEquals(listOf(audio), stateWithError.allAudios)
    assertEquals(R.string.seeAudiosErrorLoadMedia, stateWithError.errorMsgRes)

    viewModel.clearErrorMsg()

    val clearedState = viewModel.uiState.value as SeeAudiosUIState.Content
    assertEquals(listOf(audio), clearedState.allAudios)
    assertNull(clearedState.errorMsgRes)
  }

  @Test
  fun media_flow_reconnects_after_error_without_leaving_screen() {
    var subscriptions = 0
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow {
          subscriptions++
          if (subscriptions == 1) {
            emit(listOf(audio))
            throw IOException("offline")
          }
          emit(listOf(audio, secondAudio))
          awaitCancellation()
        }
    val viewModel = createViewModel()
    assertEquals(listOf(audio), (viewModel.uiState.value as SeeAudiosUIState.Content).allAudios)

    testDispatcher.scheduler.advanceTimeBy(3_000L)
    testDispatcher.scheduler.runCurrent()

    val state = viewModel.uiState.value as SeeAudiosUIState.Content
    assertEquals(listOf(audio, secondAudio), state.allAudios)
    assertNull(state.errorMsgRes)
    assertEquals(2, subscriptions)
  }

  @Test
  fun media_failure_after_empty_list_keeps_empty_state_until_error_is_cleared() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow {
          emit(emptyList())
          throw RuntimeException("offline")
        }
    val viewModel = createViewModel()
    assertEquals(
        R.string.seeAudiosErrorLoadMedia,
        (viewModel.uiState.value as SeeAudiosUIState.Empty).errorMsgRes,
    )

    viewModel.clearErrorMsg()

    assertNull((viewModel.uiState.value as SeeAudiosUIState.Empty).errorMsgRes)
  }

  @Test
  fun retry_recovers_from_a_blocking_error() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.failure(Exception("offline"))
    val viewModel = createViewModel()
    assertTrue(viewModel.uiState.value is SeeAudiosUIState.BlockingError)

    coEvery { profileRepository.getCurrentProfile() } returns Result.success(profile)
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(audio))
    viewModel.retry()

    assertEquals(listOf(audio), (viewModel.uiState.value as SeeAudiosUIState.Content).allAudios)
  }

  @Test
  fun retry_shows_loading_and_stops_observing_old_media() {
    val oldMedia = MutableStateFlow<List<MediaItem>>(listOf(audio))
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns oldMedia
    val viewModel = createViewModel()

    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flow { awaitCancellation() }
    viewModel.retry()
    oldMedia.value = listOf(secondAudio)

    assertTrue(viewModel.uiState.value is SeeAudiosUIState.Loading)
  }

  @Test
  fun refresh_updates_content_from_new_media() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(audio))
    val viewModel = createViewModel()

    val newMedia = MutableStateFlow<List<MediaItem>>(listOf(audio, secondAudio))
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns newMedia
    viewModel.refreshUIState()

    assertEquals(
        listOf(audio, secondAudio),
        (viewModel.uiState.value as SeeAudiosUIState.Content).allAudios,
    )
  }

  @Test
  fun refresh_keeps_current_content_while_reloading() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(audio))
    val viewModel = createViewModel()

    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flow { awaitCancellation() }
    viewModel.refreshUIState()

    assertEquals(listOf(audio), (viewModel.uiState.value as SeeAudiosUIState.Content).allAudios)
  }

  @Test
  fun download_stays_loading_until_file_arrives() {
    val pendingFile = CompletableDeferred<File>()
    val file = File("delayed.m4a")
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(audio))
    coEvery { mediaRepository.getFile(circle, audio) } coAnswers { pendingFile.await() }
    val viewModel = createViewModel()

    viewModel.onPlayPause(audio)
    assertTrue(viewModel.playbackStates.value[audio.id] is AudioPlaybackState.Loading)
    assertTrue(audioPlayer.played.isEmpty())

    pendingFile.complete(file)
    assertEquals(listOf(file), audioPlayer.played)
    assertTrue(viewModel.playbackStates.value[audio.id] is AudioPlaybackState.Playing)
  }

  @Test
  fun selecting_another_audio_cancels_the_pending_download() {
    val pendingFile = CompletableDeferred<File>()
    val secondFile = File("second.m4a")
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flowOf(listOf(audio, secondAudio))
    coEvery { mediaRepository.getFile(circle, audio) } coAnswers { pendingFile.await() }
    coEvery { mediaRepository.getFile(circle, secondAudio) } returns secondFile
    val viewModel = createViewModel()

    viewModel.onPlayPause(audio)
    viewModel.onPlayPause(secondAudio)
    pendingFile.complete(File("stale.m4a"))

    assertEquals(listOf(secondFile), audioPlayer.played)
    assertTrue(viewModel.playbackStates.value[audio.id] is AudioPlaybackState.Idle)
    assertTrue(viewModel.playbackStates.value[secondAudio.id] is AudioPlaybackState.Playing)
  }

  @Test
  fun switching_pauses_current_audio_and_tapping_it_cancels_the_new_download() {
    val firstFile = File("first.m4a")
    val pendingFile = CompletableDeferred<File>()
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flowOf(listOf(audio, secondAudio))
    coEvery { mediaRepository.getFile(circle, audio) } returns firstFile
    coEvery { mediaRepository.getFile(circle, secondAudio) } coAnswers { pendingFile.await() }
    val viewModel = createViewModel()

    viewModel.onPlayPause(audio)
    viewModel.onPlayPause(secondAudio)
    assertTrue(viewModel.playbackStates.value[audio.id] is AudioPlaybackState.Paused)
    assertTrue(viewModel.playbackStates.value[secondAudio.id] is AudioPlaybackState.Loading)
    assertEquals(false, audioPlayer.state.value.isPlaying)

    viewModel.onPlayPause(audio)
    pendingFile.complete(File("second.m4a"))

    assertTrue(audioPlayer.state.value.isPlaying)
    assertEquals(firstFile, audioPlayer.state.value.file)
    assertEquals(listOf(firstFile), audioPlayer.played)
    assertTrue(viewModel.playbackStates.value[secondAudio.id] is AudioPlaybackState.Idle)
  }

  @Test
  fun another_player_file_resets_active_card_and_restart_does_nothing() {
    val file = File("audio.m4a")
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(audio))
    coEvery { mediaRepository.getFile(circle, audio) } returns file
    val viewModel = createViewModel()
    viewModel.onPlayPause(audio)

    runBlocking { audioPlayer.play(File("other-screen.m4a")) }
    viewModel.onRestart(audio)

    assertTrue(viewModel.playbackStates.value[audio.id] is AudioPlaybackState.Idle)
    assertEquals(File("other-screen.m4a"), audioPlayer.state.value.file)
  }

  @Test
  fun player_unloading_file_while_playing_shows_error_on_active_card() {
    val file = File("audio.m4a")
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(audio))
    coEvery { mediaRepository.getFile(circle, audio) } returns file
    val viewModel = createViewModel()
    viewModel.onPlayPause(audio)

    audioPlayer.state.value = AudioState()

    assertTrue(viewModel.playbackStates.value[audio.id] is AudioPlaybackState.Error)
  }

  @Test
  fun refresh_failure_preserves_content_with_a_transient_error() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(audio))
    val viewModel = createViewModel()
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow { throw IOException("offline") }

    viewModel.refreshUIState()

    val state = viewModel.uiState.value as SeeAudiosUIState.Content
    assertEquals(listOf(audio), state.allAudios)
    assertEquals(R.string.seeAudiosErrorLoadMedia, state.errorMsgRes)
  }

  @Test
  fun playback_controls_do_nothing_without_content() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flow { awaitCancellation() }
    val viewModel = createViewModel()

    viewModel.onPlayPause(audio)
    viewModel.onRestart(audio)

    assertTrue(viewModel.playbackStates.value.isEmpty())
    assertTrue(audioPlayer.played.isEmpty())
    coVerify(exactly = 0) { mediaRepository.getFile(any(), any()) }
  }

  @Test
  fun finished_audio_is_paused_at_the_start_and_can_resume() {
    val file = File("audio.m4a")
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(audio))
    coEvery { mediaRepository.getFile(circle, audio) } returns file
    val viewModel = createViewModel()
    viewModel.onPlayPause(audio)
    audioPlayer.seekTo(2_000L)

    audioPlayer.finish()

    assertEquals(
        AudioPlaybackState.Paused(positionMs = 0L, durationMs = audioPlayer.durationMs),
        viewModel.playbackStates.value[audio.id],
    )
    viewModel.onPlayPause(audio)
    assertTrue(audioPlayer.state.value.isPlaying)
    coVerify(exactly = 1) { mediaRepository.getFile(circle, audio) }
  }

  @Test
  fun refresh_failure_preserves_empty_state_with_a_transient_error() {
    val viewModel = createViewModel()
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow { throw IOException("offline") }

    viewModel.refreshUIState()

    val state = viewModel.uiState.value as SeeAudiosUIState.Empty
    assertEquals(profile, state.patientProfile)
    assertEquals(circle, state.careCircle)
    assertEquals(R.string.seeAudiosErrorLoadMedia, state.errorMsgRes)
    viewModel.clearErrorMsg()
    assertNull((viewModel.uiState.value as SeeAudiosUIState.Empty).errorMsgRes)
  }

  @Test
  fun retry_after_media_failure_replaces_error_with_fresh_content() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow { throw IOException("offline") }
    val viewModel = createViewModel()
    assertTrue(viewModel.uiState.value is SeeAudiosUIState.BlockingError)
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(secondAudio))

    viewModel.retry()

    val state = viewModel.uiState.value as SeeAudiosUIState.Content
    assertEquals(listOf(secondAudio), state.allAudios)
    assertNull(state.errorMsgRes)
  }
}
