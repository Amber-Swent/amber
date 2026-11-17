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
import com.github.se.amber.model.location.MediaMarker
import com.github.se.amber.model.location.NEARBY_EXIT_MARGIN_METERS
import com.github.se.amber.model.location.NEARBY_RADIUS_METERS
import com.github.se.amber.model.location.TestPlaces.GENEVA
import com.github.se.amber.model.location.TestPlaces.JUST_INSIDE_SAME_PLACE_RADIUS
import com.github.se.amber.model.location.TestPlaces.LAUSANNE
import com.github.se.amber.model.location.TestPlaces.latitude
import com.github.se.amber.model.location.TestPlaces.longitude
import com.github.se.amber.model.location.TestPlaces.movedNorth
import com.github.se.amber.model.location.TestPositions.APPROXIMATE_ACCURACY_METERS
import com.github.se.amber.model.location.TestPositions.GPS_ACCURACY_METERS
import com.github.se.amber.model.location.TestPositions.INDOOR_GPS_ACCURACY_METERS
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
  private val nearby = JUST_INSIDE_SAME_PLACE_RADIUS

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

  /**
   * Collects [MediaMapViewModel.locationState], as the screen does, until cancelled, adding each
   * state to [seen].
   */
  private fun MediaMapViewModel.showMap(
      seen: MutableList<MediaMapLocationState> = mutableListOf()
  ): Job =
      CoroutineScope(Dispatchers.Main)
          .launch { locationState.collect { seen += it } }
          .also { shownMaps += it }

  private fun MediaMapViewModel.content() = uiState.value as MediaMapUIState.Content

  // ---------------------------------------------------------------------------------------------
  // Content / Empty
  // ---------------------------------------------------------------------------------------------

  @Test
  fun showsThePinsOfTheActiveCirclePickedAmongTheUsersCircles() {
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
  fun showsEmptyWhenNoMediaHasAPlaceSayingWhenThePhoneIsOffline() {
    mediaLocationRepository.markers.value = emptyMap()
    val viewModel = createViewModel()
    assertEquals(MediaMapUIState.Empty(isOffline = false), viewModel.uiState.value)

    connectivity.isOnline.value = false
    assertEquals(MediaMapUIState.Empty(isOffline = true), viewModel.uiState.value)
  }

  @Test
  fun contentFollowsTheConnectionAndNewPinsLive() {
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
  fun theSameActiveCircleAgainChangesNothingAnotherOneShowsItsPinsAndClosesTheOpenPin() {
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
  fun selectingAPinOpensItAnUnknownOneDoesNothingDismissingClosesIt() {
    val viewModel = createViewModel()

    viewModel.selectMarker(twoPins[1].id)
    assertEquals(twoPins[1], viewModel.content().selectedMarker)

    viewModel.selectMarker("not-a-pin")
    assertEquals(twoPins[1], viewModel.content().selectedMarker)

    viewModel.dismissMarker()
    assertNull(viewModel.content().selectedMarker)
  }

  @Test
  fun selectingAPinWhileLoadingDoesNothing() {
    // The screen is already loading when the pin is selected, and its pins arrive after
    val updates = MutableSharedFlow<List<MediaMarker>>(extraBufferCapacity = 1)
    val viewModel = createViewModel { updates }
    assertEquals(MediaMapUIState.Loading, viewModel.uiState.value)

    viewModel.selectMarker(twoPins[0].id)
    updates.tryEmit(twoPins)

    assertNull(viewModel.content().selectedMarker)
  }

  @Test
  fun theOpenPinFollowsItsMediaClosesWhenTheyAreGoneAndStaysClosedWhenTheyComeBack() {
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

  @Test
  fun theOpenPinStaysOpenWhenItsOldestMediaIsRemovedOrAnOlderOneIsAddedAsItsMediaChangeOneAtATime() {
    // A pin's id is its oldest media's, so both change the id of the open pin
    val oldest = MediaItem.Picture(id = "p1", location = lausanne)
    val newerPoint = lausanne.movedNorth(nearby)
    val newer = MediaItem.Picture(id = "p2", location = newerPoint)
    mediaLocationRepository.markers.value =
        mapOf("circle1" to listOf(newer, oldest).toMediaMarkers())
    val viewModel = createViewModel()
    viewModel.selectMarker(oldest.id)

    mediaLocationRepository.markers.value = mapOf("circle1" to listOf(newer).toMediaMarkers())
    assertEquals(listOf(newer), viewModel.content().selectedMarker?.media)

    val olderStill = MediaItem.Picture(id = "p0", location = newerPoint.movedNorth(nearby))
    mediaLocationRepository.markers.value =
        mapOf("circle1" to listOf(newer, olderStill).toMediaMarkers())
    assertEquals(listOf(newer, olderStill), viewModel.content().selectedMarker?.media)

    // None of the media it was opened with is left, but it never lost all its media at once
    mediaLocationRepository.markers.value = mapOf("circle1" to listOf(olderStill).toMediaMarkers())
    assertEquals(listOf(olderStill), viewModel.content().selectedMarker?.media)
  }

  @Test
  fun anOpenPinSplitInTwoByTheRemovalOfItsOldestMediaStaysOpenOnItsNewestMedia() {
    // north and south share the pin of the media between them, but are too far apart without it
    val between = MediaItem.Picture(id = "p1", location = lausanne)
    val south = MediaItem.Picture(id = "p2", location = lausanne.movedNorth(-nearby))
    val north = MediaItem.Picture(id = "p3", location = lausanne.movedNorth(nearby))
    mediaLocationRepository.markers.value =
        mapOf("circle1" to listOf(north, south, between).toMediaMarkers())
    val viewModel = createViewModel()
    viewModel.selectMarker(between.id)

    mediaLocationRepository.markers.value =
        mapOf("circle1" to listOf(north, south).toMediaMarkers())

    assertEquals(listOf(listOf(north), listOf(south)), viewModel.content().markers.map { it.media })
    assertEquals(listOf(north), viewModel.content().selectedMarker?.media)
  }

  // ---------------------------------------------------------------------------------------------
  // Errors
  // ---------------------------------------------------------------------------------------------

  @Test
  fun aProfileThatFailsToLoadIsABlockingError() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.failure(denied)

    val viewModel = createViewModel()

    assertEquals(
        MediaMapUIState.BlockingError(R.string.mediaMapErrorLoadProfile),
        viewModel.uiState.value,
    )
  }

  @Test
  fun aMissingProfileIsABlockingError() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(null)

    val viewModel = createViewModel()

    assertEquals(
        MediaMapUIState.BlockingError(R.string.mediaMapErrorNoProfile),
        viewModel.uiState.value,
    )
  }

  @Test
  fun noActiveCircleIsABlockingError() {
    activeCircleRepository.activeId.tryEmit(null)

    val viewModel = createViewModel()

    assertEquals(
        MediaMapUIState.BlockingError(R.string.mediaMapErrorNoCircle),
        viewModel.uiState.value,
    )
  }

  @Test
  fun pinsFailingBeforeAnyIsShownIsABlockingError() {
    mediaLocationRepository.failures["circle1"] = denied

    val viewModel = createViewModel()

    assertEquals(
        MediaMapUIState.BlockingError(R.string.mediaMapErrorLoadMedia),
        viewModel.uiState.value,
    )
  }

  @Test
  fun theNewCirclesPinsFailingAtOnceIsABlockingErrorNotAnEndlessLoading() {
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
  fun pinsFailingAfterTheyAreShownKeepThemWithAMessageThatRetryOrClearingRemoves() {
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

    updates.tryEmit(twoPins)
    viewModel.retry()
    assertEquals(MediaMapUIState.Content(markers = twoPins), viewModel.uiState.value)

    updates.tryEmit(null)
    assertEquals(R.string.mediaMapErrorLoadMedia, viewModel.content().errorMsgRes)
    viewModel.clearErrorMsg()
    assertNull(viewModel.content().errorMsgRes)
  }

  @Test
  fun retryLoadsAgainAfterAnErrorAndFromThePinsClosesTheOpenPinAndStopsThePreviousLoad() {
    mediaLocationRepository.failures["circle1"] = denied
    val viewModel = createViewModel()

    mediaLocationRepository.failures.clear()
    viewModel.retry()
    assertEquals(MediaMapUIState.Content(markers = twoPins), viewModel.uiState.value)

    viewModel.selectMarker(twoPins[0].id)
    viewModel.retry()
    assertEquals(MediaMapUIState.Content(markers = twoPins), viewModel.uiState.value)
    assertEquals(listOf("circle1", "circle1", "circle1"), mediaLocationRepository.observedCircleIds)
    assertEquals(1, mediaLocationRepository.markers.subscriptionCount.value)
  }

  // ---------------------------------------------------------------------------------------------
  // Live location
  // ---------------------------------------------------------------------------------------------

  // A good GPS fix at the Lausanne pin, then half the radius north of it (near you), and five
  // times the radius north of it (well out of it)
  private val atLausannePin =
      DevicePosition(LAUSANNE.latitude, LAUSANNE.longitude, accuracyMeters = GPS_ACCURACY_METERS)
  private val halfRadius = NEARBY_RADIUS_METERS / 2
  private val wellOutsideRadius = NEARBY_RADIUS_METERS * 5
  private val nearLausanne = atLausannePin.movedNorth(halfRadius)
  private val farFromLausanne = atLausannePin.movedNorth(wellOutsideRadius)

  private fun MediaMapViewModel.location() = locationState.value

  @Test
  fun withoutThePermissionTheMapWorksWithoutTheGPSUntilTheUserAllowsIt() {
    locationUpdates.permission = false
    val viewModel = createViewModel()
    viewModel.showMap()

    assertEquals(MediaMapLocationState.NoPermission, viewModel.location())
    assertFalse(locationUpdates.isRunning)
    assertEquals(twoPins, viewModel.content().markers)

    locationUpdates.permission = true
    viewModel.refreshPermission()
    assertEquals(MediaMapLocationState.Searching, viewModel.location())
    assertTrue(locationUpdates.isRunning)

    locationUpdates.permission = false
    viewModel.refreshPermission()
    assertEquals(MediaMapLocationState.NoPermission, viewModel.location())
    assertFalse(locationUpdates.isRunning)
  }

  @Test
  fun thePinsNearYouNeedAPositionKnownWellEnoughIndoorsIncluded() {
    val viewModel = createViewModel()
    viewModel.showMap()
    fun locatedAt(position: DevicePosition): MediaMapLocationState.Located {
      locationUpdates.updates.value = LocationUpdate.Position(position)
      return viewModel.location() as MediaMapLocationState.Located
    }

    val outdoors = locatedAt(nearLausanne)
    assertEquals(MediaMapLocationState.Located(nearLausanne, listOf(twoPins[0])), outdoors)
    assertTrue(outdoors.isPrecise)

    val indoorPosition = nearLausanne.copy(accuracyMeters = INDOOR_GPS_ACCURACY_METERS)
    assertEquals(
        MediaMapLocationState.Located(indoorPosition, listOf(twoPins[0])),
        locatedAt(indoorPosition),
    )

    assertEquals(MediaMapLocationState.Located(farFromLausanne), locatedAt(farFromLausanne))

    val notPrecise =
        listOf(
            nearLausanne.copy(accuracyMeters = APPROXIMATE_ACCURACY_METERS),
            nearLausanne.copy(accuracyMeters = null),
            nearLausanne.copy(isLastKnown = true),
        )
    notPrecise.forEach { position ->
      val located = locatedAt(position)
      assertEquals(MediaMapLocationState.Located(position), located)
      assertFalse(located.isPrecise)
    }
  }

  @Test
  fun aPinNearYouStaysNearAtTheEdgeAndLeavesPastTheExitMargin() {
    val viewModel = createViewModel()
    viewModel.showMap()
    val atEdge = atLausannePin.movedNorth(NEARBY_RADIUS_METERS + NEARBY_EXIT_MARGIN_METERS / 2)
    val pastMargin = atLausannePin.movedNorth(NEARBY_RADIUS_METERS + NEARBY_EXIT_MARGIN_METERS * 2)

    locationUpdates.updates.value = LocationUpdate.Position(atEdge)
    assertEquals(MediaMapLocationState.Located(atEdge), viewModel.location())

    locationUpdates.updates.value = LocationUpdate.Position(nearLausanne)
    locationUpdates.updates.value = LocationUpdate.Position(atEdge)
    assertEquals(MediaMapLocationState.Located(atEdge, listOf(twoPins[0])), viewModel.location())

    locationUpdates.updates.value = LocationUpdate.Position(pastMargin)
    assertEquals(MediaMapLocationState.Located(pastMargin), viewModel.location())
  }

  @Test
  fun aPinNearYouKeepsItsExitMarginWhileTheSamePinsReload() {
    val viewModel = createViewModel()
    viewModel.showMap()
    val atEdge = atLausannePin.movedNorth(NEARBY_RADIUS_METERS + NEARBY_EXIT_MARGIN_METERS / 2)
    locationUpdates.updates.value = LocationUpdate.Position(nearLausanne)
    locationUpdates.updates.value = LocationUpdate.Position(atEdge)

    viewModel.retry()

    assertEquals(MediaMapLocationState.Located(atEdge, listOf(twoPins[0])), viewModel.location())
  }

  @Test
  fun thePinNearYouFollowsThePinsAsTheyChange() {
    mediaLocationRepository.markers.value = mapOf("circle1" to genevaOnly)
    val viewModel = createViewModel()
    viewModel.showMap()
    locationUpdates.updates.value = LocationUpdate.Position(nearLausanne)
    assertEquals(MediaMapLocationState.Located(nearLausanne), viewModel.location())

    mediaLocationRepository.markers.value = mapOf("circle1" to twoPins)

    assertEquals(
        MediaMapLocationState.Located(nearLausanne, nearbyMarkers = listOf(twoPins[0])),
        viewModel.location(),
    )
  }

  @Test
  fun followsTheLocationTurnedOffAndOnAgainSearchingBeforeThePosition() {
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
  fun theMapStartsOnTheRealLocationStateWithoutSearchingFirstAlsoWhenShownAgain() {
    val scheduler = useStandardDispatcher().scheduler
    locationUpdates.updates.value = LocationUpdate.LocationOff
    val viewModel = createViewModel()
    val seenFirstTime = mutableListOf<MediaMapLocationState>()
    val firstMap = viewModel.showMap(seenFirstTime)
    advanceMain()
    assertEquals(listOf(MediaMapLocationState.LocationOff), seenFirstTime)

    locationUpdates.updates.value = LocationUpdate.Position(farFromLausanne)
    advanceMain()
    firstMap.cancel()
    scheduler.advanceTimeBy(MAP_LOCATION_STOP_DELAY_MILLIS)
    scheduler.runCurrent()
    assertFalse(locationUpdates.isRunning)

    // The GPS starts again with "searching", like the real repository: the last position stays,
    // as last known, until a new one comes
    locationUpdates.updates.value = LocationUpdate.Searching
    val seenAgain = mutableListOf<MediaMapLocationState>()
    viewModel.showMap(seenAgain)
    advanceMain()
    locationUpdates.updates.value = LocationUpdate.Position(nearLausanne)
    advanceMain()
    assertEquals(
        listOf(
            MediaMapLocationState.Located(farFromLausanne),
            MediaMapLocationState.Located(farFromLausanne.copy(isLastKnown = true)),
            MediaMapLocationState.Located(nearLausanne, nearbyMarkers = listOf(twoPins[0])),
        ),
        seenAgain,
    )
  }

  @Test
  fun aPositionShownBeforeTheGPSStoppedIsNotPreciseWhenShownAgainNorNearAPin() {
    val scheduler = useStandardDispatcher().scheduler
    val viewModel = createViewModel()
    val firstMap = viewModel.showMap()
    locationUpdates.updates.value = LocationUpdate.Position(nearLausanne)
    advanceMain()
    firstMap.cancel()
    scheduler.advanceTimeBy(MAP_LOCATION_STOP_DELAY_MILLIS)
    scheduler.runCurrent()

    locationUpdates.updates.value = LocationUpdate.Searching
    viewModel.showMap()
    advanceMain()

    val shownAgain = viewModel.location() as MediaMapLocationState.Located
    assertEquals(MediaMapLocationState.Located(nearLausanne.copy(isLastKnown = true)), shownAgain)
    assertFalse(shownAgain.isPrecise)
  }

  @Test
  fun afterTheLocationWasOffOrUnavailableTheMapShownAgainSearchesWithoutAnOldPosition() {
    val viewModel = createViewModel()
    viewModel.showMap()
    locationUpdates.updates.value = LocationUpdate.Position(farFromLausanne)
    locationUpdates.updates.value = LocationUpdate.LocationOff
    locationUpdates.updates.value = LocationUpdate.Searching
    assertEquals(MediaMapLocationState.Searching, viewModel.location())

    locationUpdates.updates.value = LocationUpdate.Position(farFromLausanne)
    locationUpdates.failure.value = IllegalStateException("Google Play services missing")
    assertEquals(MediaMapLocationState.Unavailable, viewModel.location())
    locationUpdates.failure.value = null
    locationUpdates.updates.value = LocationUpdate.Searching
    locationUpdates.permission = false
    viewModel.refreshPermission()
    locationUpdates.permission = true
    viewModel.refreshPermission()
    assertEquals(MediaMapLocationState.Searching, viewModel.location())
  }

  @Test
  fun aPermissionTakenBackWhileTheMapShowsThePositionIsNoPermission() {
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
        MediaMapLocationState.Located(nearLausanne, nearbyMarkers = listOf(twoPins[0])),
        viewModel.location(),
    )
  }

  @Test
  fun aPhoneThatCantGiveItsPositionIsUnavailableAndTheMapStillWorks() {
    locationUpdates.failure.value = IllegalStateException("Google Play services missing")
    val viewModel = createViewModel()
    viewModel.showMap()

    assertEquals(MediaMapLocationState.Unavailable, viewModel.location())
    assertEquals(twoPins, viewModel.content().markers)
  }

  @Test
  fun theGPSRunsOnlyWhileTheMapIsShownAndStopsAWhileAfterItIsClosed() {
    val scheduler = useStandardDispatcher().scheduler
    val viewModel = createViewModel()
    advanceMain()
    assertFalse(locationUpdates.isRunning)

    val map = viewModel.showMap()
    advanceMain()
    assertTrue(locationUpdates.isRunning)

    map.cancel()
    scheduler.advanceTimeBy(MAP_LOCATION_STOP_DELAY_MILLIS - 1)
    scheduler.runCurrent()
    assertTrue(locationUpdates.isRunning) // a screen rotation doesn't restart it

    scheduler.advanceTimeBy(1) // now exactly the delay
    scheduler.runCurrent()
    assertFalse(locationUpdates.isRunning)
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
