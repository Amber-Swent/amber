package com.github.se.amber.ui.caregiver.upload.media

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
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.model.user.Person
import java.io.File
import java.time.LocalDate
import kotlin.collections.orEmpty
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Error message shared by the caregiver Upload Media ViewModel. */
enum class UploadMediaViewModelErrorMessage(val mess: String) {
  UNVERIFIED(mess = "Your identity can't be verified"),
  COULD_NOT_LOAD_PROFILE_ID(mess = "Could not load your profile: "),
  COULD_NOT_LOAD_CARE_CIRCLE(mess = "Could not load your care Circle: "),
  STILL_LOADING("Your care circle is still loading, try again"),
  NOT_SET_CARE_CIRCLE(mess = "Your care circle isn't set up"),
  UNKNOW_ERROR(mess = "Upload failed: "),
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

/** The fields shared by every media upload screen; define by MediaItem. */
data class UploadMediaUIState(
    val description: String = "",
    val date: MemoryDate? = MemoryDate(LocalDate.now().year),
    val location: LocationUIState = LocationUIState(),
    val shownIds: Set<String> = emptySet(),
    val isUploading: Boolean = false,
    val errorMsg: String? = null,
    val isAddPersonOpen: Boolean = false,
    val isAddingPerson: Boolean = false,
    val personFirstName: String = "",
    val personLastName: String = "",
    val personNickname: String = "",
) {
  /** Whether the shared fields allow an upload; each media also checks its own content. */
  val canUpload: Boolean
    get() = description.isNotBlank() && date != null && location.hasValidPlace && !isUploading
}

/** One-shot events shared by every media upload screen. */
sealed interface UploadMediaEvent {
  data object Uploaded : UploadMediaEvent
}

/** Common result of checking the prerequisites for a media upload. */
sealed interface UploadCheck {
  data class Ready(
      val authorId: String,
      val circle: CareCircle,
      val date: MemoryDate,
      val file: File? = null,
  ) : UploadCheck

  data class Blocked(val error: String) : UploadCheck
}

/**
 * Base ViewModel for creating any kind of media.
 *
 * Media-specific ViewModels only own their media content (file, text, recording...), their
 * validation of that content and the [MediaItem] they build. Everything else is shared here.
 */
abstract class UploadMediaViewModel(
    protected val mediaRepo: MediaRepository = MediaRepositoryProvider.repository,
    protected val authRepo: AuthRepository = AuthRepositoryProvider.repository,
    protected val profileRepo: ProfileRepository = ProfileRepositoryProvider.default.repository,
    protected val circleRepo: CareCircleRepository =
        CareCircleRepositoryProvider.default.repository,
    locationPicker: LocationPicker? = null,
    placeRepo: PlaceSearchRepository = PlaceSearchRepositoryProvider.default.repository,
) : ViewModel() {

// --------- Variable -------------
protected val picker = locationPicker ?: LocationPicker(placeRepo, viewModelScope)

    private val _fields = MutableStateFlow(UploadMediaUIState())
  protected val _mediaState = MutableStateFlow(UploadMediaUIState())
    // UI continue to see it as a single State, with location inside
    val mediaState: StateFlow<UploadMediaUIState> =
        combine(_fields, picker.state) { fields, location -> fields.copy(location = location) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, UploadMediaUIState())
    protected val _careCircle = MutableStateFlow<CareCircle?>(null)
  val careCircle: StateFlow<CareCircle?> = _careCircle.asStateFlow()

  /**
   * “Upload finished, navigate away” is an event: it should happen once and then be gone. A Channel
   * is built for that.
   */
  private val _events = Channel<UploadMediaEvent>(Channel.BUFFERED)
  val events: Flow<UploadMediaEvent> = _events.receiveAsFlow()

  /** uid -> Person of each circle member whose profile was read; null while the profiles load */
  private val _members = MutableStateFlow<Map<String, Person>?>(null)
  /** people saved by the add-person dialog before the circle arrived, keyed by id */
  private val pendingPeople = mutableMapOf<String, Person>()
  /** true once loading the circle has failed for good: nothing will apply pendingPeople */
  protected var circleLoadFailed = false

  /** The currently authenticated user's id, or null when no user is signed in. */
  protected val currentUserId: String?
    get() = authRepo.currentUser?.uid

  /**
   * People available for tagging, keyed by person ID and displayed by name. Empty until both the
   * circle and its members' profiles are loaded, so no chip shows a placeholder while loading.
   */
  val involvedOptions: StateFlow<Map<String, String>> =
      combine(careCircle, _members) { circle, members ->
            if (circle == null || members == null) emptyMap()
            else {
              val viewerUid = currentUserId
              var unknownCount = 0
              circle.taggableIds().associateWith { id ->
                circle.displayNameOf(id, viewerUid, members) ?: unknownMemberLabel(++unknownCount)
              }
            }
          }
          .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

  /**
   * The circle's saved places that can be picked as where the media happened, keyed by place ID and
   * displayed by name. A place without coordinates is left out: only real places are saved.
   */
  val placeOptions: StateFlow<Map<String, String>> =
      careCircle
          .map { circle -> circle?.pickablePlaces().orEmpty().mapValues { it.value.name } }
          .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

// --------------- Init ------------------

    init {
    loadCircle()
  }

// --------------- Update and Set Functions ------------------

  protected fun updateMediaState(transform: (UploadMediaUIState) -> UploadMediaUIState) {
    _mediaState.update(transform)
  }

  /** Clears the error message shown by the media upload screen. */
  fun clearErrorMsg() {
    updateMediaState { it.copy(errorMsg = null) }
  }

  /** Sets the error message shown by the media upload screen. */
  internal fun setErrorMsg(errorMsg: String) {
    updateMediaState { it.copy(errorMsg = errorMsg) }
  }

  /** Updates the upload progress state shared by every media type. */
  protected fun setUploading(isUploading: Boolean) {
    updateMediaState { it.copy(isUploading = isUploading) }
  }

  /** Updates the description in the UI state. */
  fun setDescription(description: String) {
    updateMediaState { it.copy(description = description) }
  }

  /** Updates the date in the UI state. */
  fun setDate(date: MemoryDate?) {
    updateMediaState { it.copy(date = date) }
  }

  // ------------------- Load CareCircle -------------------

  /** Loads the care circle associated with the authenticated user. */
  protected fun loadCircle() {
    val uid = currentUserId
    if (uid == null) {
      failCircleLoad(UploadMediaViewModelErrorMessage.UNVERIFIED.mess)
      return
    }
    viewModelScope.launch {
      val profile =
          profileRepo.getProfile(uid).getOrElse {
            failCircleLoad(
                UploadMediaViewModelErrorMessage.COULD_NOT_LOAD_PROFILE_ID.mess + it.message
            )
            return@launch
          }
      val circleId = profile?.circleIds?.firstOrNull()
      if (circleId == null) {
        failCircleLoad(UploadMediaViewModelErrorMessage.NOT_SET_CARE_CIRCLE.mess)
        return@launch
      }
      circleRepo
          .getCircle(circleId)
          .onSuccess { loaded ->
            val circle = prepareLoadedCircle(loaded)
            _careCircle.value = circle
            loadMembers(circle)
          }
          .onFailure {
            failCircleLoad(
                UploadMediaViewModelErrorMessage.COULD_NOT_LOAD_CARE_CIRCLE.mess + it.message
            )
          }
    }
  }

  private fun failCircleLoad(errorMsg: String) {
    circleLoadFailed = true
    setErrorMsg(errorMsg)
  }

  /** Adds the people saved by the add-person dialog while the circle was loading. */
  private fun prepareLoadedCircle(circle: CareCircle): CareCircle {
    if (pendingPeople.isEmpty()) return circle
    val merged = circle.copy(people = circle.people + pendingPeople)
    pendingPeople.clear()
    return merged
  }

  /**
   * Reads every member's profile so their name can be shown instead of their uid. A member whose
   * profile can't be read is left out and labeled with [unknownMemberLabel], unless the viewer gave
   * them a nickname.
   *
   * @param circle The loaded care circle.
   */
  private fun loadMembers(circle: CareCircle) {
    viewModelScope.launch {
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
  }

  /** This circle's saved places that are real points on the map. */
  private fun CareCircle.pickablePlaces(): Map<String, Place> = places.filterValues {
    it.location.lat != null && it.location.lng != null
  }
// ----------------- Location ---------------------
    // Delegation to avoid making the UI complex
    fun setPlaceQuery(query: String) = picker.setQuery(query)
    fun retryPlaceSearch() = picker.retrySearch()
    fun selectPlace(place: GeoLocation) = picker.selectPlace(place)
    fun selectPlace(placeId: String) =
        picker.selectSavedPlace(placeId, _careCircle.value?.pickablePlaces().orEmpty())

// ----------------- Tagged people and add-person dialog ---------------------

  /**
   * Adds a person to the tagged people, or removes them if already tagged.
   *
   * @param id The ID of the person to select or deselect.
   */
  fun toggleShownId(id: String) {
    updateMediaState {
      val updated = if (id in it.shownIds) it.shownIds - id else it.shownIds + id
      it.copy(shownIds = updated)
    }
  }

  private fun addShownId(id: String) {
    updateMediaState { it.copy(shownIds = it.shownIds + id) }
  }

  private fun closeAddPersonDialog() {
    updateMediaState {
      it.copy(
          isAddPersonOpen = false,
          isAddingPerson = false,
          personFirstName = "",
          personLastName = "",
          personNickname = "",
      )
    }
  }

  /**
   * Shows a person who was just added to the care circle and tags them in the media, since they
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
          IllegalArgumentException(UploadMediaViewModelErrorMessage.PERSON_WITHOUT_ID.mess)
      )
    }
    if (circleLoadFailed) {
      return Result.failure(
          IllegalStateException(UploadMediaViewModelErrorMessage.CANNOT_TAG_PERSON.mess)
      )
    }
    if (_careCircle.value == null) pendingPeople[person.id] = person
    else
        _careCircle.update { circle ->
          circle?.copy(people = circle.people + (person.id to person))
        }
    addShownId(person.id)
    return Result.success(Unit)
  }

  fun onAddPerson() {
    updateMediaState { it.copy(isAddPersonOpen = true) }
  }

  fun dismissAddPerson() {
    if (_mediaState.value.isAddingPerson) return
    closeAddPersonDialog()
  }

  fun setPersonFirstName(firstName: String) {
    updateMediaState { it.copy(personFirstName = firstName) }
  }

  fun setPersonLastName(lastName: String) {
    updateMediaState { it.copy(personLastName = lastName) }
  }

  fun setPersonNickname(nickname: String) {
    updateMediaState { it.copy(personNickname = nickname) }
  }

  /** Saves a person in the care circle and makes them available for tagging. */
  fun addPerson() {
    val circle =
        _careCircle.value
            ?: run {
              setErrorMsg(UploadMediaViewModelErrorMessage.STILL_LOADING.mess)
              return
            }
    val state = _mediaState.value
    if (state.isAddingPerson) return
    if (state.personFirstName.isBlank()) {
      setErrorMsg(UploadMediaViewModelErrorMessage.FIRST_NAME_REQUIRED.mess)
      return
    }
    updateMediaState { it.copy(isAddingPerson = true) }
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
              closeAddPersonDialog()
              addShownId(person.id)
            }
            .onFailure {
              setErrorMsg(it.message ?: UploadMediaViewModelErrorMessage.COULD_NOT_ADD_PERSON.mess)
            }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        setErrorMsg(e.message ?: UploadMediaViewModelErrorMessage.COULD_NOT_ADD_PERSON.mess)
      } finally {
        updateMediaState { it.copy(isAddingPerson = false) }
      }
    }
  }

// ----------------- Upload ---------------------

  /**
   * Checks everything needed to upload, in the order the user should fix it: sign-in, circle, date,
   * the media's own content, description, then place.
   *
   * @param missingContentError The error to show if the media's own content (picture, text,
   *   recording...) is missing, or null if it is there.
   * @return [UploadCheck.Ready] with what the upload needs, or the first missing thing.
   */
  protected fun checkCommonUpload(missingContentError: String?): UploadCheck {
    val state = _mediaState.value
    val authorId = currentUserId
    val circle = careCircle.value
    val date = state.date
    return when {
      authorId == null -> UploadCheck.Blocked(UploadMediaViewModelErrorMessage.UNVERIFIED.mess)
      circle == null -> UploadCheck.Blocked(UploadMediaViewModelErrorMessage.STILL_LOADING.mess)
      date == null -> UploadCheck.Blocked(UploadMediaViewModelErrorMessage.SELECT_DATE.mess)
      missingContentError != null -> UploadCheck.Blocked(missingContentError)
      state.description.isBlank() ->
          UploadCheck.Blocked(UploadMediaViewModelErrorMessage.DESCRIPTION_EMPTY.mess)
      !state.location.hasValidPlace ->
          UploadCheck.Blocked(UploadMediaViewModelErrorMessage.PICK_A_PLACE.mess)
      else -> UploadCheck.Ready(authorId, circle, date)
    }
  }

  /**
   * Uploads the media if [check] found everything needed, otherwise shows what is missing. Does
   * nothing while an upload is already running.
   *
   * @param check The result of the media's own check.
   * @param buildItem Builds the media item from its new id, the people to tag (only people still in
   *   the circle) and what the check found.
   */
  protected fun startUpload(
      check: UploadCheck,
      buildItem: (id: String, shownIds: List<String>, ready: UploadCheck.Ready) -> MediaItem,
  ) {
    if (_mediaState.value.isUploading) return
    when (check) {
      is UploadCheck.Blocked -> setErrorMsg(check.error)
      is UploadCheck.Ready -> upload(check, buildItem)
    }
  }

  private fun upload(
      ready: UploadCheck.Ready,
      buildItem: (id: String, shownIds: List<String>, ready: UploadCheck.Ready) -> MediaItem,
  ) {
    val circle = ready.circle
    // only people still in the circle can be tagged
    val taggableIds = circle.taggableIds()
    val shownIds = _mediaState.value.shownIds.filter { it in taggableIds }

    setUploading(true)
    viewModelScope.launch {
      try {
        val id = mediaRepo.newMediaId(circle.id)
        mediaRepo.addMedia(circle, buildItem(id, shownIds, ready), ready.file)
        setUploading(false)
        _events.send(UploadMediaEvent.Uploaded)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        setUploading(false)
        setErrorMsg(UploadMediaViewModelErrorMessage.UNKNOW_ERROR.mess + (e.message ?: ""))
      }
    }
  }
}
