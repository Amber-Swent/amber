// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.parameters.savedPlaces

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.circle.ActiveCircleRepositoryProvider
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.location.LocationUpdate
import com.github.se.amber.data.location.LocationUpdatesRepository
import com.github.se.amber.data.location.LocationUpdatesRepositoryProvider
import com.github.se.amber.data.location.PlaceSearchRepository
import com.github.se.amber.data.location.PlaceSearchRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.location.Place
import com.github.se.amber.model.location.isPreciseWithin
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * UI state of the caregiver's Saved places screen. The errors are string resources, which the
 * screen resolves in the app language.
 */
sealed interface SavedPlacesUIState {
  data object Loading : SavedPlacesUIState

  /** Nothing can be shown: [errorMsgRes] replaces the screen, with a Retry button. */
  data class BlockingError(@param:StringRes val errorMsgRes: Int) : SavedPlacesUIState

  /**
   * The saved [places], by name. While a place is being added: [searchResults] of the address
   * search, [isSearching] while it runs, and [here] for "I'm here now". [errorMsgRes] is a message
   * to show once, then clear with [SavedPlacesViewModel.clearErrorMsg].
   */
  data class Content(
      val places: List<Place>,
      val searchResults: List<GeoLocation> = emptyList(),
      val isSearching: Boolean = false,
      val here: HereState = HereState.Idle,
      @param:StringRes val errorMsgRes: Int? = null,
  ) : SavedPlacesUIState
}

/** Where "I'm here now" is at. Every way it can end leaves [Locating]. */
sealed interface HereState {
  data object Idle : HereState

  /** Waiting for a precise position, at most [HERE_TIMEOUT_MILLIS]. */
  data object Locating : HereState

  /** The caregiver is at [location]: the screen can save it with a name. */
  data class Found(val location: GeoLocation) : HereState

  /** The user didn't let the app read the location: the screen can ask, then try again. */
  data object NoPermission : HereState

  /** The location is turned off in the phone's settings. */
  data object LocationOff : HereState

  /**
   * No position precise to [HERE_MAX_ACCURACY_METERS] came in time, for example indoors or with
   * only the approximate location allowed. The screen can suggest the address search instead.
   */
  data object NotFound : HereState
}

/** How precise "I'm here now" must be: the patient is walked to this point. */
internal const val HERE_MAX_ACCURACY_METERS = 50.0

/** How long "I'm here now" waits for a precise position. */
internal const val HERE_TIMEOUT_MILLIS = 30_000L

/**
 * Lets a caregiver keep the saved places of their active care circle: the live list, adding a place
 * found by address or where they stand, renaming and deleting one. Switching the active circle
 * shows the places of the new one.
 *
 * Writes don't wait for the server (see [CareCircleRepository.addPlace]), so there is no saving
 * state: a place shows up in the list at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedPlacesViewModel(
    private val circleRepository: CareCircleRepository =
        CareCircleRepositoryProvider.default.repository,
    private val profileRepository: ProfileRepository = ProfileRepositoryProvider.default.repository,
    private val activeCircleRepository: ActiveCircleRepository =
        ActiveCircleRepositoryProvider.default.repository,
    private val placeSearchRepository: PlaceSearchRepository =
        PlaceSearchRepositoryProvider.default.repository,
    private val locationUpdatesRepository: LocationUpdatesRepository =
        LocationUpdatesRepositoryProvider.default.repository,
) : ViewModel() {

  /** The places, or why there are none yet. */
  private sealed interface Loaded {
    data object Loading : Loaded

    data class Failed(@param:StringRes val errorMsgRes: Int) : Loaded

    data class Places(val circleId: String, val places: List<Place>) : Loaded
  }

  private data class Form(
      val searchResults: List<GeoLocation> = emptyList(),
      val isSearching: Boolean = false,
      val here: HereState = HereState.Idle,
  )

  private val loaded = MutableStateFlow<Loaded>(Loaded.Loading)
  private val form = MutableStateFlow(Form())
  private val errorMsgRes = MutableStateFlow<Int?>(null)

  val uiState: StateFlow<SavedPlacesUIState> =
      combine(loaded, form, errorMsgRes) { loaded, form, errorMsgRes ->
            when (loaded) {
              Loaded.Loading -> SavedPlacesUIState.Loading
              is Loaded.Failed -> SavedPlacesUIState.BlockingError(loaded.errorMsgRes)
              is Loaded.Places ->
                  SavedPlacesUIState.Content(
                      places = loaded.places,
                      searchResults = form.searchResults,
                      isSearching = form.isSearching,
                      here = form.here,
                      errorMsgRes = errorMsgRes,
                  )
            }
          }
          .stateIn(viewModelScope, SharingStarted.Eagerly, SavedPlacesUIState.Loading)

  private var loadJob: Job? = null
  private var searchJob: Job? = null
  private var hereJob: Job? = null

  init {
    load()
  }

  /** Loads everything again and shows the loading state. */
  fun retry() = load()

  /** Clears the message shown once. */
  fun clearErrorMsg() {
    errorMsgRes.value = null
  }

  /**
   * Looks up [query] as an address. A new search replaces the one running. A blank query just
   * clears the results.
   */
  fun searchAddress(query: String) {
    searchJob?.cancel()
    if (query.isBlank()) {
      form.value = form.value.copy(searchResults = emptyList(), isSearching = false)
      return
    }
    form.value = form.value.copy(isSearching = true)
    // Cancelled by a new search or by closeForm, which set the flag themselves
    searchJob = viewModelScope.launch {
      val result = placeSearchRepository.search(query)
      form.value =
          form.value.copy(searchResults = result.getOrDefault(emptyList()), isSearching = false)
      result.onFailure { e ->
        Log.e(TAG, "Error searching an address", e)
        errorMsgRes.value = R.string.savedPlacesErrorSearch
      }
    }
  }

  /**
   * Finds where the caregiver stands, for "I'm here now". Ends in [HereState.Found], or says why
   * not. Without the permission, it ends at once in [HereState.NoPermission]: call it again once
   * the user allowed it.
   */
  fun locateHere() {
    hereJob?.cancel()
    if (!locationUpdatesRepository.hasPermission()) {
      setHere(HereState.NoPermission)
      return
    }
    setHere(HereState.Locating)
    // Cancelled by a new call or by closeForm, which set the state themselves
    hereJob = viewModelScope.launch {
      val result =
          try {
            withTimeoutOrNull(HERE_TIMEOUT_MILLIS) { firstPreciseUpdate() } ?: HereState.NotFound
          } catch (e: CancellationException) {
            throw e
          } catch (_: SecurityException) {
            HereState.NoPermission
          } catch (e: Exception) {
            // No position at all, e.g. without Google Play services
            Log.e(TAG, "Error reading the location", e)
            HereState.NotFound
          }
      setHere(result)
    }
  }

  /** Forgets the address search and "I'm here now", when the add form closes. */
  fun closeForm() {
    searchJob?.cancel()
    hereJob?.cancel()
    form.value = Form()
  }

  /** Saves a new place named [name] at [location], found by the search or by "I'm here now". */
  fun addPlace(name: String, location: GeoLocation) =
      write(R.string.savedPlacesErrorSave) { circleId ->
        circleRepository.addPlace(circleId, Place(name = name, location = location))
      }

  /** Gives [place] the new name [name]. Its location stays. */
  fun renamePlace(place: Place, name: String) =
      write(R.string.savedPlacesErrorSave) { circleId ->
        circleRepository.updatePlace(circleId, place.copy(name = name))
      }

  /** Deletes the place [placeId]. The screen asks "Are you sure?" before calling it. */
  fun deletePlace(placeId: String) =
      write(R.string.savedPlacesErrorDelete) { circleId ->
        circleRepository.deletePlace(circleId, placeId)
      }

  private fun write(@StringRes failureMsgRes: Int, save: suspend (String) -> Result<*>) {
    val circleId = (loaded.value as? Loaded.Places)?.circleId ?: return
    viewModelScope.launch {
      save(circleId).onFailure { e ->
        Log.e(TAG, "Error saving a place", e)
        errorMsgRes.value = failureMsgRes
      }
    }
  }

  private fun setHere(here: HereState) {
    form.value = form.value.copy(here = here)
  }

  /** The first position precise enough, or why there is none. Never a last known position. */
  private suspend fun firstPreciseUpdate(): HereState =
      locationUpdatesRepository
          .observeLocation()
          .map { update ->
            when (update) {
              LocationUpdate.LocationOff -> HereState.LocationOff
              LocationUpdate.Searching -> null
              is LocationUpdate.Position ->
                  update.position
                      .takeIf { !it.isLastKnown && it.isPreciseWithin(HERE_MAX_ACCURACY_METERS) }
                      ?.let { HereState.Found(GeoLocation(lat = it.lat, lng = it.lng)) }
            }
          }
          .first { it != null } ?: HereState.NotFound

  /** Loads the profile, then follows the active circle and its places. */
  private fun load() {
    loadJob?.cancel()
    loaded.value = Loaded.Loading
    errorMsgRes.value = null
    loadJob = viewModelScope.launch {
      val profile =
          profileRepository.getCurrentProfile().getOrElse { e ->
            Log.e(TAG, "Error fetching profile", e)
            loaded.value = Loaded.Failed(R.string.savedPlacesErrorLoadProfile)
            return@launch
          }
              ?: run {
                loaded.value = Loaded.Failed(R.string.savedPlacesErrorNoProfile)
                return@launch
              }

      activeCircleRepository
          .activeCircleId(profile.uid, profile.circleIds)
          .distinctUntilChanged()
          .flatMapLatest { circleId ->
            if (circleId == null) flowOf(Loaded.Failed(R.string.savedPlacesErrorNoCircle))
            else placesOf(circleId)
          }
          .collect { loaded.value = it }
    }
  }

  /**
   * The places of [circleId] by name, starting with [Loaded.Loading]. If they stop with an error,
   * the places already shown stay with a message; with none shown yet, the error replaces the
   * screen.
   */
  private fun placesOf(circleId: String): Flow<Loaded> {
    var shown = false
    return circleRepository
        .observePlaces(circleId)
        .map<List<Place>, Loaded> { places ->
          shown = true
          Loaded.Places(circleId, places.sortedBy { it.name.lowercase() })
        }
        .onStart { emit(Loaded.Loading) }
        .catch { e ->
          Log.e(TAG, "Error observing the places", e)
          if (shown) errorMsgRes.value = R.string.savedPlacesErrorLoad
          else emit(Loaded.Failed(R.string.savedPlacesErrorLoad))
        }
  }

  private companion object {
    const val TAG = "SavedPlacesViewModel"
  }
}
