// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.map

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.circle.ActiveCircleRepositoryProvider
import com.github.se.amber.data.connectivity.ConnectivityMonitor
import com.github.se.amber.data.connectivity.ConnectivityMonitorProvider
import com.github.se.amber.data.location.LocationUpdate
import com.github.se.amber.data.location.LocationUpdatesRepository
import com.github.se.amber.data.location.LocationUpdatesRepositoryProvider
import com.github.se.amber.data.location.MediaLocationRepository
import com.github.se.amber.data.location.MediaLocationRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.location.DevicePosition
import com.github.se.amber.model.location.MediaMarker
import com.github.se.amber.model.location.NEARBY_EXIT_MARGIN_METERS
import com.github.se.amber.model.location.NEARBY_MAX_ACCURACY_METERS
import com.github.se.amber.model.location.isPreciseWithin
import com.github.se.amber.model.location.nearby
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI state of the memories map, the same for the patient and the caregiver. The errors are string
 * resources, which the screen resolves in the app language.
 */
sealed interface MediaMapUIState {
  data object Loading : MediaMapUIState

  /** Nothing can be shown: [errorMsgRes] replaces the screen, with a Retry button. */
  data class BlockingError(@param:StringRes val errorMsgRes: Int) : MediaMapUIState

  /**
   * No media of the circle has a place on the map. When [isOffline], the media may just not be
   * loaded yet: the screen can ask to connect once.
   */
  data class Empty(val isOffline: Boolean = false) : MediaMapUIState

  /**
   * The pins to show. [selectedMarker] is the pin whose media are open, if any; it follows the
   * media as they change. When [isOffline], the pins are those of the media already loaded.
   */
  data class Content(
      val markers: List<MediaMarker>,
      val selectedMarker: MediaMarker? = null,
      val isOffline: Boolean = false,
      @param:StringRes val errorMsgRes: Int? = null,
  ) : MediaMapUIState
}

/**
 * Where the user is on the memories map, kept apart from [MediaMapUIState] so the GPS runs only
 * while the screen shows the map. The map works the same in every state, only without the dot.
 */
sealed interface MediaMapLocationState {

  /** The user didn't let the app read the location: the screen can explain why and ask. */
  data object NoPermission : MediaMapLocationState

  /** The location is turned off in the phone's settings: the screen can ask to turn it on. */
  data object LocationOff : MediaMapLocationState

  /** Looking for the position. Not an error: without internet, the first one can take a while. */
  data object Searching : MediaMapLocationState

  /**
   * The phone can't give its position (e.g. no Google Play services). It is tried again the next
   * time the map is shown, after being closed for [MAP_LOCATION_STOP_DELAY_MILLIS], or when the
   * permission changes.
   */
  data object Unavailable : MediaMapLocationState

  /**
   * The user is at [position]. [nearbyMarkers] are the pins near it, nearest first (see [nearby]):
   * empty if none is, or if the position isn't [isPrecise]. A pin that became near stays near up to
   * [NEARBY_EXIT_MARGIN_METERS] further, so it doesn't blink. The margin is forgotten once the GPS
   * stops: the user may have moved far by the time the map is shown again.
   *
   * When the map is shown again, the last position stays, as last known, until a new one comes.
   */
  data class Located(
      val position: DevicePosition,
      val nearbyMarkers: List<MediaMarker> = emptyList(),
  ) : MediaMapLocationState {

    /**
     * Whether the position is known well enough to say which pins are near: false with the
     * approximate location (the screen can ask for the precise one), and for the last known
     * position shown while waiting for a new one.
     */
    val isPrecise: Boolean
      get() = !position.isLastKnown && position.isPreciseWithin(NEARBY_MAX_ACCURACY_METERS)
  }
}

/**
 * How long the GPS keeps running after the map stops being shown: long enough for a screen
 * rotation, short enough not to run in the background.
 */
internal const val MAP_LOCATION_STOP_DELAY_MILLIS = 5_000L

/**
 * Loads the pins of the signed-in user's active care circle, patient or caregiver, and keeps them
 * live. Switching the active circle reloads the pins of the new one. While the map is shown, it
 * also follows the user's live position and finds the pin near them ([locationState]).
 *
 * @param mediaLocationRepository gives the pins of a circle.
 * @param profileRepository gives the signed-in user's profile and circles.
 * @param activeCircleRepository picks which of their circles to show.
 * @param connectivityMonitor tells whether the phone is offline, so the screen can say so.
 * @param locationUpdatesRepository gives the user's live position, and whether they allowed it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaMapViewModel(
    private val mediaLocationRepository: MediaLocationRepository =
        MediaLocationRepositoryProvider.default.repository,
    private val profileRepository: ProfileRepository = ProfileRepositoryProvider.default.repository,
    private val activeCircleRepository: ActiveCircleRepository =
        ActiveCircleRepositoryProvider.default.repository,
    connectivityMonitor: ConnectivityMonitor = ConnectivityMonitorProvider.monitor,
    private val locationUpdatesRepository: LocationUpdatesRepository =
        LocationUpdatesRepositoryProvider.default.repository,
) : ViewModel() {

  /** The pins, or why there are none yet; the open pin and the offline flag are kept apart. */
  private sealed interface Loaded {
    data object Loading : Loaded

    data class Failed(@param:StringRes val errorMsgRes: Int) : Loaded

    data class Markers(val markers: List<MediaMarker>) : Loaded
  }

  private val loaded = MutableStateFlow<Loaded>(Loaded.Loading)
  // The media of the open pin, not its id: the id changes when the pin's oldest media is removed
  // or an older one is added, and the pin must stay open then
  private val selectedMediaIds = MutableStateFlow<Set<String>>(emptySet())
  private val errorMsgRes = MutableStateFlow<Int?>(null)

  val uiState: StateFlow<MediaMapUIState> =
      combine(loaded, selectedMediaIds, errorMsgRes, connectivityMonitor.isOnline) {
              loaded,
              selectedIds,
              errorMsgRes,
              isOnline ->
            when (loaded) {
              Loaded.Loading -> MediaMapUIState.Loading
              is Loaded.Failed -> MediaMapUIState.BlockingError(loaded.errorMsgRes)
              is Loaded.Markers ->
                  if (loaded.markers.isEmpty()) {
                    MediaMapUIState.Empty(isOffline = !isOnline)
                  } else {
                    MediaMapUIState.Content(
                        markers = loaded.markers,
                        selectedMarker = loaded.markers.openPin(selectedIds),
                        isOffline = !isOnline,
                        errorMsgRes = errorMsgRes,
                    )
                  }
            }
          }
          .stateIn(viewModelScope, SharingStarted.Eagerly, MediaMapUIState.Loading)

  private val hasLocationPermission = MutableStateFlow(locationUpdatesRepository.hasPermission())

  // The real first state, so the screen doesn't show "searching" before the location is off
  private val initialLocationState =
      when {
        !hasLocationPermission.value -> MediaMapLocationState.NoPermission
        !locationUpdatesRepository.isLocationEnabled() -> MediaMapLocationState.LocationOff
        else -> MediaMapLocationState.Searching
      }

  /**
   * The user's position and the pin near them. The GPS runs only while this is collected, and stops
   * [MAP_LOCATION_STOP_DELAY_MILLIS] after the last collector leaves: collect it with
   * `collectAsStateWithLifecycle()`, so it stops when the app goes to the background.
   */
  val locationState: StateFlow<MediaMapLocationState> =
      hasLocationPermission
          .flatMapLatest { granted ->
            if (granted) positions() else flowOf(MediaMapLocationState.NoPermission)
          }
          .combine(shownMarkers()) { location, markers -> location to markers }
          // The pins near before are kept up to the exit margin, so they don't blink. The fold
          // starts from null, which isn't emitted: the first state is the repository's
          .runningFold<_, MediaMapLocationState?>(null) { previous, (location, markers) ->
            if (location !is MediaMapLocationState.Located || !location.isPrecise) {
              return@runningFold location
            }
            val nearBefore = (previous as? MediaMapLocationState.Located)?.nearbyMarkers.orEmpty()
            location.copy(
                nearbyMarkers = markers.nearby(location.position, nearBefore.map { it.id }.toSet())
            )
          }
          .filterNotNull()
          .distinctUntilChanged()
          .stateIn(
              viewModelScope,
              SharingStarted.WhileSubscribed(MAP_LOCATION_STOP_DELAY_MILLIS),
              initialLocationState,
          )

  private var loadJob: Job? = null

  init {
    load()
  }

  /** Reloads everything and shows the loading state. */
  fun retry() = load()

  /** Opens the media of the pin [markerId]. Does nothing if no pin has this id. */
  fun selectMarker(markerId: String) {
    val markers = (loaded.value as? Loaded.Markers)?.markers ?: return
    markers.find { it.id == markerId }?.let { selectedMediaIds.value = it.mediaIds() }
  }

  /** Closes the media of the selected pin. */
  fun dismissMarker() {
    selectedMediaIds.value = emptySet()
  }

  /**
   * Reads again whether the user allows the location. The screen calls it once the permission
   * dialog closes, and when it comes back to the foreground, in case it changed in the settings.
   */
  fun refreshPermission() {
    hasLocationPermission.value = locationUpdatesRepository.hasPermission()
  }

  /** Clears the non-blocking error message. */
  fun clearErrorMsg() {
    errorMsgRes.value = null
  }

  /** Loads the profile, then follows the active circle and its pins. */
  private fun load() {
    loadJob?.cancel()
    loaded.value = Loaded.Loading
    selectedMediaIds.value = emptySet()
    errorMsgRes.value = null

    loadJob = viewModelScope.launch {
      val profile =
          profileRepository.getCurrentProfile().getOrElse { e ->
            Log.e(TAG, "Error fetching profile", e)
            loaded.value = Loaded.Failed(R.string.mediaMapErrorLoadProfile)
            return@launch
          }
              ?: run {
                loaded.value = Loaded.Failed(R.string.mediaMapErrorNoProfile)
                return@launch
              }

      activeCircleRepository
          .activeCircleId(profile.uid, profile.circleIds)
          .distinctUntilChanged()
          .flatMapLatest { circleId ->
            if (circleId == null) flowOf(Loaded.Failed(R.string.mediaMapErrorNoCircle))
            else markersOf(circleId)
          }
          .collect { next ->
            val open = (next as? Loaded.Markers)?.markers?.openPin(selectedMediaIds.value)
            loaded.value = next
            // Keep the open pin's current media, so it stays open while they change one at a time.
            // Forget it once it is gone (another circle, or all its media removed), so it doesn't
            // open again by itself if its media come back
            selectedMediaIds.value = open?.mediaIds().orEmpty()
          }
    }
  }

  /**
   * The pins of [circleId], starting with [Loaded.Loading]. If they stop with an error, the pins
   * already shown stay with a message; with none shown yet, the error replaces the screen.
   */
  private fun markersOf(circleId: String): Flow<Loaded> {
    // Whether this circle's pins were shown; loaded may still hold the previous circle's
    var shown = false
    return mediaLocationRepository
        .observeMarkers(circleId)
        .map<List<MediaMarker>, Loaded> { Loaded.Markers(it) }
        .onEach { shown = true }
        .onStart { emit(Loaded.Loading) }
        .catch { e ->
          Log.e(TAG, "Error observing the pins", e)
          if (shown) {
            errorMsgRes.value = R.string.mediaMapErrorLoadMedia
          } else {
            emit(Loaded.Failed(R.string.mediaMapErrorLoadMedia))
          }
        }
  }

  /**
   * The pins the position is compared with. While they reload, the last ones are kept, so a pin
   * near you doesn't lose its exit margin on a reload of the same pins.
   */
  private fun shownMarkers(): Flow<List<MediaMarker>> =
      loaded.scan(emptyList()) { previous, next ->
        when (next) {
          Loaded.Loading -> previous
          is Loaded.Failed -> emptyList()
          is Loaded.Markers -> next.markers
        }
      }

  /**
   * The live position; the repository first says whether the location is on. When the GPS starts
   * again, the position shown before stays as last known while searching, so it isn't said precise
   * nor near a pin until a new one comes.
   */
  private fun positions(): Flow<MediaMapLocationState> =
      locationUpdatesRepository
          .observeLocation()
          .map { update ->
            when (update) {
              LocationUpdate.LocationOff -> MediaMapLocationState.LocationOff
              LocationUpdate.Searching ->
                  (locationState.value as? MediaMapLocationState.Located)?.let {
                    MediaMapLocationState.Located(it.position.copy(isLastKnown = true))
                  } ?: MediaMapLocationState.Searching
              is LocationUpdate.Position -> MediaMapLocationState.Located(update.position)
            }
          }
          .catch { e ->
            if (e is SecurityException) {
              // Missing when the GPS started: the flow above switches to NoPermission
              hasLocationPermission.value = false
            } else {
              Log.e(TAG, "Error reading the location", e)
              emit(MediaMapLocationState.Unavailable)
            }
          }

  /** The pin that has one of [mediaIds], the media of the open pin; null if none has. */
  private fun List<MediaMarker>.openPin(mediaIds: Set<String>): MediaMarker? = find { pin ->
    pin.media.any { it.id in mediaIds }
  }

  private fun MediaMarker.mediaIds(): Set<String> = media.mapTo(mutableSetOf()) { it.id }

  private companion object {
    const val TAG = "MediaMapViewModel"
  }
}
