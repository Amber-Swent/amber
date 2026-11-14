// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.map

import com.github.se.amber.R
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.connectivity.FakeConnectivityMonitor
import com.github.se.amber.data.location.FakeMediaLocationRepository
import com.github.se.amber.data.location.MediaLocationRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.location.MediaMarker
import com.github.se.amber.model.location.TestPlaces.GENEVA
import com.github.se.amber.model.location.TestPlaces.LAUSANNE
import com.github.se.amber.model.location.toMediaMarkers
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaMapViewModelTest {

  /**
   * An [ActiveCircleRepository] whose active circle the test emits through [activeId]. Unlike a
   * StateFlow, it can emit the same id twice. Records the arguments it was asked with.
   */
  private class FakeActiveCircleRepository(initial: String?) : ActiveCircleRepository {
    val activeId = MutableSharedFlow<String?>(replay = 1).apply { tryEmit(initial) }
    val requests = mutableListOf<Pair<String, List<String>>>()

    override fun activeCircleId(uid: String, circleIds: List<String>): Flow<String?> {
      requests += uid to circleIds
      return activeId
    }

    override suspend fun setActiveCircle(uid: String, id: String) {
      activeId.emit(id)
    }

    override suspend fun clear(uid: String) {
      activeId.emit(null)
    }
  }

  private val profileRepository = mockk<ProfileRepository>()
  private val activeCircleRepository = FakeActiveCircleRepository("circle1")
  private val connectivity = FakeConnectivityMonitor(online = true)

  private val caregiver =
      UserProfile(
          uid = "caregiver",
          role = Role.CAREGIVER,
          person = Person(),
          circleIds = listOf("circle1", "circle2"),
      )

  private val lausanne = LAUSANNE
  private val geneva = GENEVA
  private val lausanneMedia = listOf(MediaItem.Picture(id = "p1", location = lausanne))
  private val genevaMedia = listOf(MediaItem.Audio(id = "a1", location = geneva))
  private val twoPins = (lausanneMedia + genevaMedia).toMediaMarkers()
  private val genevaOnly = genevaMedia.toMediaMarkers()

  private val locationRepository = FakeMediaLocationRepository(mapOf("circle1" to twoPins))
  private val denied = IllegalStateException("permission denied")

  /** Set by [useStandardDispatcher], for tests that must run the coroutines step by step. */
  private var standardDispatcher: TestDispatcher? = null

  @Before
  fun setUp() {
    // Unconfined: the coroutines launched in init run immediately, no need to advance time
    Dispatchers.setMain(UnconfinedTestDispatcher())
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(caregiver)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(repository: MediaLocationRepository = locationRepository) =
      MediaMapViewModel(repository, profileRepository, activeCircleRepository, connectivity)

  private fun MediaMapViewModel.content() = uiState.value as MediaMapUIState.Content

  // ---------------------------------------------------------------------------------------------
  // Content / Empty
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `shows the pins of the active circle picked among the user's circles`() {
    val viewModel = createViewModel()

    assertEquals(
        MediaMapUIState.Content(markers = twoPins, selectedMarker = null, isOffline = false),
        viewModel.uiState.value,
    )
    assertEquals(
        listOf("caregiver" to listOf("circle1", "circle2")),
        activeCircleRepository.requests,
    )
    assertEquals(listOf("circle1"), locationRepository.observedCircleIds)
  }

  @Test
  fun `shows Empty when no media has a place, saying when the phone is offline`() {
    locationRepository.markers.value = emptyMap()
    val viewModel = createViewModel()
    assertEquals(MediaMapUIState.Empty(isOffline = false), viewModel.uiState.value)

    connectivity.isOnline.value = false
    assertEquals(MediaMapUIState.Empty(isOffline = true), viewModel.uiState.value)
  }

  @Test
  fun `Content follows the connection and new pins live`() {
    locationRepository.markers.value = mapOf("circle1" to genevaOnly)
    val viewModel = createViewModel()

    connectivity.isOnline.value = false
    assertEquals(
        MediaMapUIState.Content(markers = genevaOnly, isOffline = true),
        viewModel.uiState.value,
    )

    locationRepository.markers.value = mapOf("circle1" to twoPins)
    connectivity.isOnline.value = true
    assertEquals(
        MediaMapUIState.Content(markers = twoPins, isOffline = false),
        viewModel.uiState.value,
    )
  }

  @Test
  fun `the same active circle again changes nothing, another one shows its pins and closes the open pin`() {
    locationRepository.markers.value = mapOf("circle1" to twoPins, "circle2" to genevaOnly)
    val viewModel = createViewModel()
    viewModel.selectMarker(twoPins[1].id) // Geneva, also a pin of circle2

    activeCircleRepository.activeId.tryEmit("circle1")
    assertEquals(listOf("circle1"), locationRepository.observedCircleIds)
    assertEquals(twoPins[1], viewModel.content().selectedMarker)

    activeCircleRepository.activeId.tryEmit("circle2")
    assertEquals(MediaMapUIState.Content(markers = genevaOnly), viewModel.uiState.value)
    assertEquals(listOf("circle1", "circle2"), locationRepository.observedCircleIds)
  }

  // ---------------------------------------------------------------------------------------------
  // Selecting a pin
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `selecting a pin opens it, an unknown one does nothing, dismissing closes it`() {
    val viewModel = createViewModel()

    viewModel.selectMarker(twoPins[1].id)
    assertEquals(twoPins[1], viewModel.content().selectedMarker)

    viewModel.selectMarker("not-a-pin")
    assertEquals(twoPins[1], viewModel.content().selectedMarker)

    viewModel.dismissMarker()
    assertNull(viewModel.content().selectedMarker)
  }

  @Test
  fun `selecting a pin while loading does nothing`() {
    useStandardDispatcher()
    val viewModel = createViewModel()
    assertEquals(MediaMapUIState.Loading, viewModel.uiState.value)

    viewModel.selectMarker(twoPins[0].id)
    advanceMain()

    assertNull(viewModel.content().selectedMarker)
  }

  @Test
  fun `the open pin follows its media, closes when they are gone and stays closed when they come back`() {
    val viewModel = createViewModel()
    viewModel.selectMarker(twoPins[0].id)

    val newPicture = MediaItem.Picture(id = "p2", location = lausanne)
    val updated = (listOf(newPicture) + lausanneMedia + genevaMedia).toMediaMarkers()
    locationRepository.markers.value = mapOf("circle1" to updated)
    assertEquals(listOf(newPicture) + lausanneMedia, viewModel.content().selectedMarker?.media)

    locationRepository.markers.value = mapOf("circle1" to genevaOnly)
    assertNull(viewModel.content().selectedMarker)

    locationRepository.markers.value = mapOf("circle1" to twoPins)
    assertNull(viewModel.content().selectedMarker)
  }

  // ---------------------------------------------------------------------------------------------
  // Errors
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `a profile that fails to load is a blocking error`() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.failure(denied)

    val viewModel = createViewModel()

    assertEquals(
        MediaMapUIState.BlockingError(R.string.mediaMapErrorLoadProfile),
        viewModel.uiState.value,
    )
  }

  @Test
  fun `a missing profile is a blocking error`() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(null)

    val viewModel = createViewModel()

    assertEquals(
        MediaMapUIState.BlockingError(R.string.mediaMapErrorNoProfile),
        viewModel.uiState.value,
    )
  }

  @Test
  fun `no active circle is a blocking error`() {
    activeCircleRepository.activeId.tryEmit(null)

    val viewModel = createViewModel()

    assertEquals(
        MediaMapUIState.BlockingError(R.string.mediaMapErrorNoCircle),
        viewModel.uiState.value,
    )
  }

  @Test
  fun `pins failing before any is shown is a blocking error`() {
    locationRepository.failures["circle1"] = denied

    val viewModel = createViewModel()

    assertEquals(
        MediaMapUIState.BlockingError(R.string.mediaMapErrorLoadMedia),
        viewModel.uiState.value,
    )
  }

  @Test
  fun `the new circle's pins failing at once is a blocking error, not an endless loading`() {
    // The switch and the failure both happen before the state catches up, as on the main thread
    useStandardDispatcher()
    locationRepository.failures["circle2"] = denied
    val viewModel = createViewModel()
    advanceMain()
    assertEquals(twoPins, viewModel.content().markers)

    activeCircleRepository.activeId.tryEmit("circle2")
    advanceMain()

    assertEquals(
        MediaMapUIState.BlockingError(R.string.mediaMapErrorLoadMedia),
        viewModel.uiState.value,
    )
  }

  @Test
  fun `pins failing after they are shown keep them, with a message to clear`() {
    val updates = MutableSharedFlow<List<MediaMarker>?>(replay = 1)
    val failingLater = MediaLocationRepository {
      flow { updates.collect { emit(it ?: throw denied) } }
    }
    updates.tryEmit(twoPins)
    val viewModel = createViewModel(failingLater)

    updates.tryEmit(null)

    assertEquals(
        MediaMapUIState.Content(markers = twoPins, errorMsgRes = R.string.mediaMapErrorLoadMedia),
        viewModel.uiState.value,
    )

    viewModel.clearErrorMsg()
    assertNull(viewModel.content().errorMsgRes)
  }

  @Test
  fun `retry loads again after an error, and from the pins closes the open pin`() {
    locationRepository.failures["circle1"] = denied
    val viewModel = createViewModel()

    locationRepository.failures.clear()
    viewModel.retry()
    assertEquals(MediaMapUIState.Content(markers = twoPins), viewModel.uiState.value)

    viewModel.selectMarker(twoPins[0].id)
    viewModel.retry()
    assertEquals(MediaMapUIState.Content(markers = twoPins), viewModel.uiState.value)
    assertEquals(listOf("circle1", "circle1", "circle1"), locationRepository.observedCircleIds)
  }

  private fun useStandardDispatcher() {
    standardDispatcher = StandardTestDispatcher().also { Dispatchers.setMain(it) }
  }

  /** Runs everything waiting on the dispatcher of [useStandardDispatcher]. */
  private fun advanceMain() {
    val dispatcher = checkNotNull(standardDispatcher) { "Call useStandardDispatcher() first" }
    dispatcher.scheduler.advanceUntilIdle()
  }
}
