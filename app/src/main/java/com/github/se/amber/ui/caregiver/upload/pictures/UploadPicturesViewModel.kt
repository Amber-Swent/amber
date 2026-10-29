/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 * Modified with assistance from OpenAI Codex.
 * Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload.picture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.location.PlaceSearchRepository
import com.github.se.amber.data.location.PlaceSearchRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.circle.displayNameOf
import com.github.se.amber.model.circle.taggableIds
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.location.Place
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MediaRepositoryProvider
import com.github.se.amber.model.media.MediaStatus
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.UserProfile
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UploadPictureEvent {
  data object Uploaded : UploadPictureEvent
}

/** Error message used by the caregiver Upload Picture ViewModel. */
enum class UploadPictureViewModelErrorMessage(val mess: String) {
  UNVERIFIED(mess = "Your identity can't be verified"),
  COULD_NOT_LOAD_PROFILE_ID(mess = "Could not load your profile: "),
  COULD_NOT_LOAD_CARE_CIRCLE(mess = "Could not load your care Circle: "),
  STILL_LOADING("Your care circle is still loading, try again"),
  NOT_SET_CARE_CIRCLE(mess = "Your care circle isn't set up"),
  SELECT_PICTURE(mess = "Please select a picture first"),
  UNKNOW_ERROR(mess = "Upload failed: "),
  COULD_NOT_READ_PICTURE(mess = "Could not read your picture: "),
  DESCRIPTION_EMPTY(mess = "description is  empty"),
  SELECT_DATE(mess = "date can't be empty"),
  PICK_A_PLACE(mess = "Pick the place from the suggestions"),
  FIRST_NAME_REQUIRED(mess = "A first name is required"),
  COULD_NOT_ADD_PERSON(mess = "Could not add person"),
  PERSON_WITHOUT_ID(mess = "This person wasn't saved, try again"),
  CANNOT_TAG_PERSON(
      mess = "This person was saved but can't be tagged: your care circle isn't loaded"
  ),
}

/** Start of the label of a member whose profile could not be read, instead of showing their uid. */
const val UNKNOWN_MEMBER_LABEL = "Unknown member"

/**
 * Label of the [number]-th member (from 1) whose profile could not be read, so that several of them
 * can be told apart.
 */
fun unknownMemberLabel(number: Int): String = "$UNKNOWN_MEMBER_LABEL $number"

/**
 * Represents the state displayed by the picture upload screen.
 *
 * @param description The description entered for the picture.
 * @param date The date associated with the picture, if one was selected.
 * @param location Where the picture was taken: the place search and the picked place.
 * @param shownIds The IDs of the people tagged in the picture.
 * @param pictureFile The local picture file selected for upload.
 * @param pictureWidth The width of the selected picture in pixels.
 * @param pictureHeight The height of the selected picture in pixels.
 * @param isUploading Whether an upload is currently in progress.
 * @param errorMsg The error message to display, if any.
 */
data class UploadPictureUIState(
    val description: String = "",
    val date: MemoryDate? = null,
    val location: PictureLocationUIState = PictureLocationUIState(),
    val shownIds: Set<String> = emptySet(),
    val pictureFile: File? = null,
    val pictureWidth: Int = 0,
    val pictureHeight: Int = 0,
    val isUploading: Boolean = false,
    val errorMsg: String? = null,
    val isAddPersonOpen: Boolean = false,
    val isAddingPerson: Boolean = false,
    val personFirstName: String = "",
    val personLastName: String = "",
    val personNickname: String = "",
) {
  val canUpload: Boolean
    get() =
        pictureFile != null &&
            description.isNotBlank() &&
            date != null &&
            location.hasValidPlace &&
            !isUploading
}

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
data class PictureLocationUIState(
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

/** The checks [UploadPictureViewModel.uploadPicture] runs before uploading. */
private sealed interface UploadCheck {
  /** Everything needed to upload is there. */
  data class Ready(
      val authorId: String,
      val circle: CareCircle,
      val file: File,
      val date: MemoryDate,
  ) : UploadCheck

  /** The upload can't start: [error] says why. */
  data class Blocked(val error: UploadPictureViewModelErrorMessage) : UploadCheck
}

/**
 * Manages the state and user actions for the picture upload screen.
 *
 * @param mediaRepo The repository used to create and upload media items.
 * @param authRepo The repository used to access the currently authenticated user.
 * @param profileRepo The repository used to load the authenticated user's profile.
 * @param circleRepo The repository used to load the user's care circle.
 * @param placeRepo The repository used to find real places from what the user types.
 * @param pictureReader The reader used to copy selected pictures and read their dimensions.
 */
class UploadPictureViewModel(
    private val mediaRepo: MediaRepository = MediaRepositoryProvider.repository,
    private val authRepo: AuthRepository = AuthRepositoryProvider.repository,
    private val profileRepo: ProfileRepository = ProfileRepositoryProvider.default.repository,
    private val circleRepo: CareCircleRepository = CareCircleRepositoryProvider.default.repository,
    private val placeRepo: PlaceSearchRepository = PlaceSearchRepositoryProvider.default.repository,
    private val pictureReader: SelectedPictureReader = reader,
) : ViewModel() {
  private val _uiState = MutableStateFlow(UploadPictureUIState())
  val uiState: StateFlow<UploadPictureUIState> = _uiState.asStateFlow()
  // “Upload finished, navigate away” is an event:
  // it should happen once and then be gone. A Channel is built for that.
  private val _careCircle = MutableStateFlow<CareCircle?>(null)
  val careCircle: StateFlow<CareCircle?> = _careCircle.asStateFlow()
  // uid -> Person of each circle member whose profile was read; null while the profiles load
  private val _members = MutableStateFlow<Map<String, Person>?>(null)
  // people saved by the add-person dialog before the circle arrived, keyed by id
  private val pendingPeople = mutableMapOf<String, Person>()
  // true once loading the circle has failed for good: nothing will apply pendingPeople
  private var circleLoadFailed = false
  private val _events = Channel<UploadPictureEvent>(Channel.BUFFERED)
  val events: Flow<UploadPictureEvent> = _events.receiveAsFlow()
  // What the user types in the place field, searched once they stop typing. Not a StateFlow: a
  // retry sends the same text again, and a StateFlow would drop it as unchanged.
  private val placeQueries =
      MutableSharedFlow<String>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    loadCircle()
    searchPlacesAsTyped()
  }

  // id -> label shown on the "who is involved" chips.
  // All the names in the care circle are clickable, using chips.
  // This prevents spelling mistakes and the omission of separators, making the code more robust.
  /**
   * People available for tagging, keyed by person ID and displayed by name. Empty until both the
   * circle and its members' profiles are loaded, so no chip shows a placeholder while loading.
   */
  val involvedOptions: StateFlow<Map<String, String>> =
      combine(careCircle, _members) { circle, members ->
            if (circle == null || members == null) emptyMap()
            else {
              val viewerUid = authRepo.currentUser?.uid
              var unknownCount = 0
              circle.taggableIds().associateWith { id ->
                circle.displayNameOf(id, viewerUid, members) ?: unknownMemberLabel(++unknownCount)
              }
            }
          }
          .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

  /**
   * The circle's saved places that can be picked as where the picture was taken, keyed by place ID
   * and displayed by name. A place without coordinates is left out: only real places are saved.
   */
  val placeOptions: StateFlow<Map<String, String>> =
      careCircle
          .map { circle -> circle?.pickablePlaces().orEmpty().mapValues { it.value.name } }
          .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

  /** This circle's saved places that are real points on the map. */
  private fun CareCircle.pickablePlaces(): Map<String, Place> = places.filterValues {
    it.location.lat != null && it.location.lng != null
  }

  /** Loads the care circle associated with the authenticated user. */
  private fun loadCircle() {
    val uid = authRepo.currentUser?.uid
    if (uid == null) {
      failCircleLoad(UploadPictureViewModelErrorMessage.UNVERIFIED.mess)
      return
    }
    viewModelScope.launch {
      // null when the user has no profile yet
      val profile: UserProfile? =
          profileRepo.getProfile(uid).getOrElse {
            failCircleLoad(
                UploadPictureViewModelErrorMessage.COULD_NOT_LOAD_PROFILE_ID.mess + "${it.message}"
            )
            return@launch
          }
      val circleId = profile?.circleIds?.firstOrNull()
      if (circleId == null) {
        failCircleLoad(UploadPictureViewModelErrorMessage.NOT_SET_CARE_CIRCLE.mess)
        return@launch
      }
      circleRepo
          .getCircle(circleId)
          .onSuccess { loaded ->
            // keep the people added while the circle was loading
            val circle =
                if (pendingPeople.isEmpty()) loaded
                else loaded.copy(people = loaded.people + pendingPeople)
            pendingPeople.clear()
            _careCircle.value = circle
            loadMembers(circle)
          }
          .onFailure {
            failCircleLoad(
                UploadPictureViewModelErrorMessage.COULD_NOT_LOAD_CARE_CIRCLE.mess + "${it.message}"
            )
          }
    }
  }

  /**
   * Records that the circle will not load, so people added later are refused, and shows [errorMsg].
   */
  private fun failCircleLoad(errorMsg: String) {
    circleLoadFailed = true
    pendingPeople.clear()
    setErrorMsg(errorMsg)
  }

  /**
   * Reads every member's profile so their name can be shown instead of their uid. A member whose
   * profile can't be read is left out and labelled with [unknownMemberLabel], unless the viewer
   * gave them a nickname.
   *
   * @param circle The loaded care circle.
   */
  private suspend fun loadMembers(circle: CareCircle) {
    _members.value = coroutineScope {
      circle.memberIds
          .map { uid ->
            async { profileRepo.getProfile(uid).getOrNull()?.let { uid to it.person } }
          }
          .awaitAll()
          .filterNotNull()
          .toMap()
    }
  }

  /** Uploads the picture currently stored in the UI state. */
  fun uploadPicture() {
    val state = _uiState.value
    if (state.isUploading) return
    when (val check = checkUpload(state)) {
      is UploadCheck.Blocked -> setErrorMsg(check.error.mess)
      is UploadCheck.Ready -> upload(state, check)
    }
  }

  /**
   * Checks that everything needed to upload is there, in the order the user should fix it.
   *
   * @param state The screen state to upload.
   * @return [UploadCheck.Ready] with what the upload needs, or the first missing thing.
   */
  private fun checkUpload(state: UploadPictureUIState): UploadCheck {
    val authorId = authRepo.currentUser?.uid
    val circle = _careCircle.value
    val file = state.pictureFile
    val date = state.date
    return when {
      authorId == null -> UploadCheck.Blocked(UploadPictureViewModelErrorMessage.UNVERIFIED)
      circle == null -> UploadCheck.Blocked(UploadPictureViewModelErrorMessage.STILL_LOADING)
      file == null -> UploadCheck.Blocked(UploadPictureViewModelErrorMessage.SELECT_PICTURE)
      state.description.isBlank() ->
          UploadCheck.Blocked(UploadPictureViewModelErrorMessage.DESCRIPTION_EMPTY)
      date == null -> UploadCheck.Blocked(UploadPictureViewModelErrorMessage.SELECT_DATE)
      !state.location.hasValidPlace ->
          UploadCheck.Blocked(UploadPictureViewModelErrorMessage.PICK_A_PLACE)
      else -> UploadCheck.Ready(authorId, circle, file, date)
    }
  }

  /**
   * Uploads the picture once [checkUpload] found everything needed.
   *
   * @param state The screen state to upload.
   * @param ready What [checkUpload] found.
   */
  private fun upload(state: UploadPictureUIState, ready: UploadCheck.Ready) {
    val circle = ready.circle
    // only people still in the circle can be tagged
    val taggableIds = circle.taggableIds()
    val shownIds = state.shownIds.filter { it in taggableIds }

    _uiState.update { it.copy(isUploading = true) }
    viewModelScope.launch {
      try {
        val id = mediaRepo.newMediaId(circle.id)
        val item =
            MediaItem.Picture(
                id = id,
                authorId = ready.authorId,
                // must start with careCircles/<circleId>/ (see MediaRepositoryFirebase.getFile)
                storagePath = "careCircles/${circle.id}/media/$id.jpg",
                createdAt = System.currentTimeMillis(),
                description = state.description,
                date = ready.date,
                location = state.location.pickedPlace,
                shownIds = shownIds,
                authorizedIds = emptyList(), // empty = whole circle can see it, for the moment
                status = MediaStatus.APPROVED,
                width = state.pictureWidth,
                height = state.pictureHeight,
            )
        mediaRepo.addMedia(circle, item, ready.file)
        _uiState.update { it.copy(isUploading = false) }
        _events.send(UploadPictureEvent.Uploaded)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.update { it.copy(isUploading = false) }
        setErrorMsg(UploadPictureViewModelErrorMessage.UNKNOW_ERROR.mess + (e.message ?: ""))
      }
    }
  }

  /**
   * Reads a selected picture and stores it in the UI state.
   *
   * @param uri The string representation of the selected picture URI.
   */
  fun selectPicture(uri: String) {
    viewModelScope.launch {
      try {
        val picture = pictureReader.read(uri)
        setPicture(
            picture.file,
            picture.width,
            picture.height,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        setErrorMsg(
            UploadPictureViewModelErrorMessage.COULD_NOT_READ_PICTURE.mess + (e.message ?: "")
        )
      }
    }
  }

  /**
   * Stores the local picture selected by the user.
   *
   * @param file The local picture file.
   * @param width The picture width in pixels.
   * @param height The picture height in pixels.
   */
  internal fun setPicture(file: File, width: Int, height: Int) {
    _uiState.update {
      it.copy(
          pictureFile = file,
          pictureWidth = width,
          pictureHeight = height,
      )
    }
  }

  /** Clears the current error message from the UI state. */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMsg = null) }
  }

  /**
   * Sets the error message shown by the picture upload screen.
   *
   * @param errorMsg The error message to display.
   */
  internal fun setErrorMsg(errorMsg: String) {
    _uiState.update { it.copy(errorMsg = errorMsg) }
  }

  /**
   * Updates the picture description in the UI state.
   *
   * @param description The new picture description.
   */
  fun setDescription(description: String) {
    _uiState.update { it.copy(description = description) }
  }

  /**
   * Updates the picture date in the UI state.
   *
   * @param date The date associated with the picture.
   */
  fun setDate(date: MemoryDate) {
    _uiState.update { it.copy(date = date) }
  }

  /**
   * Updates the place search typed by the user; [searchPlacesAsTyped] looks it up once they stop
   * typing. Any place picked before is dropped: the text no longer names it.
   *
   * @param query The text typed in the place field.
   */
  fun setPlaceQuery(query: String) {
    // Nothing changed: no new search will come, so it mustn't look like one is running
    if (query == _uiState.value.location.query) return
    _uiState.update {
      val location = PictureLocationUIState(query = query, suggestions = it.location.suggestions)
      val willSearch = location.isLongEnoughToSearch
      it.copy(
          location =
              location.copy(
                  isSearching = willSearch,
                  suggestions = if (willSearch) location.suggestions else emptyList(),
              )
      )
    }
    placeQueries.tryEmit(query)
  }

  /**
   * Searches the place text again after its search failed, e.g. once the network is back: the text
   * didn't change, so [setPlaceQuery] doesn't search it again.
   */
  fun retryPlaceSearch() {
    val location = _uiState.value.location
    if (!location.searchFailed) return
    _uiState.update {
      it.copy(location = it.location.copy(isSearching = true, searchFailed = false))
    }
    placeQueries.tryEmit(location.query)
  }

  /**
   * Looks up real places matching what the user types, once they stop typing: Geocoder goes over
   * the network, so not on every key. A newer query cancels the search still running for an older
   * one, and a result is only kept if the field still shows the query it was searched for.
   */
  @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
  private fun searchPlacesAsTyped() {
    placeQueries
        .debounce(PLACE_SEARCH_DEBOUNCE)
        .filter { PictureLocationUIState(query = it).isLongEnoughToSearch }
        .mapLatest { query -> query to placeRepo.search(query) }
        .onEach { (query, result) -> showPlaceSearchResult(query, result) }
        .launchIn(viewModelScope)
  }

  /**
   * Shows the places found for [query], unless the user has typed something else or picked a place
   * since.
   *
   * @param query The text that was searched.
   * @param result The places found, or why the search failed.
   */
  private fun showPlaceSearchResult(query: String, result: Result<List<GeoLocation>>) {
    val current = _uiState.value.location
    if (current.query != query || current.pickedPlace != null) return
    result
        .onSuccess { places ->
          _uiState.update {
            it.copy(location = it.location.copy(suggestions = places, isSearching = false))
          }
        }
        // Shown inline through searchFailed; errorMsg is left to the upload and the circle
        .onFailure {
          _uiState.update {
            it.copy(
                location =
                    it.location.copy(
                        suggestions = emptyList(),
                        isSearching = false,
                        searchFailed = true,
                    )
            )
          }
        }
  }

  /**
   * Picks one of the suggested real places as where the picture was taken.
   *
   * @param place One of [PictureLocationUIState.suggestions].
   */
  fun selectPlace(place: GeoLocation) {
    if (place.lat == null || place.lng == null) return // not a point on the map
    _uiState.update {
      it.copy(
          location = PictureLocationUIState(query = place.placeName.orEmpty(), pickedPlace = place)
      )
    }
  }

  /**
   * Picks one of the circle's saved places as where the picture was taken, or drops it if it was
   * already picked.
   *
   * @param placeId One of the keys of [placeOptions].
   */
  fun selectPlace(placeId: String) {
    if (_uiState.value.location.selectedPlaceId == placeId) {
      setPlaceQuery("")
      return
    }
    val place = _careCircle.value?.pickablePlaces()?.get(placeId) ?: return
    selectPlace(place.location.copy(placeName = place.name))
    _uiState.update { it.copy(location = it.location.copy(selectedPlaceId = placeId)) }
  }

  /**
   * Shows a person who was just added to the care circle and tags them in the picture, since they
   * were added while uploading it. Call it once the repository has saved [person] with its id. This
   * only updates the screen: the repository's addPerson is what writes them to Firestore.
   *
   * If the circle is still loading, [person] is kept and added to it once it arrives.
   *
   * @param person The saved person, with the id the repository generated.
   * @return A failure, with a message the dialog can show, if [person] has no id or the circle
   *   could not be loaded.
   */
  fun onPersonAdded(person: Person): Result<Unit> {
    if (person.id.isBlank()) {
      return Result.failure(
          IllegalArgumentException(UploadPictureViewModelErrorMessage.PERSON_WITHOUT_ID.mess)
      )
    }
    if (circleLoadFailed) {
      return Result.failure(
          IllegalStateException(UploadPictureViewModelErrorMessage.CANNOT_TAG_PERSON.mess)
      )
    }
    if (_careCircle.value == null) pendingPeople[person.id] = person
    else
        _careCircle.update { circle ->
          circle?.copy(people = circle.people + (person.id to person))
        }
    _uiState.update { it.copy(shownIds = it.shownIds + person.id) }
    return Result.success(Unit)
  }

  /**
   * Adds a person to the tagged people, or removes them if already tagged.
   *
   * @param id The ID of the person to select or deselect.
   */
  fun toggleShownId(id: String) {
    _uiState.update { state ->
      val updated = if (id in state.shownIds) state.shownIds - id else state.shownIds + id
      state.copy(shownIds = updated)
    }
  }

  fun onAddPerson() {
    _uiState.update { it.copy(isAddPersonOpen = true) }
  }

  fun dismissAddPerson() {
    if (_uiState.value.isAddingPerson) return
    _uiState.update {
      it.copy(
          isAddPersonOpen = false,
          personFirstName = "",
          personLastName = "",
          personNickname = "",
      )
    }
  }

  fun setPersonFirstName(firstName: String) {
    _uiState.update { it.copy(personFirstName = firstName) }
  }

  fun setPersonLastName(lastName: String) {
    _uiState.update { it.copy(personLastName = lastName) }
  }

  fun setPersonNickname(nickname: String) {
    _uiState.update { it.copy(personNickname = nickname) }
  }

  /** Saves a person in the care circle and makes them available for picture tagging. */
  fun addPerson() {
    val circle =
        _careCircle.value
            ?: run {
              setErrorMsg(UploadPictureViewModelErrorMessage.STILL_LOADING.mess)
              return
            }
    val state = _uiState.value
    if (state.isAddingPerson) return
    if (state.personFirstName.isBlank()) {
      setErrorMsg(UploadPictureViewModelErrorMessage.FIRST_NAME_REQUIRED.mess)
      return
    }
    _uiState.update { it.copy(isAddingPerson = true) }
    viewModelScope.launch {
      try {
        circleRepo
            .addPerson(
                circle.id,
                Person(
                    firstName = state.personFirstName,
                    lastName = state.personLastName,
                    nickname = state.personNickname,
                ),
            )
            .onSuccess { person ->
              _careCircle.update { it?.copy(people = it.people + (person.id to person)) }
              _uiState.update {
                it.copy(
                    isAddPersonOpen = false,
                    isAddingPerson = false,
                    personFirstName = "",
                    personLastName = "",
                    personNickname = "",
                    shownIds = it.shownIds + person.id,
                )
              }
            }
            .onFailure {
              setErrorMsg(
                  it.message ?: UploadPictureViewModelErrorMessage.COULD_NOT_ADD_PERSON.mess
              )
            }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        setErrorMsg(e.message ?: UploadPictureViewModelErrorMessage.COULD_NOT_ADD_PERSON.mess)
      } finally {
        _uiState.update { it.copy(isAddingPerson = false) }
      }
    }
  }

  private companion object {
    /** How long the user must stop typing before their place search is sent. */
    val PLACE_SEARCH_DEBOUNCE = 300.milliseconds
  }
}
