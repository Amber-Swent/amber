// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.map

import com.github.se.amber.R
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.connectivity.FakeConnectivityMonitor
import com.github.se.amber.data.location.FakeLocationUpdatesRepository
import com.github.se.amber.data.location.FakeMediaLocationRepository
import com.github.se.amber.data.location.LocationUpdate
import com.github.se.amber.data.location.MediaLocationRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.location.DevicePosition
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.location.MediaMarker
import com.github.se.amber.model.location.NEARBY_RADIUS_METERS
import com.github.se.amber.model.location.TestPlaces.GENEVA
import com.github.se.amber.model.location.TestPlaces.LAUSANNE
import com.github.se.amber.model.location.TestPositions.APPROXIMATE_ACCURACY_METERS
import com.github.se.amber.model.location.TestPositions.GPS_ACCURACY_METERS
import com.github.se.amber.model.location.TestPositions.movedNorth
import com.github.se.amber.model.location.toMediaMarkers
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
  private val locationUpdates = FakeLocationUpdatesRepository(permission = true)

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

  private val mediaLocationRepository = FakeMediaLocationRepository(mapOf("circle1" to twoPins))
  private val denied = IllegalStateException("permission denied")

  /** Set by [useStandardDispatcher], for tests that must run the coroutines step by step. */
  private var standardDispatcher: TestDispatcher? = null

  /** The collections started by [showMap], cancelled after each test even if it fails. */
  private val shownMaps = mutableListOf<Job>()

  @Before
  fun setUp() {
    // Unconfined: the coroutines launched in init run immediately, no need to advance time
    Dispatchers.setMain(UnconfinedTestDispatcher())
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(caregiver)
  }

  @After
  fun tearDown() {
    shownMaps.forEach { it.cancel() }
    Dispatchers.resetMain()
  }

  private fun createViewModel(repository: MediaLocationRepository = mediaLocationRepository) =
      MediaMapViewModel(
          repository,
          profileRepository,
          activeCircleRepository,
          connectivity,
          locationUpdates,
      )

  /** Collects [MediaMapViewModel.locationState], as the screen does, until cancelled. */
  private fun MediaMapViewModel.showMap(): Job =
      CoroutineScope(Dispatchers.Main).launch { locationState.collect {} }.also { shownMaps += it }

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
    assertEquals(listOf("circle1"), mediaLocationRepository.observedCircleIds)
  }

  @Test
  fun `shows Empty when no media has a place, saying when the phone is offline`() {
    mediaLocationRepository.markers.value = emptyMap()
    val viewModel = createViewModel()
    assertEquals(MediaMapUIState.Empty(isOffline = false), viewModel.uiState.value)

    connectivity.isOnline.value = false
    assertEquals(MediaMapUIState.Empty(isOffline = true), viewModel.uiState.value)
  }

  @Test
  fun `Content follows the connection and new pins live`() {
    mediaLocationRepository.markers.value = mapOf("circle1" to genevaOnly)
    val viewModel = createViewModel()

    connectivity.isOnline.value = false
    assertEquals(
        MediaMapUIState.Content(markers = genevaOnly, isOffline = true),
        viewModel.uiState.value,
    )

    mediaLocationRepository.markers.value = mapOf("circle1" to twoPins)
    connectivity.isOnline.value = true
    assertEquals(
        MediaMapUIState.Content(markers = twoPins, isOffline = false),
        viewModel.uiState.value,
    )
  }

  @Test
  fun `the same active circle again changes nothing, another one shows its pins and closes the open pin`() {
    mediaLocationRepository.markers.value = mapOf("circle1" to twoPins, "circle2" to genevaOnly)
    val viewModel = createViewModel()
    viewModel.selectMarker(twoPins[1].id) // Geneva, also a pin of circle2

    activeCircleRepository.activeId.tryEmit("circle1")
    assertEquals(listOf("circle1"), mediaLocationRepository.observedCircleIds)
    assertEquals(twoPins[1], viewModel.content().selectedMarker)

    activeCircleRepository.activeId.tryEmit("circle2")
    assertEquals(MediaMapUIState.Content(markers = genevaOnly), viewModel.uiState.value)
    assertEquals(listOf("circle1", "circle2"), mediaLocationRepository.observedCircleIds)
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
    mediaLocationRepository.markers.value = mapOf("circle1" to updated)
    assertEquals(listOf(newPicture) + lausanneMedia, viewModel.content().selectedMarker?.media)

    mediaLocationRepository.markers.value = mapOf("circle1" to genevaOnly)
    assertNull(viewModel.content().selectedMarker)

    mediaLocationRepository.markers.value = mapOf("circle1" to twoPins)
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
    mediaLocationRepository.failures["circle1"] = denied

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
    mediaLocationRepository.failures["circle2"] = denied
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
    mediaLocationRepository.failures["circle1"] = denied
    val viewModel = createViewModel()

    mediaLocationRepository.failures.clear()
    viewModel.retry()
    assertEquals(MediaMapUIState.Content(markers = twoPins), viewModel.uiState.value)

    viewModel.selectMarker(twoPins[0].id)
    viewModel.retry()
    assertEquals(MediaMapUIState.Content(markers = twoPins), viewModel.uiState.value)
    assertEquals(listOf("circle1", "circle1", "circle1"), mediaLocationRepository.observedCircleIds)
  }

  // ---------------------------------------------------------------------------------------------
  // Live location
  // ---------------------------------------------------------------------------------------------

  // A good GPS fix at the Lausanne pin, then half the radius north of it, and five times the
  // radius north of it
  private val atLausannePin =
      DevicePosition(
          checkNotNull(lausanne.lat),
          checkNotNull(lausanne.lng),
          accuracyMeters = GPS_ACCURACY_METERS,
      )
  private val nearLausanne = atLausannePin.movedNorth(NEARBY_RADIUS_METERS / 2)
  private val farFromLausanne = atLausannePin.movedNorth(NEARBY_RADIUS_METERS * 5)

  private fun MediaMapViewModel.location() = locationState.value

  @Test
  fun `without the permission the map works without the GPS, until the user allows it`() {
    locationUpdates.permission = false
    val viewModel = createViewModel()
    viewModel.showMap()

    assertEquals(MediaMapLocationState.NoPermission, viewModel.location())
    assertEquals(0, locationUpdates.activeCollectors)
    assertEquals(twoPins, viewModel.content().markers)

    locationUpdates.permission = true
    viewModel.refreshPermission()
    assertEquals(MediaMapLocationState.Searching, viewModel.location())
    assertEquals(1, locationUpdates.activeCollectors)

    locationUpdates.permission = false
    viewModel.refreshPermission()
    assertEquals(MediaMapLocationState.NoPermission, viewModel.location())
    assertEquals(0, locationUpdates.activeCollectors)
  }

  @Test
  fun `a precise position shows the pin within the radius as near you, and none once further`() {
    val viewModel = createViewModel()
    viewModel.showMap()

    locationUpdates.updates.value = LocationUpdate.Position(nearLausanne)
    val located = viewModel.location() as MediaMapLocationState.Located
    assertEquals(MediaMapLocationState.Located(nearLausanne, nearbyMarker = twoPins[0]), located)
    assertTrue(located.isPrecise)

    locationUpdates.updates.value = LocationUpdate.Position(farFromLausanne)
    assertEquals(MediaMapLocationState.Located(farFromLausanne), viewModel.location())
  }

  @Test
  fun `an approximate or unknown accuracy shows the position but no pin near you`() {
    val viewModel = createViewModel()
    viewModel.showMap()

    val approximate = nearLausanne.copy(accuracyMeters = APPROXIMATE_ACCURACY_METERS)
    locationUpdates.updates.value = LocationUpdate.Position(approximate)
    val located = viewModel.location() as MediaMapLocationState.Located
    assertEquals(MediaMapLocationState.Located(approximate, nearbyMarker = null), located)
    assertFalse(located.isPrecise)

    val unknownAccuracy = nearLausanne.copy(accuracyMeters = null)
    locationUpdates.updates.value = LocationUpdate.Position(unknownAccuracy)
    assertEquals(MediaMapLocationState.Located(unknownAccuracy), viewModel.location())
  }

  @Test
  fun `the pin near you follows the pins as they change`() {
    mediaLocationRepository.markers.value = mapOf("circle1" to genevaOnly)
    val viewModel = createViewModel()
    viewModel.showMap()
    locationUpdates.updates.value = LocationUpdate.Position(nearLausanne)
    assertEquals(MediaMapLocationState.Located(nearLausanne), viewModel.location())

    mediaLocationRepository.markers.value = mapOf("circle1" to twoPins)

    assertEquals(
        MediaMapLocationState.Located(nearLausanne, nearbyMarker = twoPins[0]),
        viewModel.location(),
    )
  }

  @Test
  fun `follows the location turned off and on again, searching before the position`() {
    locationUpdates.updates.value = LocationUpdate.LocationOff
    val viewModel = createViewModel()
    viewModel.showMap()
    assertEquals(MediaMapLocationState.LocationOff, viewModel.location())

    locationUpdates.updates.value = LocationUpdate.Searching
    assertEquals(MediaMapLocationState.Searching, viewModel.location())

    locationUpdates.updates.value = LocationUpdate.Position(farFromLausanne)
    assertEquals(MediaMapLocationState.Located(farFromLausanne), viewModel.location())
  }

  @Test
  fun `a permission taken back while the map shows the position is NoPermission`() {
    val viewModel = createViewModel()
    viewModel.showMap()
    locationUpdates.updates.value = LocationUpdate.Position(nearLausanne)
    assertTrue(viewModel.location() is MediaMapLocationState.Located)

    locationUpdates.permission = false
    locationUpdates.failure.value = SecurityException("permission taken back")
    assertEquals(MediaMapLocationState.NoPermission, viewModel.location())

    locationUpdates.failure.value = null
    locationUpdates.permission = true
    viewModel.refreshPermission()
    assertEquals(
        MediaMapLocationState.Located(nearLausanne, nearbyMarker = twoPins[0]),
        viewModel.location(),
    )
  }

  @Test
  fun `a phone that can't give its position is Unavailable, and the map still works`() {
    locationUpdates.failure.value = IllegalStateException("Google Play services missing")
    val viewModel = createViewModel()
    viewModel.showMap()

    assertEquals(MediaMapLocationState.Unavailable, viewModel.location())
    assertEquals(twoPins, viewModel.content().markers)
  }

  @Test
  fun `the GPS runs only while the map is shown, and stops a while after it is closed`() {
    val scheduler = useStandardDispatcher().scheduler
    val viewModel = createViewModel()
    advanceMain()
    assertEquals(0, locationUpdates.activeCollectors)

    val map = viewModel.showMap()
    advanceMain()
    assertEquals(1, locationUpdates.activeCollectors)

    map.cancel()
    scheduler.advanceTimeBy(MAP_LOCATION_STOP_DELAY_MILLIS - 1)
    scheduler.runCurrent()
    assertEquals(1, locationUpdates.activeCollectors) // a screen rotation doesn't restart it

    scheduler.advanceTimeBy(2) // now just past the delay
    scheduler.runCurrent()
    assertEquals(0, locationUpdates.activeCollectors)
  }

  private fun useStandardDispatcher(): TestDispatcher =
      StandardTestDispatcher().also {
        standardDispatcher = it
        Dispatchers.setMain(it)
      }

  /** Runs everything waiting on the dispatcher of [useStandardDispatcher]. */
  private fun advanceMain() {
    val dispatcher = checkNotNull(standardDispatcher) { "Call useStandardDispatcher() first" }
    dispatcher.scheduler.advanceUntilIdle()
  }
}
