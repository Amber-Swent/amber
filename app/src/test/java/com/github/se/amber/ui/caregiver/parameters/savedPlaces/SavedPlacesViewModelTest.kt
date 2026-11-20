// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.parameters.savedPlaces

import com.github.se.amber.R
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.circle.CareCircleError
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.location.FakeLocationUpdatesRepository
import com.github.se.amber.data.location.LocationUpdate
import com.github.se.amber.data.location.PlaceSearchRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.location.Place
import com.github.se.amber.model.location.TestPlaces.GENEVA
import com.github.se.amber.model.location.TestPlaces.LAUSANNE
import com.github.se.amber.model.location.TestPositions.APPROXIMATE_ACCURACY_METERS
import com.github.se.amber.model.location.TestPositions.GPS_ACCURACY_METERS
import com.github.se.amber.model.location.TestPositions.LAUSANNE_FLON
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedPlacesViewModelTest {

  /** Keeps the places of each circle in memory. [failWith] makes every write fail with it. */
  private class FakePlacesRepository : CareCircleRepository {
    val places = MutableStateFlow<Map<String, List<Place>>>(emptyMap())
    val feedFailure = MutableStateFlow<Throwable?>(null)
    var failWith: CareCircleError? = null
    private var lastId = 0

    override fun newId() = "id${++lastId}"

    override suspend fun getCircle(circleId: String) = Result.success(CareCircle(id = circleId))

    override suspend fun addPerson(circleId: String, person: Person) = Result.success(person)

    override fun observePlaces(circleId: String): Flow<List<Place>> =
        merge(
            places.map { it[circleId].orEmpty() },
            feedFailure.filterNotNull().map<Throwable, List<Place>> { throw it },
        )

    override suspend fun addPlace(circleId: String, place: Place) =
        save(circleId, place.copy(id = newId()))

    override suspend fun updatePlace(circleId: String, place: Place) = save(circleId, place)

    override suspend fun deletePlace(circleId: String, placeId: String): Result<Unit> {
      failWith?.let {
        return Result.failure(it)
      }
      edit(circleId) { list -> list.filterNot { it.id == placeId } }
      return Result.success(Unit)
    }

    private fun save(circleId: String, place: Place): Result<Place> {
      failWith?.let {
        return Result.failure(it)
      }
      edit(circleId) { list -> list.filterNot { it.id == place.id } + place }
      return Result.success(place)
    }

    private fun edit(circleId: String, change: (List<Place>) -> List<Place>) {
      places.value = places.value + (circleId to change(places.value[circleId].orEmpty()))
    }
  }

  /** The active circle, which the test switches through [activeId]. */
  private class FakeActiveCircleRepository(initial: String?) : ActiveCircleRepository {
    val activeId = MutableSharedFlow<String?>(replay = 1).apply { tryEmit(initial) }

    override fun activeCircleId(uid: String, circleIds: List<String>): Flow<String?> = activeId

    override suspend fun setActiveCircle(uid: String, id: String) = activeId.emit(id)

    override suspend fun clear(uid: String) = activeId.emit(null)
  }

  /** An address search that answers each query when the test completes [answers] for it. */
  private class FakePlaceSearch : PlaceSearchRepository {
    val answers = mutableMapOf<String, CompletableDeferred<Result<List<GeoLocation>>>>()

    override suspend fun search(query: String) =
        answers.getOrPut(query) { CompletableDeferred() }.await()
  }

  private val dispatcher = UnconfinedTestDispatcher()
  private val circles = FakePlacesRepository()
  private val profiles = mockk<ProfileRepository>()
  private val activeCircle = FakeActiveCircleRepository("circle1")
  private val search = FakePlaceSearch()
  private val location = FakeLocationUpdatesRepository(permission = true)

  private val caregiver =
      UserProfile(uid = "care", role = Role.CAREGIVER, circleIds = listOf("circle1", "circle2"))
  private val pharmacy = Place(id = "pharmacy", name = "pharmacy", location = GENEVA)
  private val home = Place(id = "home", name = "Home", location = LAUSANNE)
  private val preciseHere = LAUSANNE_FLON.copy(accuracyMeters = GPS_ACCURACY_METERS)

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    coEvery { profiles.getCurrentProfile() } returns Result.success(caregiver)
    circles.places.value = mapOf("circle1" to listOf(pharmacy, home))
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel() =
      SavedPlacesViewModel(circles, profiles, activeCircle, search, location)

  private val SavedPlacesViewModel.content
    get() = uiState.value as SavedPlacesUIState.Content

  @Test
  fun showsActiveCirclePlacesByNameAndFollowsCircleSwitch() =
      runTest(dispatcher) {
        val viewModel = createViewModel()
        assertEquals(listOf(home, pharmacy), viewModel.content.places)

        activeCircle.activeId.emit("circle2")
        assertEquals(emptyList<Place>(), viewModel.content.places)

        // Writes go to the circle shown now
        viewModel.addPlace("Café", GENEVA)
        assertEquals(listOf("Café"), circles.places.value["circle2"].orEmpty().map { it.name })
      }

  @Test
  fun missingProfileOrCircleReplacesTheScreen() =
      runTest(dispatcher) {
        coEvery { profiles.getCurrentProfile() } returns Result.failure(IllegalStateException())
        assertEquals(
            SavedPlacesUIState.BlockingError(R.string.savedPlacesErrorLoadProfile),
            createViewModel().uiState.value,
        )

        coEvery { profiles.getCurrentProfile() } returns Result.success(null)
        assertEquals(
            SavedPlacesUIState.BlockingError(R.string.savedPlacesErrorNoProfile),
            createViewModel().uiState.value,
        )

        coEvery { profiles.getCurrentProfile() } returns Result.success(caregiver)
        activeCircle.activeId.emit(null)
        assertEquals(
            SavedPlacesUIState.BlockingError(R.string.savedPlacesErrorNoCircle),
            createViewModel().uiState.value,
        )
      }

  @Test
  fun feedFailingBeforeAnyPlacesReplacesTheScreenUntilRetry() =
      runTest(dispatcher) {
        circles.feedFailure.value = CareCircleError.PermissionDenied
        val viewModel = createViewModel()
        assertEquals(
            SavedPlacesUIState.BlockingError(R.string.savedPlacesErrorLoad),
            viewModel.uiState.value,
        )

        circles.feedFailure.value = null
        viewModel.retry()
        assertEquals(listOf(home, pharmacy), viewModel.content.places)
      }

  @Test
  fun feedFailingAfterPlacesKeepsThemAndSaysSoAgainAfterRetry() =
      runTest(dispatcher) {
        val viewModel = createViewModel()

        // Twice, so a failure after a retry still reaches the user
        repeat(2) {
          circles.feedFailure.value = CareCircleError.Network
          assertEquals(listOf(home, pharmacy), viewModel.content.places)
          assertEquals(R.string.savedPlacesErrorLoad, viewModel.content.errorMsgRes)
          viewModel.clearErrorMsg()
          circles.feedFailure.value = null
          viewModel.retry()
          assertEquals(listOf(home, pharmacy), viewModel.content.places)
        }
      }

  @Test
  fun addRenameAndDeleteShowInTheList() =
      runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.addPlace("Café", GENEVA)
        val cafe = viewModel.content.places.first { it.name == "Café" }
        assertEquals(GENEVA, cafe.location)

        viewModel.renamePlace(home, "My home")
        assertEquals(LAUSANNE, viewModel.content.places.first { it.id == home.id }.location)

        viewModel.deletePlace(pharmacy.id)
        assertEquals(listOf("Café", "My home"), viewModel.content.places.map { it.name })
      }

  @Test
  fun failedWritesChangeNothingAndSaySoEveryTime() =
      runTest(dispatcher) {
        val viewModel = createViewModel()
        circles.failWith = CareCircleError.InvalidPlace

        viewModel.addPlace(" ", GENEVA)
        assertEquals(R.string.savedPlacesErrorSave, viewModel.content.errorMsgRes)
        viewModel.clearErrorMsg()
        viewModel.renamePlace(home, " ")
        assertEquals(R.string.savedPlacesErrorSave, viewModel.content.errorMsgRes)
        viewModel.deletePlace(home.id)
        assertEquals(R.string.savedPlacesErrorDelete, viewModel.content.errorMsgRes)
        assertEquals(listOf(home, pharmacy), viewModel.content.places)
      }

  @Test
  fun addressSearchClearsItsBusyFlagOnEveryEnding() =
      runTest(dispatcher) {
        val viewModel = createViewModel()

        // Found
        viewModel.searchAddress("Lausanne")
        assertTrue(viewModel.content.isSearching)
        search.answers.getValue("Lausanne").complete(Result.success(listOf(LAUSANNE)))
        assertFalse(viewModel.content.isSearching)
        assertEquals(listOf(LAUSANNE), viewModel.content.searchResults)

        // Failed: the old results go, and the user is told
        viewModel.searchAddress("Geneva")
        search.answers.getValue("Geneva").complete(Result.failure(IllegalStateException()))
        assertFalse(viewModel.content.isSearching)
        assertEquals(emptyList<GeoLocation>(), viewModel.content.searchResults)
        assertEquals(R.string.savedPlacesErrorSearch, viewModel.content.errorMsgRes)

        // Replaced by a newer search: only the newer answer counts
        viewModel.searchAddress("Lau")
        viewModel.searchAddress("Lausanne Flon")
        search.answers.getValue("Lau").complete(Result.success(listOf(GENEVA)))
        assertTrue(viewModel.content.isSearching)
        search.answers.getValue("Lausanne Flon").complete(Result.success(listOf(LAUSANNE)))
        assertEquals(listOf(LAUSANNE), viewModel.content.searchResults)

        // Blank, and form closed while searching
        viewModel.searchAddress(" ")
        assertEquals(emptyList<GeoLocation>(), viewModel.content.searchResults)
        viewModel.searchAddress("Bern")
        viewModel.closeForm()
        assertFalse(viewModel.content.isSearching)
      }

  @Test
  fun hereFindsTheFirstPrecisePositionAndStopsTheGps() =
      runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.locateHere()
        assertEquals(HereState.Locating, viewModel.content.here)
        // Not precise enough to walk someone to, nor a position from before
        location.updates.value =
            LocationUpdate.Position(
                LAUSANNE_FLON.copy(accuracyMeters = APPROXIMATE_ACCURACY_METERS)
            )
        location.updates.value = LocationUpdate.Position(preciseHere.copy(isLastKnown = true))
        assertEquals(HereState.Locating, viewModel.content.here)

        location.updates.value = LocationUpdate.Position(preciseHere)
        assertEquals(
            HereState.Found(GeoLocation(lat = preciseHere.lat, lng = preciseHere.lng)),
            viewModel.content.here,
        )
        assertFalse(location.isRunning)
      }

  @Test
  fun hereLeavesLocatingOnEveryOtherEnding() =
      runTest(dispatcher) {
        val viewModel = createViewModel()

        // No permission: asked before starting the GPS
        location.permission = false
        viewModel.locateHere()
        assertEquals(HereState.NoPermission, viewModel.content.here)
        location.permission = true

        // Location off
        location.updates.value = LocationUpdate.LocationOff
        viewModel.locateHere()
        assertEquals(HereState.LocationOff, viewModel.content.here)
        location.updates.value = LocationUpdate.Searching

        // Permission taken back as the GPS starts
        location.failure.value = SecurityException()
        viewModel.locateHere()
        assertEquals(HereState.NoPermission, viewModel.content.here)

        // No position service at all
        location.failure.value = IllegalStateException("no Play services")
        viewModel.locateHere()
        assertEquals(HereState.NotFound, viewModel.content.here)
        location.failure.value = null

        // Nothing precise in time
        viewModel.locateHere()
        advanceTimeBy(HERE_TIMEOUT_MILLIS + 1)
        assertEquals(HereState.NotFound, viewModel.content.here)
        assertFalse(location.isRunning)

        // Form closed while locating: the GPS stops and nothing comes back later
        viewModel.locateHere()
        viewModel.closeForm()
        assertEquals(HereState.Idle, viewModel.content.here)
        assertFalse(location.isRunning)
        advanceTimeBy(HERE_TIMEOUT_MILLIS + 1)
        assertEquals(HereState.Idle, viewModel.content.here)
      }
}
