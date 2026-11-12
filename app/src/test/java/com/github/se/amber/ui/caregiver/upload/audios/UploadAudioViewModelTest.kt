// Written by Lou-Anne Maier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.upload.audios

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.location.PlaceSearchRepository
import com.github.se.amber.data.media.audio.AudioRecorder
import com.github.se.amber.data.media.audio.AudioState
import com.github.se.amber.data.media.audio.FakeAudioPlayer
import com.github.se.amber.data.media.audio.FakeAudioRecorder
import com.github.se.amber.data.media.audio.LocalAudio
import com.github.se.amber.data.media.audio.WAVEFORM_BARS
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.AudioRecordingState
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.caregiver.upload.media.CommonMediaUIState
import com.github.se.amber.ui.theme.NO_PLAYED_FRACTION
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import java.io.File
import java.io.IOException
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for what is specific to [UploadAudioViewModel]: the recording, and the item made from
 * it. What all upload ViewModels share is tested in
 * [com.github.se.amber.ui.caregiver.upload.media.MediaViewModelTest]. Repositories are mocked and
 * the microphone is a [FakeAudioRecorder]: nothing touches the network.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UploadAudioViewModelTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private val testDispatcher = UnconfinedTestDispatcher()
  private val circle = CareCircle(id = "circle1")

  private lateinit var mediaRepo: MediaRepository
  private lateinit var authRepo: AuthRepository
  private lateinit var profileRepo: ProfileRepository
  private lateinit var circleRepo: CareCircleRepository
  private lateinit var placeRepo: PlaceSearchRepository
  private lateinit var recorder: FakeAudioRecorder
  private lateinit var player: FakeAudioPlayer

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mediaRepo = mockk(relaxed = true)
    authRepo = mockk()
    profileRepo = mockk()
    circleRepo = mockk()
    placeRepo = mockk()
    recorder = FakeAudioRecorder(tempFolder.newFolder())
    player = FakeAudioPlayer()

    every { authRepo.currentUser } returns mockk { every { uid } returns "user1" }
    coEvery { profileRepo.getProfile(any()) } returns
        Result.success(mockk<UserProfile> { every { circleIds } returns listOf("circle1") })
    coEvery { circleRepo.getCircle("circle1") } returns Result.success(circle)
    coEvery { mediaRepo.newMediaId(any()) } returns "media1"
    coEvery { placeRepo.search(any()) } returns Result.success(emptyList())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkAll()
  }

  // ---------- helpers ----------

  private fun createViewModel(recorder: AudioRecorder = this.recorder) =
      UploadAudioViewModel(
          mediaRepo = mediaRepo,
          authRepo = authRepo,
          profileRepo = profileRepo,
          circleRepo = circleRepo,
          placeRepo = placeRepo,
          cleanupScope = CoroutineScope(testDispatcher),
          recorder = recorder,
          player = player,
      )

  private fun recordedViewModel(): UploadAudioViewModel {
    val vm = createViewModel()
    vm.startRecording()
    vm.stopRecording()
    vm.setDescription("Grandma's story")
    return vm
  }

  /** Moves the clock on, so the ViewModel reads the microphone level the given times. */
  private fun advanceBy(ms: Long) {
    testDispatcher.scheduler.advanceTimeBy(ms)
    testDispatcher.scheduler.runCurrent()
  }

  private fun uploadedItem(): MediaItem.Audio {
    val slot = slot<MediaItem>()
    coVerify { mediaRepo.addMedia(any(), capture(slot), any()) }
    return slot.captured as MediaItem.Audio
  }

  private val recordedState =
      UploadAudioUIState(
          common = CommonMediaUIState(description = "Grandma's story", date = MemoryDate(1974)),
          audioFile = File("audio.m4a"),
          recordingState = AudioRecordingState.RECORDED,
      )

  // ---------- UploadAudioUIState ----------

  @Test
  fun canUploadIsFalseOnTheInitialState() {
    assertFalse(UploadAudioUIState().canUpload)
  }

  @Test
  fun canUploadIsTrueOnceRecordedAndCommonFieldsAreFilled() {
    assertTrue(recordedState.canUpload)
  }

  @Test
  fun canUploadIsFalseWithoutAudioFile() {
    assertFalse(recordedState.copy(audioFile = null).canUpload)
  }

  @Test
  fun canUploadIsFalseWithoutDescription() {
    val state = recordedState.copy(common = recordedState.common.copy(description = ""))
    assertFalse(state.canUpload)
  }

  @Test
  fun withCommonReplacesTheCommonFields_andKeepsTheAudioFile() {
    val common = CommonMediaUIState(description = "New")
    assertEquals(common, recordedState.withCommon(common).common)
    assertEquals(recordedState.audioFile, recordedState.withCommon(common).audioFile)
  }

  // ---------- initial state ----------

  @Test
  fun initialRecordingStateIsIdle() {
    assertEquals(AudioRecordingState.IDLE, createViewModel().uiState.value.recordingState)
  }

  @Test
  fun initialDateIsTheCurrentYear() {
    assertEquals(
        MemoryDate(LocalDate.now().year),
        createViewModel().uiState.value.common.date,
    )
  }

  // ---------- startRecording ----------

  @Test
  fun startRecordingStartsTheRecorder_andStartRecordingSetsTheRecordingState() {
    val vm = createViewModel()
    vm.startRecording()
    assertEquals(1, recorder.startCalls)
    assertEquals(AudioRecordingState.RECORDING, vm.uiState.value.recordingState)
  }

  @Test
  fun initialState_hasHeardNothing() {
    val vm = createViewModel()
    assertEquals(NO_PLAYED_FRACTION, vm.uiState.value.playedFraction, 0f)
  }

  // ---------- actions: TODO(), so they throw until implemented ----------

  @Test
  fun startRecordingTwiceStartsTheRecorderOnce() {
    val vm = createViewModel()
    vm.startRecording()
    vm.startRecording()
    assertEquals(1, recorder.startCalls)
  }

  @Test
  fun tappingRecordWhileTheRecorderIsStartingStartsItOnce() {
    recorder.startGate = CompletableDeferred()
    val vm = createViewModel()
    vm.startRecording()
    vm.startRecording()
    assertEquals(1, recorder.startCalls)
  }

  @Test
  fun theFirstTapStillRecordsOnceTheRecorderHasStarted() {
    val gate = CompletableDeferred<Unit>()
    recorder.startGate = gate
    val vm = createViewModel()
    vm.startRecording()
    vm.startRecording()
    gate.complete(Unit)
    assertEquals(AudioRecordingState.RECORDING, vm.uiState.value.recordingState)
  }

  @Test
  fun recordingCanStartAgainAfterAFailedStart() {
    recorder.startError = IOException("busy")
    val vm = createViewModel()
    vm.startRecording()
    recorder.startError = null
    vm.startRecording()
    assertEquals(2, recorder.startCalls)
  }

  @Test
  fun waveformGrowsWithTheMicrophoneLevel() {
    val vm = createViewModel()
    vm.startRecording()
    recorder.amplitude.value = 0.7f
    advanceBy(100)
    assertEquals(listOf(0.7f), vm.uiState.value.waveform)
  }

  @Test
  fun waveformGrowsForEachSampleEvenWhenTheLevelDoesNotChange() {
    val vm = createViewModel()
    vm.startRecording()
    recorder.amplitude.value = 0.4f
    advanceBy(300)
    assertEquals(3, vm.uiState.value.waveform.size)
  }

  @Test
  fun waveformStopsGrowingOnceTheRecordingIsStopped() {
    val vm = createViewModel()
    vm.startRecording()
    vm.stopRecording()
    advanceBy(500)
    assertEquals(recorder.waveform, vm.uiState.value.waveform)
  }

  @Test
  fun waveformKeepsOnlyTheLatestLevelsOnceFull() {
    val vm = createViewModel()
    vm.startRecording()
    repeat(WAVEFORM_BARS + 5) {
      recorder.amplitude.value = (it + 1) / 100f
      advanceBy(100)
    }
    assertEquals(WAVEFORM_BARS, vm.uiState.value.waveform.size)
  }

  @Test
  fun waveformEndsWithTheLatestLevelOnceFull() {
    val vm = createViewModel()
    vm.startRecording()
    repeat(WAVEFORM_BARS + 5) {
      recorder.amplitude.value = (it + 1) / 100f
      advanceBy(100)
    }
    assertEquals((WAVEFORM_BARS + 5) / 100f, vm.uiState.value.waveform.last())
  }

  @Test
  fun startRecordingWithoutPermissionAsksToAllowTheMicrophone() {
    recorder.startError = SecurityException()
    val vm = createViewModel()
    vm.startRecording()
    assertEquals(
        UploadAudioViewModelErrorMessage.MICROPHONE_NOT_ALLOWED.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
  }

  @Test
  fun startRecordingFailureSetsTheStartError_andStaysIdle() {
    recorder.startError = IllegalStateException("busy")
    val vm = createViewModel()
    vm.startRecording()
    assertEquals(
        UploadAudioViewModelErrorMessage.COULD_NOT_START_RECORDING.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
    assertEquals(AudioRecordingState.IDLE, vm.uiState.value.recordingState)
  }

  @Test
  fun startRecordingCanceledDoesNotShowAnError() {
    recorder.startError = CancellationException("canceled")
    val vm = createViewModel()
    vm.startRecording()
    assertNull(vm.uiState.value.common.errorMsgRes)
  }

  @Test
  fun startRecordingCanceledDoesNotStartTheRecording() {
    recorder.startError = CancellationException("canceled")
    val vm = createViewModel()
    vm.startRecording()
    assertEquals(AudioRecordingState.IDLE, vm.uiState.value.recordingState)
  }

  @Test
  fun startRecordingWithoutPermissionStaysIdle() {
    recorder.startError = SecurityException()
    val vm = createViewModel()
    vm.startRecording()
    assertEquals(AudioRecordingState.IDLE, vm.uiState.value.recordingState)
  }

  @Test
  fun startRecordingWithoutPermissionDoesNotKeepARecording() {
    recorder.startError = SecurityException()
    val vm = createViewModel()
    vm.startRecording()
    assertNull(recorder.file)
  }

  @Test
  fun startRecordingWithAnIOExceptionSetsTheCouldNotStartError() {
    recorder.startError = IOException("in use")
    val vm = createViewModel()
    vm.startRecording()
    assertEquals(
        UploadAudioViewModelErrorMessage.COULD_NOT_START_RECORDING.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
  }

  @Test
  fun startRecordingWithAnIOExceptionStaysIdle() {
    recorder.startError = IOException("in use")
    val vm = createViewModel()
    vm.startRecording()
    assertEquals(AudioRecordingState.IDLE, vm.uiState.value.recordingState)
  }

  // ---------- stopRecording ----------

  /** A recorder whose [stop] waits for [stopGate], like one that takes time to finish the file. */
  private class SlowStopRecorder(private val fake: FakeAudioRecorder) : AudioRecorder by fake {
    val stopGate = CompletableDeferred<Unit>()
    var stopCalls = 0
      private set

    override suspend fun stop(): LocalAudio {
      stopCalls++
      stopGate.await()
      return fake.stop()
    }
  }

  /** The recorder reached its limit and its recording is being saved: stop is still pending. */
  private fun savingAfterALimit(): Pair<UploadAudioViewModel, SlowStopRecorder> {
    val slow = SlowStopRecorder(recorder)
    val vm = createViewModel(slow)
    vm.startRecording()
    recorder.reachLimit()
    return vm to slow
  }

  @Test
  fun stopTappedWhileALimitSaveIsRunningDoesNotStopTheRecorderTwice() {
    val (vm, slow) = savingAfterALimit()
    vm.stopRecording()
    slow.stopGate.complete(Unit)
    assertEquals(1, slow.stopCalls)
  }

  @Test
  fun stopTappedWhileALimitSaveIsRunningStillKeepsTheRecording() {
    val (vm, slow) = savingAfterALimit()
    vm.stopRecording()
    slow.stopGate.complete(Unit)
    assertEquals(AudioRecordingState.RECORDED, vm.uiState.value.recordingState)
  }

  @Test
  fun stopTappedWhileALimitSaveIsRunningKeepsTheRecordedFile() {
    val (vm, slow) = savingAfterALimit()
    vm.stopRecording()
    slow.stopGate.complete(Unit)
    assertTrue(vm.uiState.value.audioFile!!.exists())
  }

  @Test
  fun stopRecordingWithoutRecordingDoesNothing() {
    createViewModel().stopRecording()
    assertEquals(0, recorder.stopCalls)
  }

  @Test
  fun stopRecordingSetsTheRecordedState() {
    val vm = createViewModel()
    vm.startRecording()
    vm.stopRecording()
    assertEquals(AudioRecordingState.RECORDED, vm.uiState.value.recordingState)
  }

  @Test
  fun stopRecordingKeepsTheDuration() {
    // Necessary included : AudioFile, Duration, Waveform
    recorder.durationMs = 4_500L
    val vm = createViewModel()
    vm.startRecording()
    vm.stopRecording()
    assertTrue(vm.uiState.value.audioFile?.exists() == true)
    assertEquals(4_500L, vm.uiState.value.durationMs)
    assertEquals(WAVEFORM_BARS, vm.uiState.value.waveform.size)
  }

  @Test
  fun recorderStoppingByItselfKeepsTheRecording() {
    val vm = createViewModel()
    vm.startRecording()
    recorder.reachLimit()
    assertEquals(AudioRecordingState.RECORDED, vm.uiState.value.recordingState)
  }

  @Test
  fun recorderStoppingByItself_TellsTheUserTheMaximumLengthWasReached() {
    val vm = createViewModel()
    vm.startRecording()
    recorder.reachLimit()
    assertEquals(
        UploadAudioViewModelErrorMessage.MAX_LENGTH_REACHED.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
  }

  @Test
  fun userStoppingTheRecordingDoesNotTellTheMaximumLengthWasReached() {
    val vm = createViewModel()
    vm.startRecording()
    vm.stopRecording()
    assertNull(vm.uiState.value.common.errorMsgRes)
  }

  @Test
  fun aFailedSaveAfterTheLimitShowsTheStopErrorInsteadOfTheMaximumLength() {
    recorder.stopError = IllegalStateException("too short")
    val vm = createViewModel()
    vm.startRecording()
    recorder.reachLimit()
    assertEquals(
        UploadAudioViewModelErrorMessage.COULD_NOT_STOP_RECORDING.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
  }

  @Test
  fun stopRecordingFailureSetsTheStopError_andGoesBackToIdle() {
    recorder.stopError = IllegalStateException("too short")
    val vm = createViewModel()
    vm.startRecording()
    vm.stopRecording()
    assertEquals(
        UploadAudioViewModelErrorMessage.COULD_NOT_STOP_RECORDING.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
    assertEquals(AudioRecordingState.IDLE, vm.uiState.value.recordingState)
  }

  @Test
  fun stopRecordingCanceledDoesNotShowAnError() {
    recorder.stopError = CancellationException("canceled")
    val vm = createViewModel()
    vm.startRecording()
    vm.stopRecording()
    assertNull(vm.uiState.value.common.errorMsgRes)
  }

  @Test
  fun stopRecordingCanceledDoesNotResetTheRecording() {
    recorder.stopError = CancellationException("canceled")
    val vm = createViewModel()
    vm.startRecording()
    vm.stopRecording()
    assertEquals(AudioRecordingState.RECORDING, vm.uiState.value.recordingState)
  }

  // ---------- deleteRecording ----------

  @Test
  fun deleteRecordingWhileRecordingCancelsTheRecorder_andGoesBackToIdle() {
    val vm = createViewModel()
    vm.startRecording()
    vm.deleteRecording()
    assertEquals(1, recorder.cancelCalls)
    assertEquals(AudioRecordingState.IDLE, vm.uiState.value.recordingState)
  }

  @Test
  fun deleteRecordingOnceRecordedDeletesTheFile_andClearsTheAudioFile() {
    val vm = recordedViewModel()
    val file = vm.uiState.value.audioFile!!
    vm.deleteRecording()
    assertFalse(file.exists())
    assertNull(vm.uiState.value.audioFile)
  }

  @Test
  fun deleteRecordingOnceRecordedClearsTheWaveform() {
    val vm = recordedViewModel()
    vm.deleteRecording()
    assertTrue(vm.uiState.value.waveform.isEmpty())
  }

  @Test
  fun recordingAgainAfterDeleteStartsTheRecorderAgain() {
    val vm = recordedViewModel()
    vm.deleteRecording()
    vm.startRecording()
    assertEquals(2, recorder.startCalls)
  }

  // ---------- playRecording ----------

  @Test
  fun playRecordingPlaysTheRecordedFile() {
    val vm = recordedViewModel()
    vm.playRecording()
    assertEquals(listOf(vm.uiState.value.audioFile), player.played)
  }

  @Test
  fun playRecordingBeforeRecordingDoesNotPlay() {
    createViewModel().playRecording()
    assertTrue(player.played.isEmpty())
  }

  @Test
  fun playRecordingWhileStillRecordingDoesNotPlay() {
    val vm = createViewModel()
    vm.startRecording()
    vm.playRecording()
    assertTrue(player.played.isEmpty())
  }

  @Test
  fun playRecordingFailureSetsThePlayError() {
    player.playError = IOException("corrupted")
    val vm = recordedViewModel()
    vm.playRecording()
    assertEquals(
        UploadAudioViewModelErrorMessage.COULD_NOT_PLAY_RECORDING.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
  }

  @Test
  fun playRecordingCanceledDoesNotShowAnError() {
    player.playError = CancellationException("canceled")
    val vm = recordedViewModel()
    vm.playRecording()
    assertNull(vm.uiState.value.common.errorMsgRes)
  }

  @Test
  fun playRecordingSwitchesToListening() {
    val vm = recordedViewModel()
    vm.playRecording()
    assertEquals(AudioRecordingState.LISTENING, vm.uiState.value.recordingState)
  }

  @Test
  fun playRecordingFailureStaysRecorded() {
    player.playError = IOException("corrupted")
    val vm = recordedViewModel()
    vm.playRecording()
    assertEquals(AudioRecordingState.RECORDED, vm.uiState.value.recordingState)
  }

  @Test
  fun listeningFollowsThePlayedFraction() {
    val vm = recordedViewModel()
    vm.playRecording()
    player.seekTo(player.durationMs / 2)
    assertEquals(0.5f, vm.uiState.value.playedFraction, 0f)
  }

  @Test
  fun listeningUntilTheEndGoesBackToRecorded() {
    val vm = recordedViewModel()
    vm.playRecording()
    player.finish()
    assertEquals(AudioRecordingState.RECORDED, vm.uiState.value.recordingState)
  }

  @Test
  fun listeningUntilTheEndResetsThePlayedFraction() {
    val vm = recordedViewModel()
    vm.playRecording()
    player.seekTo(player.durationMs / 2)
    player.finish()
    assertEquals(NO_PLAYED_FRACTION, vm.uiState.value.playedFraction, 0f)
  }

  @Test
  fun anotherAudioReplacingTheOneListenedToGoesBackToRecorded() {
    val vm = recordedViewModel()
    vm.playRecording()
    player.state.value = AudioState()
    assertEquals(AudioRecordingState.RECORDED, vm.uiState.value.recordingState)
  }

  // ---------- stopListening ----------

  @Test
  fun stopListeningGoesBackToRecorded() {
    val vm = recordedViewModel()
    vm.playRecording()
    vm.stopListening()
    assertEquals(AudioRecordingState.RECORDED, vm.uiState.value.recordingState)
  }

  @Test
  fun stopListeningStopsThePlayer() {
    val vm = recordedViewModel()
    vm.playRecording()
    vm.stopListening()
    assertNull(player.state.value.file)
  }

  @Test
  fun stopListeningResetsThePlayedFraction() {
    val vm = recordedViewModel()
    vm.playRecording()
    player.seekTo(player.durationMs / 2)
    vm.stopListening()
    assertEquals(NO_PLAYED_FRACTION, vm.uiState.value.playedFraction, 0f)
  }

  @Test
  fun stopListeningWhenNotListeningKeepsTheState() {
    val vm = recordedViewModel()
    vm.stopListening()
    assertEquals(AudioRecordingState.RECORDED, vm.uiState.value.recordingState)
  }

  @Test
  fun playingAgainAfterStoppingStartsFromTheBeginning() {
    val vm = recordedViewModel()
    vm.playRecording()
    vm.stopListening()
    vm.playRecording()
    assertEquals(2, player.played.size)
  }

  @Test
  fun deleteRecordingWhileListeningGoesBackToIdle() {
    val vm = recordedViewModel()
    vm.playRecording()
    vm.deleteRecording()
    assertEquals(AudioRecordingState.IDLE, vm.uiState.value.recordingState)
  }

  @Test
  fun deleteRecordingStopsThePlayback() {
    val vm = recordedViewModel()
    vm.playRecording()
    vm.deleteRecording()
    assertNull(player.state.value.file)
  }

  @Test
  fun clearingTheViewModelStopsThePlayback() {
    val vm = recordedViewModel()
    vm.playRecording()
    clear(vm)
    assertNull(player.state.value.file)
  }

  // ---------- upload ----------

  @Test
  fun uploadWithoutAudioAsksToRecordOne() {
    val vm = createViewModel()
    vm.uploadMedia()
    assertEquals(
        UploadAudioViewModelErrorMessage.RECORD_AUDIO.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
  }

  @Test
  fun uploadWhileStillRecordingAsksToRecordOne() {
    val vm = createViewModel()
    vm.startRecording()
    vm.uploadMedia()
    assertEquals(
        UploadAudioViewModelErrorMessage.RECORD_AUDIO.messageRes,
        vm.uiState.value.common.errorMsgRes,
    )
  }

  @Test
  fun uploadWithoutAudioDoesNotCallTheRepository() {
    createViewModel().uploadMedia()
    coVerify(exactly = 0) { mediaRepo.addMedia(any(), any(), any()) }
  }

  @Test
  fun uploadSendsTheRecordedFile() {
    val vm = recordedViewModel()
    val file = vm.uiState.value.audioFile
    vm.uploadMedia()
    coVerify(exactly = 1) { mediaRepo.addMedia(circle, any(), file) }
  }

  @Test
  fun uploadedItemIsAnAudioStoredAsM4aInTheCircle() {
    recordedViewModel().uploadMedia()
    assertEquals("careCircles/circle1/media/media1.m4a", uploadedItem().storagePath)
  }

  @Test
  fun uploadedItemKeepsTheNecessary() {
    // Necessary include : Duration, Waveform, Description
    recorder.durationMs = 4_500L
    recordedViewModel().uploadMedia()
    assertEquals(4_500L, uploadedItem().durationMs)
    assertEquals(List(WAVEFORM_BARS) { 0.5f }, uploadedItem().waveform)
    assertEquals("Grandma's story", uploadedItem().description)
  }

  @Test
  fun uploadedItemIsWrittenByTheSignedInUser() {
    recordedViewModel().uploadMedia()
    assertEquals("user1", uploadedItem().authorId)
  }

  // ---------- file that can't be deleted ----------

  /**
   * A recorded ViewModel whose file can't be deleted: a folder that isn't empty has taken its
   * place.
   */
  private fun recordedViewModelWithAFileThatCantBeDeleted(): UploadAudioViewModel {
    mockkStatic(Log::class)
    every { Log.w(any<String>(), any<String>()) } returns 0
    val vm = recordedViewModel()
    val file = vm.uiState.value.audioFile!!
    file.delete()
    file.mkdir()
    File(file, "inside").createNewFile()
    return vm
  }

  @Test
  fun deletingTheRecordingLogsAWarningWhenItsFileCantBeDeleted() {
    recordedViewModelWithAFileThatCantBeDeleted().deleteRecording()
    verify(exactly = 1) { Log.w(any<String>(), any<String>()) }
  }

  @Test
  fun clearingTheViewModelLogsAWarningWhenTheFileCantBeDeleted() {
    clear(recordedViewModelWithAFileThatCantBeDeleted())
    verify(exactly = 1) { Log.w(any<String>(), any<String>()) }
  }

  @Test
  fun cancelLogsAWarningWhenTheFileCantBeDeleted() {
    recordedViewModelWithAFileThatCantBeDeleted().cancel()
    verify(exactly = 1) { Log.w(any<String>(), any<String>()) }
  }

  // ---------- onCleared ----------

  /** Clears [vm] the way Android does when its screen is gone: its store is cleared. */
  private fun clear(vm: UploadAudioViewModel) {
    val store = ViewModelStore()
    ViewModelProvider(
        store,
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
        },
    )[UploadAudioViewModel::class.java]
    store.clear()
  }

  @Test
  fun clearingTheViewModelCancelsTheRecorder() {
    val vm = createViewModel()
    vm.startRecording()
    clear(vm)
    assertEquals(1, recorder.cancelCalls)
  }

  @Test
  fun clearingTheViewModelDeletesTheRecordedFileThatWasNotUploaded() {
    val vm = recordedViewModel()
    val file = vm.uiState.value.audioFile!!
    clear(vm)
    assertFalse(file.exists())
  }

  @Test
  fun clearingTheViewModelLeavesTheFileAloneWhileTheUploadIsRunning() {
    val vm = uploadInProgress()
    val file = vm.uiState.value.audioFile!!
    clear(vm)
    assertTrue(file.exists())
  }

  // ---------- cancel ----------

  /**
   * An upload that never finishes: the media is in the outbox but [uploadMedia] is still waiting.
   */
  private fun uploadInProgress(): UploadAudioViewModel {
    coEvery { mediaRepo.addMedia(any(), any(), any()) } coAnswers
        {
          CompletableDeferred<Unit>().await()
        }
    return recordedViewModel().also { it.uploadMedia() }
  }

  @Test
  fun cancelDuringTheUploadStopsShowingTheUpload() {
    val vm = uploadInProgress()
    vm.cancel()
    assertFalse(vm.uiState.value.common.isUploading)
  }

  @Test
  fun cancelDuringTheUploadTakesTheMediaOutOfTheOutbox() {
    uploadInProgress().cancel()
    coVerify(exactly = 1) { mediaRepo.discardUnsent("circle1", "media1") }
  }

  @Test
  fun cancelDuringTheUploadClearsTheAudioFileFromTheState() {
    val vm = uploadInProgress()
    vm.cancel()
    assertNull(vm.uiState.value.audioFile)
  }

  @Test
  fun cancelDuringTheUploadDeletesTheRecordedFile() {
    val vm = uploadInProgress()
    val file = vm.uiState.value.audioFile!!
    vm.cancel()
    assertFalse(file.exists())
  }

  @Test
  fun cancelDuringTheUploadClearsTheRecordingFromTheState() {
    val vm = uploadInProgress()
    vm.cancel()
    assertEquals(AudioRecordingState.IDLE, vm.uiState.value.recordingState)
  }

  @Test
  fun cancelWithoutUploadDoesNotTouchTheOutbox() {
    recordedViewModel().cancel()
    coVerify(exactly = 0) { mediaRepo.discardUnsent(any(), any()) }
  }

  @Test
  fun cancelWithoutUploadDeletesTheRecordedFile() {
    val vm = recordedViewModel()
    val file = vm.uiState.value.audioFile!!
    vm.cancel()
    assertFalse(file.exists())
  }

  @Test
  fun cancelAfterASuccessfulUploadDoesNotTakeTheMediaOutOfTheOutbox() {
    val vm = recordedViewModel()
    vm.uploadMedia()
    vm.cancel()
    coVerify(exactly = 0) { mediaRepo.discardUnsent(any(), any()) }
  }

  @Test
  fun cancelWhileRecordingCancelsTheRecorder() {
    val vm = createViewModel()
    vm.startRecording()
    vm.cancel()
    assertEquals(1, recorder.cancelCalls)
  }

  /** An upload that can't be canceled, like an outbox move already under way, until [gate]. */
  private fun uploadStillRunningUntil(gate: CompletableDeferred<Unit>): UploadAudioViewModel {
    coEvery { mediaRepo.addMedia(any(), any(), any()) } coAnswers
        {
          withContext(NonCancellable) { gate.await() }
        }
    return recordedViewModel().also { it.uploadMedia() }
  }

  @Test
  fun cancelWaitsForTheUploadToStopBeforeTakingTheMediaOutOfTheOutbox() {
    val vm = uploadStillRunningUntil(CompletableDeferred())
    vm.cancel()
    coVerify(exactly = 0) { mediaRepo.discardUnsent(any(), any()) }
  }

  @Test
  fun cancelTakesTheMediaOutOfTheOutboxOnceTheUploadHasStopped() {
    val gate = CompletableDeferred<Unit>()
    val vm = uploadStillRunningUntil(gate)
    vm.cancel()
    gate.complete(Unit)
    coVerify(exactly = 1) { mediaRepo.discardUnsent("circle1", "media1") }
  }

  @Test
  fun aFailingDiscardStillDeletesTheRecordedFile() {
    coEvery { mediaRepo.discardUnsent(any(), any()) } throws IOException("disk")
    val vm = uploadInProgress()
    val file = vm.uiState.value.audioFile!!
    vm.cancel()
    assertFalse(file.exists())
  }

  @Test
  fun aFailingDiscardIsLogged() {
    mockkStatic(Log::class)
    every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
    coEvery { mediaRepo.discardUnsent(any(), any()) } throws IOException("disk")
    uploadInProgress().cancel()
    verify(exactly = 1) { Log.e(any<String>(), any<String>(), any<Throwable>()) }
  }

  @Test
  fun aFailingDiscardDoesNotCrashTheCancel() {
    coEvery { mediaRepo.discardUnsent(any(), any()) } throws IOException("disk")
    val vm = uploadInProgress()
    vm.cancel()
    assertFalse(vm.uiState.value.common.isUploading)
  }
}
