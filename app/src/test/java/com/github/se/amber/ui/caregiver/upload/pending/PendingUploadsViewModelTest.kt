// Written by Alaabrine, with assistance from Cursor.
package com.github.se.amber.ui.caregiver.upload.pending

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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

class PendingUploadsViewModelTest {
  @get:Rule val mainRule = MainDispatcherRule()

  private lateinit var profileRepo: ProfileRepository
  private lateinit var circleRepo: CareCircleRepository
  private lateinit var mediaRepo: MediaRepository

  private val caregiver =
      UserProfile(uid = "cg1", role = Role.CAREGIVER, circleIds = listOf("circle1"))
  private val circle = CareCircle(id = "circle1", storageBucket = "bucket")
  private val waiting =
      OutboxEntry(MediaItem.Text(id = "waiting", description = "Note"), SendState.WAITING)
  private val sending = OutboxEntry(MediaItem.Audio(id = "sending"), SendState.SENDING)
  private val failed =
      OutboxEntry(MediaItem.Picture(id = "failed", description = "Day out"), SendState.FAILED)

  @Before
  fun setup() {
    profileRepo = mockk()
    circleRepo = mockk()
    mediaRepo = mockk(relaxed = true)
    mockkStatic(Log::class)
    every { Log.e(any(), any(), any()) } returns 0
    coEvery { profileRepo.getCurrentProfile() } returns Result.success(caregiver)
    coEvery { circleRepo.getCircle("circle1") } returns Result.success(circle)
    every { mediaRepo.observeOutbox("circle1") } returns
        MutableStateFlow(listOf(waiting, sending, failed))
  }

  @After fun tearDown() = unmockkAll()

  private fun createVm() = PendingUploadsViewModel(profileRepo, circleRepo, mediaRepo)

  @Test
  fun load_listsEverySendState() {
    val state = createVm().uiState.value

    assertEquals(listOf(waiting, sending, failed), state.entries)
    assertEquals(circle, state.circle)
    assertEquals(false, state.isLoading)
    assertNull(state.errorMsg)
  }

  @Test
  fun retry_sendsAFailedUploadAgain() {
    val vm = createVm()

    vm.retry("failed")
    vm.retry("waiting")
    vm.retry("sending")

    coVerify(exactly = 1) { mediaRepo.retrySend("circle1", "failed") }
  }

  @Test
  fun requestDiscard_opensConfirmationOnlyForAFailedUpload() {
    val vm = createVm()

    vm.requestDiscard("sending")
    assertNull(vm.uiState.value.discardMediaId)

    vm.requestDiscard("failed")
    assertEquals("failed", vm.uiState.value.discardMediaId)
  }

  @Test
  fun confirmDiscard_removesTheFailedUpload() {
    val vm = createVm()
    vm.requestDiscard("failed")

    vm.confirmDiscard()

    assertNull(vm.uiState.value.discardMediaId)
    coVerify(exactly = 1) { mediaRepo.discardUnsent("circle1", "failed") }
  }

  @Test
  fun confirmDiscard_doesNothingWhenTheUploadStartedSending() {
    val outbox = MutableStateFlow(listOf(failed))
    every { mediaRepo.observeOutbox("circle1") } returns outbox
    val vm = createVm()
    vm.requestDiscard("failed")
    outbox.value = listOf(failed.copy(state = SendState.SENDING))

    vm.confirmDiscard()

    coVerify(exactly = 0) { mediaRepo.discardUnsent(any(), any()) }
  }

  @Test
  fun load_keepsTheOutboxWhenTheCircleCannotBeReached() {
    coEvery { circleRepo.getCircle("circle1") } returns Result.failure(CareCircleError.Network)

    val state = createVm().uiState.value

    assertEquals(listOf(waiting, sending, failed), state.entries)
    assertEquals("circle1", state.circle?.id)
    assertEquals(OFFLINE_SHOWING_SAVED, state.errorMsg)
  }

  @Test
  fun load_profileNetworkFailure_asksToTryAgain() {
    coEvery { profileRepo.getCurrentProfile() } returns Result.failure(ProfileReadError.Network)

    val state = createVm().uiState.value

    assertTrue(state.entries.isEmpty())
    assertEquals(OFFLINE_TRY_AGAIN, state.errorMsg)
  }

  @Test
  fun retry_failureShowsAFixedMessage() {
    coEvery { mediaRepo.retrySend(any(), any()) } throws IOException("disk")

    val vm = createVm()
    vm.retry("failed")

    assertEquals("Could not retry this upload.", vm.uiState.value.errorMsg)
  }
}
