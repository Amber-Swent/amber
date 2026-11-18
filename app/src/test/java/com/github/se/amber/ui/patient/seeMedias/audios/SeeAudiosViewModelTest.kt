// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.patient.seeMedias.audios

import androidx.lifecycle.ViewModelStore
import com.github.se.amber.R
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.media.audio.AudioPlayer
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
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMediasUIState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
  fun download_failure_does_not_crash_or_replace_content() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(audio))
    coEvery { mediaRepository.getFile(circle, audio) } throws IOException("offline")
    val viewModel = createViewModel()

    viewModel.onPlayPause(audio)

    assertTrue(viewModel.uiState.value is SeeGeneralMediasUIState.Content)
    assertTrue(audioPlayer.played.isEmpty())
    assertTrue(viewModel.playbackStates.value[audio.id] is AudioPlaybackState.Error)
  }

  @Test
  fun failed_player_can_retry_the_same_audio() {
    val file = File("audio.m4a")
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(audio))
    coEvery { mediaRepository.getFile(circle, audio) } returns file
    audioPlayer.playError = IOException("broken")
    val viewModel = createViewModel()
    viewModel.onPlayPause(audio)
    assertTrue(viewModel.playbackStates.value[audio.id] is AudioPlaybackState.Error)

    audioPlayer.playError = null
    viewModel.onPlayPause(audio)

    assertEquals(listOf(file), audioPlayer.played)
    assertTrue(viewModel.playbackStates.value[audio.id] is AudioPlaybackState.Playing)
  }

  @Test
  fun empty_when_there_are_no_audios() {
    assertTrue(createViewModel().uiState.value is SeeGeneralMediasUIState.Empty)
  }

  @Test
  fun keeps_only_audios_in_repository_order() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flowOf(listOf(MediaItem.Picture(id = "p1"), audio, MediaItem.Audio(id = "a2")))

    val state = createViewModel().uiState.value as SeeGeneralMediasUIState.Content
    assertEquals(listOf(audio, MediaItem.Audio(id = "a2")), state.items)
    assertEquals(profile, state.patientProfile)
    assertEquals(circle, state.careCircle)
  }

  @Test
  fun missing_profile_uses_translated_error_resource() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(null)

    val state = createViewModel().uiState.value as SeeGeneralMediasUIState.BlockingError
    assertEquals(R.string.seeAudiosErrorNoProfile, state.errorMsgRes)
  }

  @Test
  fun non_audio_media_still_produces_empty_state() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flowOf(listOf(picture, text))

    val state = createViewModel().uiState.value as SeeGeneralMediasUIState.Empty
    assertEquals(profile, state.patientProfile)
    assertEquals(circle, state.careCircle)
    assertNull(state.errorMsgRes)
  }

  @Test
  fun remains_loading_until_media_emits() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flow { awaitCancellation() }

    assertTrue(createViewModel().uiState.value is SeeGeneralMediasUIState.Loading)
  }

  @Test
  fun reflects_live_audio_updates_and_removal() {
    val media = MutableStateFlow<List<MediaItem>>(emptyList())
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns media
    val viewModel = createViewModel()
    assertTrue(viewModel.uiState.value is SeeGeneralMediasUIState.Empty)

    media.value = listOf(picture, audio)
    assertEquals(listOf(audio), (viewModel.uiState.value as SeeGeneralMediasUIState.Content).items)

    media.value = listOf(secondAudio, audio)
    assertEquals(
        listOf(secondAudio, audio),
        (viewModel.uiState.value as SeeGeneralMediasUIState.Content).items,
    )

    media.value = listOf(text)
    assertTrue(viewModel.uiState.value is SeeGeneralMediasUIState.Empty)
  }

  @Test
  fun removing_playing_audio_stops_its_file() {
    val media = MutableStateFlow<List<MediaItem>>(listOf(audio))
    val file = File("audio.m4a")
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns media
    coEvery { mediaRepository.getFile(circle, audio) } returns file
    val viewModel = createViewModel()
    viewModel.onPlayPause(audio)
    assertEquals(file, audioPlayer.state.value.file)

    media.value = emptyList()

    assertTrue(viewModel.uiState.value is SeeGeneralMediasUIState.Empty)
    assertNull(audioPlayer.state.value.file)
  }

  @Test
  fun profile_failure_is_a_blocking_error() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.failure(Exception("offline"))

    val state = createViewModel().uiState.value as SeeGeneralMediasUIState.BlockingError
    assertEquals(R.string.seeAudiosErrorLoadProfile, state.errorMsgRes)
  }

  @Test
  fun clearing_message_does_not_remove_blocking_error() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.failure(IOException("offline"))
    val viewModel = createViewModel()

    viewModel.clearErrorMsg()

    assertEquals(
        R.string.seeAudiosErrorLoadProfile,
        (viewModel.uiState.value as SeeGeneralMediasUIState.BlockingError).errorMsgRes,
    )
  }

  @Test
  fun missing_circle_id_stops_before_loading_circle_or_media() {
    coEvery { profileRepository.getCurrentProfile() } returns
        Result.success(profile.copy(circleIds = listOf("", "  ")))

    val state = createViewModel().uiState.value as SeeGeneralMediasUIState.BlockingError
    assertEquals(R.string.seeAudiosErrorNoCircle, state.errorMsgRes)
    coVerify(exactly = 0) { circleRepository.getCircle(any()) }
    coVerify(exactly = 0) { mediaRepository.observeApprovedMedia(any()) }
  }

  @Test
  fun first_nonblank_circle_id_is_used() {
    coEvery { profileRepository.getCurrentProfile() } returns
        Result.success(profile.copy(circleIds = listOf("", "circle1")))

    assertTrue(createViewModel().uiState.value is SeeGeneralMediasUIState.Empty)
    coVerify(exactly = 1) { circleRepository.getCircle("circle1") }
    coVerify(exactly = 1) { mediaRepository.observeApprovedMedia("circle1") }
  }

  @Test
  fun circle_failure_is_a_blocking_error() {
    coEvery { circleRepository.getCircle("circle1") } returns Result.failure(Exception("offline"))

    val state = createViewModel().uiState.value as SeeGeneralMediasUIState.BlockingError
    assertEquals(R.string.seeAudiosErrorLoadCircle, state.errorMsgRes)
    coVerify(exactly = 0) { mediaRepository.observeApprovedMedia(any()) }
  }

  @Test
  fun media_failure_before_first_emission_is_blocking() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow { throw RuntimeException("offline") }

    val state = createViewModel().uiState.value as SeeGeneralMediasUIState.BlockingError
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

    val stateWithError = viewModel.uiState.value as SeeGeneralMediasUIState.Content
    assertEquals(listOf(audio), stateWithError.items)
    assertEquals(R.string.seeAudiosErrorLoadMedia, stateWithError.errorMsgRes)

    viewModel.clearErrorMsg()

    val clearedState = viewModel.uiState.value as SeeGeneralMediasUIState.Content
    assertEquals(listOf(audio), clearedState.items)
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
    assertEquals(listOf(audio), (viewModel.uiState.value as SeeGeneralMediasUIState.Content).items)

    testDispatcher.scheduler.advanceTimeBy(3_000L)
    testDispatcher.scheduler.runCurrent()

    val state = viewModel.uiState.value as SeeGeneralMediasUIState.Content
    assertEquals(listOf(audio, secondAudio), state.items)
    assertNull(state.errorMsgRes)
    assertEquals(2, subscriptions)
  }

  @Test
  fun media_flow_recovers_after_failure_before_first_emission() {
    var subscriptions = 0
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow {
          if (++subscriptions == 1) throw IOException("offline")
          emit(listOf(audio))
          awaitCancellation()
        }
    val viewModel = createViewModel()
    assertTrue(viewModel.uiState.value is SeeGeneralMediasUIState.BlockingError)

    testDispatcher.scheduler.advanceTimeBy(3_000L)
    testDispatcher.scheduler.runCurrent()

    assertEquals(listOf(audio), (viewModel.uiState.value as SeeGeneralMediasUIState.Content).items)
    assertEquals(2, subscriptions)
  }

  @Test
  fun repeated_media_failure_does_not_show_the_same_error_again() {
    var subscriptions = 0
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow {
          subscriptions++
          if (subscriptions == 1) emit(listOf(audio))
          if (subscriptions < 3) throw IOException("offline")
          emit(listOf(audio, secondAudio))
          awaitCancellation()
        }
    val viewModel = createViewModel()
    viewModel.clearErrorMsg()

    testDispatcher.scheduler.advanceTimeBy(3_000L)
    testDispatcher.scheduler.runCurrent()
    val stillWaiting = viewModel.uiState.value as SeeGeneralMediasUIState.Content
    assertEquals(listOf(audio), stillWaiting.items)
    assertNull(stillWaiting.errorMsgRes)

    testDispatcher.scheduler.advanceTimeBy(3_000L)
    testDispatcher.scheduler.runCurrent()
    assertEquals(
        listOf(audio, secondAudio),
        (viewModel.uiState.value as SeeGeneralMediasUIState.Content).items,
    )
    assertEquals(3, subscriptions)
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
        (viewModel.uiState.value as SeeGeneralMediasUIState.Empty).errorMsgRes,
    )

    viewModel.clearErrorMsg()

    assertNull((viewModel.uiState.value as SeeGeneralMediasUIState.Empty).errorMsgRes)
  }

  @Test
  fun retry_recovers_from_a_blocking_error() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.failure(Exception("offline"))
    val viewModel = createViewModel()
    assertTrue(viewModel.uiState.value is SeeGeneralMediasUIState.BlockingError)

    coEvery { profileRepository.getCurrentProfile() } returns Result.success(profile)
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(audio))
    viewModel.retry()

    assertEquals(listOf(audio), (viewModel.uiState.value as SeeGeneralMediasUIState.Content).items)
  }

  @Test
  fun retry_shows_loading_and_stops_observing_old_media() {
    val oldMedia = MutableStateFlow<List<MediaItem>>(listOf(audio))
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns oldMedia
    val viewModel = createViewModel()

    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flow { awaitCancellation() }
    viewModel.retry()
    oldMedia.value = listOf(secondAudio)

    assertTrue(viewModel.uiState.value is SeeGeneralMediasUIState.Loading)
  }

  @Test
  fun card_stays_loading_until_player_finishes_starting() {
    val file = File("audio.m4a")
    val started = CompletableDeferred<Unit>()
    val state = MutableStateFlow(AudioState())
    val player = mockk<AudioPlayer>()
    every { player.state } returns state
    coEvery { player.play(file) } coAnswers
        {
          state.value = AudioState(file = file, isPlaying = true)
          started.await()
        }
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(audio))
    coEvery { mediaRepository.getFile(circle, audio) } returns file
    val viewModel = SeeAudiosViewModel(mediaRepository, circleRepository, profileRepository, player)

    viewModel.onPlayPause(audio)
    assertTrue(viewModel.playbackStates.value[audio.id] is AudioPlaybackState.Loading)

    started.complete(Unit)
    assertTrue(viewModel.playbackStates.value[audio.id] is AudioPlaybackState.Playing)
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
  fun clearing_view_model_stops_its_loaded_audio() {
    val file = File("audio.m4a")
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(audio))
    coEvery { mediaRepository.getFile(circle, audio) } returns file
    val viewModel = createViewModel()
    viewModel.onPlayPause(audio)

    ViewModelStore().apply { put("audios", viewModel) }.clear()

    assertNull(audioPlayer.state.value.file)
  }

  @Test
  fun clearing_view_model_cancels_pending_download() {
    val pendingFile = CompletableDeferred<File>()
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(audio))
    coEvery { mediaRepository.getFile(circle, audio) } coAnswers { pendingFile.await() }
    val viewModel = createViewModel()
    viewModel.onPlayPause(audio)

    ViewModelStore().apply { put("audios", viewModel) }.clear()
    pendingFile.complete(File("late.m4a"))

    assertTrue(audioPlayer.played.isEmpty())
  }

  @Test
  fun clearing_view_model_before_playback_keeps_other_audio_playing() {
    val otherFile = File("other-screen.m4a")
    runBlocking { audioPlayer.play(otherFile) }
    val viewModel = createViewModel()

    ViewModelStore().apply { put("audios", viewModel) }.clear()

    assertEquals(otherFile, audioPlayer.state.value.file)
    assertTrue(audioPlayer.state.value.isPlaying)
  }

  @Test
  fun retry_after_media_failure_replaces_error_with_fresh_content() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow { throw IOException("offline") }
    val viewModel = createViewModel()
    assertTrue(viewModel.uiState.value is SeeGeneralMediasUIState.BlockingError)
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(secondAudio))

    viewModel.retry()

    val state = viewModel.uiState.value as SeeGeneralMediasUIState.Content
    assertEquals(listOf(secondAudio), state.items)
    assertNull(state.errorMsgRes)
  }
}
