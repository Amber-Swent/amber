// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.patient.seeMedias.audios

import com.github.se.amber.data.media.audio.AudioState
import com.github.se.amber.data.media.audio.FakeAudioPlayer
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.ui.media.audio.AudioPlaybackState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioPlaybackControllerTest {
  private val mediaRepository = mockk<MediaRepository>()
  private val circle = CareCircle(id = "circle1")
  private val audio = MediaItem.Audio(id = "a1")
  private val secondAudio = MediaItem.Audio(id = "a2")
  private lateinit var audioPlayer: FakeAudioPlayer
  private lateinit var scope: CoroutineScope

  @Before
  fun setUp() {
    audioPlayer = FakeAudioPlayer()
    // Unconfined: coroutines run eagerly, so no need to advance a scheduler
    scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  private fun createController(circleProvider: () -> CareCircle? = { circle }) =
      AudioPlaybackController(audioPlayer, mediaRepository, scope, circleProvider)

  @Test
  fun play_without_circle_does_nothing() {
    val controller = createController { null }

    controller.onPlayPause(audio)

    assertTrue(controller.playbackStates.value.isEmpty())
    assertTrue(audioPlayer.played.isEmpty())
    coVerify(exactly = 0) { mediaRepository.getFile(any(), any()) }
  }

  @Test
  fun first_play_downloads_and_plays_the_file() {
    val file = File("audio.m4a")
    coEvery { mediaRepository.getFile(circle, audio) } returns file
    val controller = createController()

    controller.onPlayPause(audio)

    assertEquals(listOf(file), audioPlayer.played)
    assertTrue(controller.playbackStates.value[audio.id] is AudioPlaybackState.Playing)
  }

  @Test
  fun play_pause_resume_and_restart_loaded_audio() {
    val file = File("audio.m4a")
    coEvery { mediaRepository.getFile(circle, audio) } returns file
    val controller = createController()

    controller.onPlayPause(audio)
    controller.onPlayPause(audio)
    assertTrue(controller.playbackStates.value[audio.id] is AudioPlaybackState.Paused)
    controller.onPlayPause(audio)
    assertTrue(controller.playbackStates.value[audio.id] is AudioPlaybackState.Playing)
    coVerify(exactly = 1) { mediaRepository.getFile(circle, audio) }

    audioPlayer.seekTo(2_000L)
    assertEquals(2_000L, controller.playbackStates.value[audio.id]?.positionMs)
    controller.onRestart(audio)
    assertEquals(0L, audioPlayer.state.value.positionMs)
    assertEquals(0L, controller.playbackStates.value[audio.id]?.positionMs)
  }

  @Test
  fun restart_does_nothing_when_audio_is_not_loaded() {
    val controller = createController()

    controller.onRestart(audio)

    assertTrue(controller.playbackStates.value.isEmpty())
    assertTrue(audioPlayer.played.isEmpty())
  }

  @Test
  fun card_stays_loading_until_file_arrives() {
    val pendingFile = CompletableDeferred<File>()
    val file = File("delayed.m4a")
    coEvery { mediaRepository.getFile(circle, audio) } coAnswers { pendingFile.await() }
    val controller = createController()

    controller.onPlayPause(audio)
    assertTrue(controller.playbackStates.value[audio.id] is AudioPlaybackState.Loading)
    assertTrue(audioPlayer.played.isEmpty())

    pendingFile.complete(file)
    assertEquals(listOf(file), audioPlayer.played)
    assertTrue(controller.playbackStates.value[audio.id] is AudioPlaybackState.Playing)
  }

  @Test
  fun download_failure_shows_error_and_can_be_retried() {
    val file = File("recovered.m4a")
    coEvery { mediaRepository.getFile(circle, audio) } throws IOException("offline")
    val controller = createController()

    controller.onPlayPause(audio)
    assertTrue(controller.playbackStates.value[audio.id] is AudioPlaybackState.Error)
    assertTrue(audioPlayer.played.isEmpty())

    coEvery { mediaRepository.getFile(circle, audio) } returns file
    controller.onPlayPause(audio)
    assertEquals(listOf(file), audioPlayer.played)
    assertTrue(controller.playbackStates.value[audio.id] is AudioPlaybackState.Playing)
  }

  @Test
  fun player_failure_shows_error_on_the_card() {
    coEvery { mediaRepository.getFile(circle, audio) } returns File("broken.m4a")
    audioPlayer.playError = IOException("broken")
    val controller = createController()

    controller.onPlayPause(audio)

    assertTrue(controller.playbackStates.value[audio.id] is AudioPlaybackState.Error)
  }

  @Test
  fun player_update_does_not_clear_playback_error() {
    val file = File("broken.m4a")
    coEvery { mediaRepository.getFile(circle, audio) } returns file
    audioPlayer.playError = IOException("broken")
    val controller = createController()
    controller.onPlayPause(audio)

    audioPlayer.state.value = AudioState(file = file)
    assertTrue(controller.playbackStates.value[audio.id] is AudioPlaybackState.Error)
    audioPlayer.state.value = AudioState()
    assertTrue(controller.playbackStates.value[audio.id] is AudioPlaybackState.Error)
  }

  @Test
  fun switching_audio_plays_the_selected_file_and_resets_the_previous_card() {
    val firstFile = File("first.m4a")
    val secondFile = File("second.m4a")
    coEvery { mediaRepository.getFile(circle, audio) } returns firstFile
    coEvery { mediaRepository.getFile(circle, secondAudio) } returns secondFile
    val controller = createController()

    controller.onPlayPause(audio)
    controller.onPlayPause(secondAudio)

    assertEquals(listOf(firstFile, secondFile), audioPlayer.played)
    assertTrue(controller.playbackStates.value[audio.id] is AudioPlaybackState.Idle)
    assertTrue(controller.playbackStates.value[secondAudio.id] is AudioPlaybackState.Playing)
  }

  @Test
  fun failed_download_leaves_previous_audio_paused() {
    coEvery { mediaRepository.getFile(circle, audio) } returns File("first.m4a")
    coEvery { mediaRepository.getFile(circle, secondAudio) } throws IOException("offline")
    val controller = createController()
    controller.onPlayPause(audio)

    controller.onPlayPause(secondAudio)

    assertTrue(controller.playbackStates.value[audio.id] is AudioPlaybackState.Paused)
    assertTrue(controller.playbackStates.value[secondAudio.id] is AudioPlaybackState.Error)
  }

  @Test
  fun selecting_another_audio_cancels_the_pending_download() {
    val pendingFile = CompletableDeferred<File>()
    val secondFile = File("second.m4a")
    coEvery { mediaRepository.getFile(circle, audio) } coAnswers { pendingFile.await() }
    coEvery { mediaRepository.getFile(circle, secondAudio) } returns secondFile
    val controller = createController()

    controller.onPlayPause(audio)
    controller.onPlayPause(secondAudio)
    pendingFile.complete(File("stale.m4a"))

    assertEquals(listOf(secondFile), audioPlayer.played)
    assertTrue(controller.playbackStates.value[audio.id] is AudioPlaybackState.Idle)
    assertTrue(controller.playbackStates.value[secondAudio.id] is AudioPlaybackState.Playing)
  }

  @Test
  fun tapping_the_playing_audio_cancels_the_new_download() {
    val firstFile = File("first.m4a")
    val pendingFile = CompletableDeferred<File>()
    coEvery { mediaRepository.getFile(circle, audio) } returns firstFile
    coEvery { mediaRepository.getFile(circle, secondAudio) } coAnswers { pendingFile.await() }
    val controller = createController()
    controller.onPlayPause(audio)
    controller.onPlayPause(secondAudio)
    assertTrue(controller.playbackStates.value[audio.id] is AudioPlaybackState.Paused)
    assertTrue(controller.playbackStates.value[secondAudio.id] is AudioPlaybackState.Loading)

    controller.onPlayPause(audio)
    pendingFile.complete(File("second.m4a"))

    assertTrue(audioPlayer.state.value.isPlaying)
    assertEquals(firstFile, audioPlayer.state.value.file)
    assertEquals(listOf(firstFile), audioPlayer.played)
    assertTrue(controller.playbackStates.value[secondAudio.id] is AudioPlaybackState.Idle)
  }

  @Test
  fun tapping_same_audio_during_download_uses_latest_request() {
    val firstDownload = CompletableDeferred<File>()
    val latestFile = File("latest.m4a")
    var downloads = 0
    coEvery { mediaRepository.getFile(circle, audio) } coAnswers
        {
          if (++downloads == 1) firstDownload.await() else latestFile
        }
    val controller = createController()

    controller.onPlayPause(audio)
    controller.onPlayPause(audio)
    firstDownload.complete(File("stale.m4a"))

    assertEquals(2, downloads)
    assertEquals(listOf(latestFile), audioPlayer.played)
    assertTrue(controller.playbackStates.value[audio.id] is AudioPlaybackState.Playing)
  }

  @Test
  fun another_player_file_resets_active_card_and_restart_does_nothing() {
    val otherFile = File("other-screen.m4a")
    coEvery { mediaRepository.getFile(circle, audio) } returns File("audio.m4a")
    val controller = createController()
    controller.onPlayPause(audio)

    runBlocking { audioPlayer.play(File("other-screen.m4a")) }
    audioPlayer.seekTo(2_000L)

    controller.onRestart(audio)

    assertTrue(controller.playbackStates.value[audio.id] is AudioPlaybackState.Idle)
    assertEquals(otherFile, audioPlayer.state.value.file)
    assertEquals(2_000L, audioPlayer.state.value.positionMs)
  }

  @Test
  fun player_unloading_file_while_playing_shows_error() {
    coEvery { mediaRepository.getFile(circle, audio) } returns File("audio.m4a")
    val controller = createController()
    controller.onPlayPause(audio)

    audioPlayer.state.value = AudioState()

    assertTrue(controller.playbackStates.value[audio.id] is AudioPlaybackState.Error)
  }

  @Test
  fun player_unloading_file_after_pause_returns_card_to_idle() {
    coEvery { mediaRepository.getFile(circle, audio) } returns File("audio.m4a")
    val controller = createController()
    controller.onPlayPause(audio)
    controller.onPlayPause(audio)
    assertTrue(controller.playbackStates.value[audio.id] is AudioPlaybackState.Paused)

    audioPlayer.state.value = AudioState()

    assertTrue(controller.playbackStates.value[audio.id] is AudioPlaybackState.Idle)
  }

  @Test
  fun finished_audio_is_paused_at_the_start_and_can_resume() {
    coEvery { mediaRepository.getFile(circle, audio) } returns File("audio.m4a")
    val controller = createController()
    controller.onPlayPause(audio)
    audioPlayer.seekTo(2_000L)

    audioPlayer.finish()

    assertEquals(
        AudioPlaybackState.Paused(positionMs = 0L, durationMs = audioPlayer.durationMs),
        controller.playbackStates.value[audio.id],
    )
    controller.onPlayPause(audio)
    assertTrue(audioPlayer.state.value.isPlaying)
    coVerify(exactly = 1) { mediaRepository.getFile(circle, audio) }
  }

  @Test
  fun removing_the_active_audio_stops_its_file() {
    coEvery { mediaRepository.getFile(circle, audio) } returns File("audio.m4a")
    val controller = createController()
    controller.onPlayPause(audio)

    controller.onItemsChanged(emptyList())

    assertNull(audioPlayer.state.value.file)
  }

  @Test
  fun removing_another_audio_keeps_the_active_file_playing() {
    val file = File("audio.m4a")
    coEvery { mediaRepository.getFile(circle, audio) } returns file
    val controller = createController()
    controller.onPlayPause(audio)

    controller.onItemsChanged(listOf(audio))

    assertEquals(file, audioPlayer.state.value.file)
    assertTrue(audioPlayer.state.value.isPlaying)
  }

  @Test
  fun removing_the_downloading_audio_cancels_its_download() {
    val pendingFile = CompletableDeferred<File>()
    coEvery { mediaRepository.getFile(circle, audio) } coAnswers { pendingFile.await() }
    val controller = createController()
    controller.onPlayPause(audio)

    controller.onItemsChanged(emptyList())
    pendingFile.complete(File("late.m4a"))

    assertTrue(audioPlayer.played.isEmpty())
  }

  @Test
  fun release_stops_the_loaded_audio() {
    coEvery { mediaRepository.getFile(circle, audio) } returns File("audio.m4a")
    val controller = createController()
    controller.onPlayPause(audio)

    controller.release()

    assertNull(audioPlayer.state.value.file)
  }

  @Test
  fun release_cancels_pending_download() {
    val pendingFile = CompletableDeferred<File>()
    coEvery { mediaRepository.getFile(circle, audio) } coAnswers { pendingFile.await() }
    val controller = createController()
    controller.onPlayPause(audio)

    controller.release()
    pendingFile.complete(File("late.m4a"))

    assertTrue(audioPlayer.played.isEmpty())
  }

  @Test
  fun release_before_playback_keeps_other_audio_playing() {
    val otherFile = File("other-screen.m4a")
    runBlocking { audioPlayer.play(otherFile) }
    val controller = createController()

    controller.release()

    assertEquals(otherFile, audioPlayer.state.value.file)
    assertTrue(audioPlayer.state.value.isPlaying)
  }
}
