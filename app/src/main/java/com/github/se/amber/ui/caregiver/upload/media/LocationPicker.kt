package com.github.se.amber.ui.caregiver.upload.media

import com.github.se.amber.data.location.PlaceSearchRepository
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.location.Place
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

/**
 * Represents where the picture was taken: the place the user searches for, and the place picked.
 *
 * @param query What the user typed to search for where the picture was taken.
 * @param suggestions Real places matching [query], to pick from.
 * @param isSearching Whether a search for [query] is waiting or running, so the screen can tell
 *   "searching" from "no places found".
 * @param searchFailed Whether the last search for [query] failed, so the screen can tell "the
 *   search failed" from "no places found" and offer to retry.
 * @param pickedPlace The real place picked from [suggestions] or the circle's saved places; null if
 *   none was picked.
 * @param selectedPlaceId The id of the saved place picked as [pickedPlace]; null if it wasn't one
 *   of the circle's saved places.
 */
data class LocationUIState(
    val query: String = "",
    val suggestions: List<GeoLocation> = emptyList(),
    val isSearching: Boolean = false,
    val searchFailed: Boolean = false,
    val pickedPlace: GeoLocation? = null,
    val selectedPlaceId: String? = null,
) {
  /** A typed place must be picked from the suggestions: only real places are saved. */
  val hasValidPlace: Boolean
    get() = query.isBlank() || pickedPlace != null

  /** Whether [query] is long enough to be searched; shorter ones match too many places. */
  val isLongEnoughToSearch: Boolean
    get() = query.trim().length >= MIN_PLACE_QUERY_LENGTH
}

/** The fewest characters a place search needs before it is sent. */
const val MIN_PLACE_QUERY_LENGTH = 3

/**
 * Owns everything about choosing "where it happened": the text typed by the user, the debounced
 * search of real places, and the picked place (searched or saved in the care circle).
 *
 * It knows nothing about the care circle or the upload: the saved places are given by the caller.
 *
 * @param placeRepo Where places are searched (goes over the network in production).
 * @param scope Scope running the search; the search stops when it is canceled.
 * @param debounce How long the user must stop typing before the search is sent.
 */
class LocationPicker(
    private val placeRepo: PlaceSearchRepository,
    scope: CoroutineScope,
    private val debounce: Duration = DEFAULT_DEBOUNCE,
) {
  private val _state = MutableStateFlow(LocationUIState())
  val state: StateFlow<LocationUIState> = _state.asStateFlow()

  /**
   * What the user types, searched once they stop typing. Not a StateFlow: a retry sends the same
   * text again, and a StateFlow would drop it as unchanged.
   */
  private val queries =
      MutableSharedFlow<String>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    searchAsTyped(scope)
  }

  /**
   * Updates the typed text; the search is sent once the user stops typing. Any place picked before
   * is dropped: the text no longer names it.
   */
  fun setQuery(query: String) {
    // Nothing changed: no new search will come, so it mustn't look like one is running
    if (query == _state.value.query) return
    _state.update { old ->
      val location = LocationUIState(query = query, suggestions = old.suggestions)
      val willSearch = location.isLongEnoughToSearch
      location.copy(
          isSearching = willSearch,
          suggestions = if (willSearch) location.suggestions else emptyList(),
      )
    }
    queries.tryEmit(query)
  }

  /** Searches the same text again after a failed search (e.g. once the network is back). */
  fun retrySearch() {
    val current = _state.value
    if (!current.searchFailed) return
    _state.update { it.copy(isSearching = true, searchFailed = false) }
    queries.tryEmit(current.query)
  }

  /**
   * Picks a place found by the search, or a saved one when [savedPlaceId] is given. The state
   * changes in one step, so no half-picked state is ever visible.
   */
  fun selectPlace(place: GeoLocation, savedPlaceId: String? = null) {
    if (place.lat == null || place.lng == null) return // not a point on the map
    _state.value =
        LocationUIState(
            query = place.placeName.orEmpty(),
            pickedPlace = place,
            selectedPlaceId = savedPlaceId,
        )
  }

  /**
   * Picks one of the circle's saved places, or drops it if it was already picked.
   *
   * @param savedPlaces The circle's pickable places, keyed by place id.
   */
  fun selectSavedPlace(placeId: String, savedPlaces: Map<String, Place>) {
    if (_state.value.selectedPlaceId == placeId) {
      clear()
      return
    }
    val place = savedPlaces[placeId] ?: return
    selectPlace(place.location.copy(placeName = place.name), savedPlaceId = placeId)
  }

  /** Forgets the text, the suggestions and the picked place. */
  fun clear() {
    _state.value = LocationUIState()
  }

  /**
   * A newer query cancels the search still running for an older one, and a result is only kept if
   * the field still shows the query it was searched for.
   */
  @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
  private fun searchAsTyped(scope: CoroutineScope) {
    queries
        .debounce(debounce)
        .filter { LocationUIState(query = it).isLongEnoughToSearch }
        .mapLatest { query -> query to placeRepo.search(query) }
        .onEach { (query, result) -> showResult(query, result) }
        .launchIn(scope)
  }

  private fun showResult(query: String, result: Result<List<GeoLocation>>) {
    val current = _state.value
    if (current.query != query || current.pickedPlace != null) return
    result
        .onSuccess { places ->
          _state.update { it.copy(suggestions = places, isSearching = false) }
        }
        .onFailure {
          _state.update {
            it.copy(suggestions = emptyList(), isSearching = false, searchFailed = true)
          }
        }
  }

  private companion object {
    /** How long the user must stop typing before their place search is sent. */
    val DEFAULT_DEBOUNCE = 300.milliseconds
  }
}
