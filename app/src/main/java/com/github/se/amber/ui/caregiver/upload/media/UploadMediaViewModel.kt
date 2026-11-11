/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 * Modified by MeiliV, with assistance from OpenAI Codex.
 * Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
 * Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload.media

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
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
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.model.validation.InputValidation
import com.github.se.amber.ui.utils.UiText
import com.github.se.amber.ui.utils.errorText
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
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

/** One-shot events sent by a [MediaViewModel] to its screen. */
sealed interface MediaEvent {
  /** The media was saved: the screen can navigate away. */
  data object Uploaded : MediaEvent
}

/**
 * Error messages shared by every upload ViewModel (text, picture, audio). A message that only makes
 * sense for one kind of media (e.g. "select a picture") belongs to that ViewModel, not here.
 *
 * @property messageRes the message shown to the user, as a string resource.
 */
enum class MediaViewModelErrorMessage(@param:StringRes val messageRes: Int) {
  UNVERIFIED(R.string.uploadMediaErrorUnverified),
  COULD_NOT_LOAD_PROFILE_ID(R.string.uploadMediaErrorLoadProfile),
  COULD_NOT_LOAD_CARE_CIRCLE(R.string.uploadMediaErrorLoadCircle),
  STILL_LOADING(R.string.uploadMediaErrorStillLoading),
  NOT_SET_CARE_CIRCLE(R.string.uploadMediaErrorNoCircle),
  UNKNOW_ERROR(R.string.uploadMediaErrorUploadFailed),
  DESCRIPTION_EMPTY(R.string.uploadMediaErrorDescriptionEmpty),
  SELECT_DATE(R.string.uploadMediaErrorSelectDate),
  PICK_A_PLACE(R.string.uploadMediaErrorPickPlace),
  FIRST_NAME_REQUIRED(R.string.uploadMediaErrorFirstNameRequired),
  INVALID_PERSON_NAME(R.string.uploadMediaErrorInvalidPersonName),
  COULD_NOT_ADD_PERSON(R.string.uploadMediaErrorAddPerson),
  PERSON_WITHOUT_ID(R.string.uploadMediaErrorPersonWithoutId),
  CANNOT_TAG_PERSON(R.string.uploadMediaErrorCannotTagPerson),
}

/**
 * Why [MediaViewModel.onPersonAdded] refused a person.
 *
 * @property error the message to show, e.g. in the add-person dialog.
 */
class PersonNotTaggedException(val error: MediaViewModelErrorMessage) : Exception(error.name)

/**
 * Label of the [number]-th member (from 1) whose profile could not be read, so that several of them
 * can be told apart: "Unknown member 3" in English, "Membre inconnu 3" in French.
 */
fun unknownMemberLabel(number: Int): UiText =
    UiText.Resource(R.string.unknownMember, listOf(number))

/** The fewest characters a place search needs before it is sent. */
const val MIN_PLACE_QUERY_LENGTH = 3

/**
 * Represents where the media was made: the place the user searches for, and the place picked.
 *
 * @param query What the user typed to search for where the media was made.
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
data class MediaLocationUIState(
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

/**
 * What every upload screen state (text, picture, audio) holds. A screen state keeps it in one
 * property, `common`, next to its own fields (e.g. the picture file).
 *
 * @param description The description entered for the media.
 * @param date The date associated with the media, if one was selected.
 * @param location Where the media was made: the place search and the picked place.
 * @param shownIds The IDs of the people tagged in the media.
 * @param isUploading Whether an upload is currently in progress.
 * @param errorMsgRes The error to display, as a string resource, if any.
 * @param isAddPersonOpen Whether the add-person dialog is open.
 * @param isAddingPerson Whether the person typed in the add-person dialog is being saved.
 * @param personFirstName The first name typed in the add-person dialog.
 * @param personLastName The last name typed in the add-person dialog.
 * @param personNickname The nickname typed in the add-person dialog.
 * @param isAddPersonSubmitted Whether the user tapped Add in the add-person dialog, after which an
 *   unfinished name like "Anne-" shows its error too.
 */
data class CommonMediaUIState(
    val description: String = "",
    val date: MemoryDate? = null,
    val location: MediaLocationUIState = MediaLocationUIState(),
    val shownIds: Set<String> = emptySet(),
    val isUploading: Boolean = false,
    @param:StringRes val errorMsgRes: Int? = null,
    val isAddPersonOpen: Boolean = false,
    val isAddingPerson: Boolean = false,
    val personFirstName: String = "",
    val personLastName: String = "",
    val personNickname: String = "",
    val isAddPersonSubmitted: Boolean = false,
) {
  /**
   * Whether the common fields allow an upload: a description, a date, a valid place, and no upload
   * already in progress. A screen state adds the check of its own content.
   */
  val canUpload: Boolean
    get() =
        InputValidation.DESCRIPTION.isValid(description) &&
            date != null &&
            location.hasValidPlace &&
            !isUploading

  /** Why the first name typed in the add-person dialog is refused; null while it is blank. */
  val personFirstNameError: UiText?
    get() =
        InputValidation.NAME.errorText(
            personFirstName,
            submitted = isAddPersonSubmitted,
            blankRes = null,
            formatRes = R.string.inputErrorNameFormat,
        )

  /** Why the surname typed in the add-person dialog is refused, or null. */
  val personLastNameError: UiText?
    get() =
        InputValidation.OPTIONAL_NAME.errorText(
            personLastName,
            submitted = isAddPersonSubmitted,
            formatRes = R.string.inputErrorNameFormat,
        )

  /** Why the nickname typed in the add-person dialog is refused, or null. */
  val personNicknameError: UiText?
    get() =
        InputValidation.OPTIONAL_NAME.errorText(
            personNickname,
            submitted = isAddPersonSubmitted,
            formatRes = R.string.inputErrorNameFormat,
        )

  /**
   * Whether the add-person dialog's Add button is enabled: a first name is typed, no field shows an
   * error, and no person is being saved. An unfinished name keeps it enabled until it is tapped, so
   * that its error shows then instead of while the user is still typing.
   */
  val canAddPerson: Boolean
    get() =
        !isAddingPerson &&
            personFirstName.isNotBlank() &&
            personFirstNameError == null &&
            personLastNameError == null &&
            personNicknameError == null

  /** Whether the names typed in the add-person dialog can be saved as they are. */
  val arePersonNamesValid: Boolean
    get() =
        InputValidation.NAME.isValid(personFirstName) &&
            InputValidation.OPTIONAL_NAME.isValid(personLastName) &&
            InputValidation.OPTIONAL_NAME.isValid(personNickname)
}

/**
 * The state of an upload screen (text, picture, audio): the fields they share, in [common], and the
 * ones of the kind of media, in the implementing data class.
 *
 * A [MediaViewModel] knows its state only through this interface, so it can't call the `copy` of
 * the data class. The data class forwards [withCommon] to it:
 * ```
 * override fun withCommon(common: CommonMediaUIState) = copy(common = common)
 * ```
 *
 * @param S The implementing state type, returned by [withCommon].
 */
interface MediaUIState<S : MediaUIState<S>> {
  /** The fields shared by every upload screen. */
  val common: CommonMediaUIState

  /** Whether everything needed to upload is there: the media's content and the common fields. */
  val canUpload: Boolean

  /**
   * Copies this state with [common] replaced, keeping the fields specific to the media.
   *
   * @param common The new common fields.
   */
  fun withCommon(common: CommonMediaUIState): S
}

/** The checks [MediaViewModel.uploadMedia] runs before uploading. */
private sealed interface UploadCheck {
  /** Everything needed to upload is there. */
  data class Ready(
      val authorId: String,
      val circle: CareCircle,
      val date: MemoryDate,
  ) : UploadCheck

  /** The upload can't start: [errorMsgRes] says why. */
  data class Blocked(@param:StringRes val errorMsgRes: Int) : UploadCheck
}

/**
 * Manages the state and user actions common to every upload screen (text, picture, audio): loading
 * the care circle and its members, the description, the date, the place, the people shown, the
 * add-person dialog and the upload itself.
 *
 * A subclass supplies what depends on the kind of media: its initial state ([initialState]), what
 * its content must contain ([checkContent]), its file ([fileOf]) and its [MediaItem]
 * ([createItem]). It adds its own functions and state fields (e.g. selecting a picture).
 *
 * @param S The state type of the screen.
 * @param initialState The state the screen starts with.
 * @param mediaRepo The repository used to create and upload media items.
 * @param authRepo The repository used to access the currently authenticated user.
 * @param profileRepo The repository used to load the authenticated user's profile.
 * @param circleRepo The repository used to load the user's care circle.
 * @param placeRepo The repository used to find real places from what the user types.
 * @param cleanupScope Where [cancel] finishes its cleanup. It is not the ViewModel's own scope,
 *   which ends as soon as the screen is left, and cancel is usually followed by leaving the screen.
 */
abstract class MediaViewModel<S : MediaUIState<S>>(
    initialState: S,
    protected val mediaRepo: MediaRepository = MediaRepositoryProvider.repository,
    protected val authRepo: AuthRepository = AuthRepositoryProvider.repository,
    protected val profileRepo: ProfileRepository = ProfileRepositoryProvider.default.repository,
    protected val circleRepo: CareCircleRepository =
        CareCircleRepositoryProvider.default.repository,
    protected val placeRepo: PlaceSearchRepository =
        PlaceSearchRepositoryProvider.default.repository,
    private val cleanupScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ViewModel() {
  /** Writable state, for the subclass to update its own fields. */
  protected val _uiState = MutableStateFlow(initialState)
  val uiState: StateFlow<S> = _uiState.asStateFlow()
  private val _careCircle = MutableStateFlow<CareCircle?>(null)
  val careCircle: StateFlow<CareCircle?> = _careCircle.asStateFlow()
  /** uid -> Person of each circle member whose profile was read; null while the profiles load */
  private val _members = MutableStateFlow<Map<String, Person>?>(null)
  /** people saved by the add-person dialog before the circle arrived, keyed by id */
  private val pendingPeople = mutableMapOf<String, Person>()
  /** true once loading the circle has failed for good: nothing will apply pendingPeople */
  private var circleLoadFailed = false
  /**
   * “Upload finished, navigate away” is an event: it should happen once and then be gone. A Channel
   * is built for that.
   */
  private val _events = Channel<MediaEvent>(Channel.BUFFERED)
  val events: Flow<MediaEvent> = _events.receiveAsFlow()
  /**
   * What the user types in the place field, searched once they stop typing. Not a StateFlow: a
   * retry sends the same text again, and a StateFlow would drop it as unchanged.
   */
  private val placeQueries =
      MutableSharedFlow<String>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  // id -> label shown on the "who is involved" chips.
  // All the names in the care circle are clickable, using chips.
  // This prevents spelling mistakes and the omission of separators, making the code more robust.
  /**
   * People available for tagging, keyed by person ID and displayed by name. Empty until both the
   * circle and its members' profiles are loaded, so no chip shows a placeholder while loading.
   *
   * A name is user data ([UiText.Dynamic]); a member whose profile can't be read is labelled with
   * [unknownMemberLabel], which is app text.
   */
  val involvedOptions: StateFlow<Map<String, UiText>> =
      combine(careCircle, _members) { circle, members ->
            if (circle == null || members == null) emptyMap()
            else {
              val viewerUid = authRepo.currentUser?.uid
              var unknownCount = 0
              circle.taggableIds().associateWith { id ->
                circle.displayNameOf(id, viewerUid, members)?.let { UiText.Dynamic(it) }
                    ?: unknownMemberLabel(++unknownCount)
              }
            }
          }
          .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

  /**
   * The circle's saved places that can be picked as where the media was made, keyed by place ID and
   * displayed by name. A place without coordinates is left out: only real places are saved.
   */
  val placeOptions: StateFlow<Map<String, String>> =
      careCircle
          .map { circle -> circle?.pickablePlaces().orEmpty().mapValues { it.value.name } }
          .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

  init {
    loadCircle()
    searchPlacesAsTyped()
  }

  /**
   * Checks the part of the state that depends on the kind of media (e.g. a picture was selected).
   * Run by [uploadMedia] after the care circle is known and before the common fields are checked.
   *
   * @param state The screen state to upload.
   * @return The error to show if the content is missing, as a string resource, or null if it is
   *   there.
   */
  @StringRes protected abstract fun checkContent(state: S): Int?

  /**
   * The local file holding the media's bytes.
   *
   * @param state The screen state to upload.
   * @return The file to upload, or null for a media without a file (a text).
   */
  protected abstract fun fileOf(state: S): File?

  /**
   * Builds the media to upload, once [checkContent] and the common checks have passed.
   *
   * @param id The new media ID, from [MediaRepository.newMediaId].
   * @param state The screen state to upload.
   * @param authorId The uid of the signed-in user.
   * @param circle The care circle the media is added to.
   * @param date The date of the media.
   * @param shownIds The people shown, already restricted to those still in the circle.
   * @return The [MediaItem] subclass matching this kind of media.
   */
  protected abstract fun createItem(
      id: String,
      state: S,
      authorId: String,
      circle: CareCircle,
      date: MemoryDate,
      shownIds: List<String>,
  ): MediaItem

  /** Called once the repository has saved the media, before the screen is told it was uploaded. */
  protected open fun onUploaded() {
    // Nothing to do by default: only a screen that keeps files of its own (the picture's camera
    // captures) needs to know the upload took its file
  }

  /** This circle's saved places that are real points on the map. */
  private fun CareCircle.pickablePlaces(): Map<String, Place> = places.filterValues {
    it.location.lat != null && it.location.lng != null
  }

  /** Loads the care circle associated with the authenticated user. */
  private fun loadCircle() {
    val uid = authRepo.currentUser?.uid
    if (uid == null) {
      failCircleLoad(MediaViewModelErrorMessage.UNVERIFIED.messageRes)
      return
    }
    viewModelScope.launch {
      // null when the user has no profile yet
      val profile: UserProfile? =
          profileRepo.getProfile(uid).getOrElse {
            Log.e(TAG, "Failed to load the profile", it)
            failCircleLoad(MediaViewModelErrorMessage.COULD_NOT_LOAD_PROFILE_ID.messageRes)
            return@launch
          }
      val circleId = profile?.circleIds?.firstOrNull()
      if (circleId == null) {
        failCircleLoad(MediaViewModelErrorMessage.NOT_SET_CARE_CIRCLE.messageRes)
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
            Log.e(TAG, "Failed to load the care circle", it)
            failCircleLoad(MediaViewModelErrorMessage.COULD_NOT_LOAD_CARE_CIRCLE.messageRes)
          }
    }
  }

  /**
   * Records that the circle will not load, so people added later are refused, and shows
   * [errorMsgRes].
   */
  private fun failCircleLoad(@StringRes errorMsgRes: Int) {
    circleLoadFailed = true
    pendingPeople.clear()
    setErrorMsg(errorMsgRes)
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

  /** The upload in progress, kept so [cancel] can stop it. */
  private var uploadJob: Job? = null

  /**
   * Care circle id and media id of the upload in progress, from the moment its id is known until it
   * is saved. If [cancel] comes in between, the media may already be in the outbox.
   */
  @Volatile private var savedMedia: Pair<String, String>? = null

  /**
   * The user cancels: stops the upload in progress, if any, and throws away what the screen holds
   * (see [discardContent]). Always possible, also during an upload.
   *
   * It takes the media back as far as it can, without guaranteeing it: the media may reach the
   * outbox just before the upload stops, and is then taken out of it. But it may still be sent if
   * it was already saved (the upload is over), if its send had already begun
   * ([MediaRepository.discardUnsent]), or if taking it out of the outbox fails (only logged).
   */
  fun cancel() {
    val job = uploadJob
    uploadJob = null
    updateCommon { it.copy(isUploading = false) }
    val leftover = discardContent()
    // cleanupScope, not viewModelScope: the screen is left right after cancel(), which ends
    // viewModelScope and would cut this cleanup short
    // Waiting for the upload to stop first: it must not add the media after it was taken out, nor
    // lose its file while still using it
    cleanupScope.launch {
      job?.cancelAndJoin()
      savedMedia?.let { (circleId, mediaId) ->
        try {
          mediaRepo.discardUnsent(circleId, mediaId)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.e(TAG, "Could not take the canceled media out of the outbox", e)
        }
      }
      savedMedia = null
      leftover?.let(::deleteOrWarn)
    }
  }

  /**
   * Deletes [file], and logs a warning if it is still there. A file left behind only wastes space
   * in the cache, and the recorder or the picker deletes its leftovers later.
   *
   * @param file The file to delete.
   */
  protected fun deleteOrWarn(file: File) {
    if (!file.delete() && file.exists()) {
      Log.w(TAG, "Couldn't delete ${file.name}; it stays in the cache until it is cleaned up")
    }
  }

  /**
   * Throws away what the screen holds for the media, for [cancel]: stops what uses it and clears it
   * from the UI state, without deleting its file yet.
   *
   * @return The file to delete once the upload has stopped, or null if there is none. Nothing by
   *   default.
   */
  protected open fun discardContent(): File? = null

  /** Uploads the media currently stored in the UI state. */
  fun uploadMedia() {
    val state = _uiState.value
    if (state.common.isUploading) return
    when (val check = checkUpload(state)) {
      is UploadCheck.Blocked -> setErrorMsg(check.errorMsgRes)
      is UploadCheck.Ready -> upload(state, check)
    }
  }

  /**
   * Checks that everything needed to upload is there, in the order the user should fix it.
   *
   * @param state The screen state to upload.
   * @return [UploadCheck.Ready] with what the upload needs, or the first missing thing.
   */
  private fun checkUpload(state: S): UploadCheck {
    val authorId = authRepo.currentUser?.uid
    val circle = _careCircle.value
    val date = state.common.date
    val contentError = if (authorId != null && circle != null) checkContent(state) else null
    return when {
      authorId == null -> UploadCheck.Blocked(MediaViewModelErrorMessage.UNVERIFIED.messageRes)
      circle == null -> UploadCheck.Blocked(MediaViewModelErrorMessage.STILL_LOADING.messageRes)
      contentError != null -> UploadCheck.Blocked(contentError)
      !InputValidation.DESCRIPTION.isValid(state.common.description) ->
          UploadCheck.Blocked(MediaViewModelErrorMessage.DESCRIPTION_EMPTY.messageRes)
      date == null -> UploadCheck.Blocked(MediaViewModelErrorMessage.SELECT_DATE.messageRes)
      !state.common.location.hasValidPlace ->
          UploadCheck.Blocked(MediaViewModelErrorMessage.PICK_A_PLACE.messageRes)
      else -> UploadCheck.Ready(authorId, circle, date)
    }
  }

  /**
   * Uploads the media once [checkUpload] found everything needed.
   *
   * @param state The screen state to upload.
   * @param ready What [checkUpload] found.
   */
  private fun upload(state: S, ready: UploadCheck.Ready) {
    val circle = ready.circle
    // only people still in the circle can be tagged
    val taggableIds = circle.taggableIds()
    val shownIds = state.common.shownIds.filter { it in taggableIds }

    // Saved without the spaces and blank lines around it
    val saved = state.withCommon(state.common.copy(description = state.common.description.trim()))

    updateCommon { it.copy(isUploading = true) }
    uploadJob = viewModelScope.launch {
      try {
        val id = mediaRepo.newMediaId(circle.id)
        savedMedia = circle.id to id
        val item = createItem(id, saved, ready.authorId, circle, ready.date, shownIds)
        mediaRepo.addMedia(circle, item, fileOf(state))
        // Saved for good: cancel no longer has anything to take back
        savedMedia = null
        onUploaded()
        updateCommon { it.copy(isUploading = false) }
        _events.send(MediaEvent.Uploaded)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Uploading the media failed", e)
        savedMedia = null
        updateCommon { it.copy(isUploading = false) }
        setErrorMsg(MediaViewModelErrorMessage.UNKNOW_ERROR.messageRes)
      }
    }
  }

  /**
   * Updates the fields all upload screens share, keeping those specific to the media.
   *
   * @param transform Gives the new common fields from the current ones.
   */
  protected fun updateCommon(transform: (CommonMediaUIState) -> CommonMediaUIState) {
    _uiState.update { it.withCommon(transform(it.common)) }
  }

  /** Clears the current error message from the UI state. */
  fun clearErrorMsg() {
    updateCommon { it.copy(errorMsgRes = null) }
  }

  /**
   * Sets the error message shown by the upload screen.
   *
   * @param errorMsgRes The error to display, as a string resource.
   */
  internal fun setErrorMsg(@StringRes errorMsgRes: Int) {
    updateCommon { it.copy(errorMsgRes = errorMsgRes) }
  }

  /**
   * Updates the media description in the UI state, cut at [InputValidation.DESCRIPTION_MAX_LENGTH]
   * and without control characters other than line breaks.
   *
   * @param description The new media description.
   */
  fun setDescription(description: String) {
    updateCommon { it.copy(description = InputValidation.DESCRIPTION.clean(description)) }
  }

  /**
   * Updates the media date in the UI state.
   *
   * @param date The date associated with the media.
   */
  fun setDate(date: MemoryDate?) {
    updateCommon { it.copy(date = date) }
  }

  /**
   * Updates the place search typed by the user; [searchPlacesAsTyped] looks it up once they stop
   * typing. Any place picked before is dropped: the text no longer names it.
   *
   * @param query The text typed in the place field, cut at
   *   [InputValidation.PLACE_QUERY_MAX_LENGTH].
   */
  fun setPlaceQuery(query: String) {
    val cleaned = InputValidation.PLACE_QUERY.clean(query)
    // Nothing changed: no new search will come, so it mustn't look like one is running
    if (cleaned == _uiState.value.common.location.query) return
    updateCommon {
      val location = MediaLocationUIState(query = cleaned, suggestions = it.location.suggestions)
      val willSearch = location.isLongEnoughToSearch
      it.copy(
          location =
              location.copy(
                  isSearching = willSearch,
                  suggestions = if (willSearch) location.suggestions else emptyList(),
              )
      )
    }
    placeQueries.tryEmit(cleaned)
  }

  /**
   * Searches the place text again after its search failed, e.g. once the network is back: the text
   * didn't change, so [setPlaceQuery] doesn't search it again.
   */
  fun retryPlaceSearch() {
    val location = _uiState.value.common.location
    if (!location.searchFailed) return
    updateCommon { it.copy(location = it.location.copy(isSearching = true, searchFailed = false)) }
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
        .filter { MediaLocationUIState(query = it).isLongEnoughToSearch }
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
    val current = _uiState.value.common.location
    if (current.query != query || current.pickedPlace != null) return
    result
        .onSuccess { places ->
          updateCommon {
            it.copy(location = it.location.copy(suggestions = places, isSearching = false))
          }
        }
        // Shown inline through searchFailed; errorMsgRes is left to the upload and the circle
        .onFailure {
          updateCommon {
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
   * Picks one of the suggested real places as where the media was made.
   *
   * @param place One of [MediaLocationUIState.suggestions].
   */
  fun selectPlace(place: GeoLocation) {
    if (place.lat == null || place.lng == null) return // not a point on the map
    updateCommon {
      it.copy(
          location = MediaLocationUIState(query = place.placeName.orEmpty(), pickedPlace = place)
      )
    }
  }

  /**
   * Picks one of the circle's saved places as where the media was made, or drops it if it was
   * already picked.
   *
   * @param placeId One of the keys of [placeOptions].
   */
  fun selectPlace(placeId: String) {
    if (_uiState.value.common.location.selectedPlaceId == placeId) {
      setPlaceQuery("")
      return
    }
    val place = _careCircle.value?.pickablePlaces()?.get(placeId) ?: return
    selectPlace(place.location.copy(placeName = place.name))
    updateCommon { it.copy(location = it.location.copy(selectedPlaceId = placeId)) }
  }

  /**
   * Shows a person who was just added to the care circle and tags them in the media, since they
   * were added while uploading it. Call it once the repository has saved [person] with its id. This
   * only updates the screen: the repository's addPerson is what writes them to Firestore.
   *
   * If the circle is still loading, [person] is kept and added to it once it arrives.
   *
   * @param person The saved person, with the id the repository generated.
   * @return A [PersonNotTaggedException], with the message the dialog can show, if [person] has no
   *   id or the circle could not be loaded.
   */
  fun onPersonAdded(person: Person): Result<Unit> {
    if (person.id.isBlank()) {
      return Result.failure(PersonNotTaggedException(MediaViewModelErrorMessage.PERSON_WITHOUT_ID))
    }
    if (circleLoadFailed) {
      return Result.failure(PersonNotTaggedException(MediaViewModelErrorMessage.CANNOT_TAG_PERSON))
    }
    if (_careCircle.value == null) pendingPeople[person.id] = person
    else
        _careCircle.update { circle ->
          circle?.copy(people = circle.people + (person.id to person))
        }
    updateCommon { it.copy(shownIds = it.shownIds + person.id) }
    return Result.success(Unit)
  }

  /**
   * Adds a person to the tagged people, or removes them if already tagged.
   *
   * @param id The ID of the person to select or deselect.
   */
  fun toggleShownId(id: String) {
    updateCommon { state ->
      val updated = if (id in state.shownIds) state.shownIds - id else state.shownIds + id
      state.copy(shownIds = updated)
    }
  }

  fun onAddPerson() {
    updateCommon { it.copy(isAddPersonOpen = true) }
  }

  fun dismissAddPerson() {
    if (_uiState.value.common.isAddingPerson) return
    updateCommon {
      it.copy(
          isAddPersonOpen = false,
          personFirstName = "",
          personLastName = "",
          personNickname = "",
          isAddPersonSubmitted = false,
      )
    }
  }

  /** Updates the first name typed in the add-person dialog, cut at the names' maximum length. */
  fun setPersonFirstName(firstName: String) {
    updateCommon { it.copy(personFirstName = InputValidation.NAME.clean(firstName)) }
  }

  /** Updates the surname typed in the add-person dialog, cut at the names' maximum length. */
  fun setPersonLastName(lastName: String) {
    updateCommon { it.copy(personLastName = InputValidation.NAME.clean(lastName)) }
  }

  /** Updates the nickname typed in the add-person dialog, cut at the names' maximum length. */
  fun setPersonNickname(nickname: String) {
    updateCommon { it.copy(personNickname = InputValidation.NAME.clean(nickname)) }
  }

  /** Saves a person in the care circle and makes them available for media tagging. */
  fun addPerson() {
    val circle =
        _careCircle.value
            ?: run {
              setErrorMsg(MediaViewModelErrorMessage.STILL_LOADING.messageRes)
              return
            }
    val state = _uiState.value.common
    if (state.isAddingPerson) return
    if (state.personFirstName.isBlank()) {
      setErrorMsg(MediaViewModelErrorMessage.FIRST_NAME_REQUIRED.messageRes)
      return
    }
    if (!state.arePersonNamesValid) {
      updateCommon { it.copy(isAddPersonSubmitted = true) }
      setErrorMsg(MediaViewModelErrorMessage.INVALID_PERSON_NAME.messageRes)
      return
    }
    updateCommon { it.copy(isAddingPerson = true) }
    viewModelScope.launch {
      try {
        circleRepo
            .addPerson(
                circle.id,
                Person(
                    firstName = state.personFirstName.trim(),
                    lastName = state.personLastName.trim(),
                    nickname = state.personNickname.trim(),
                ),
            )
            .onSuccess { person ->
              _careCircle.update { it?.copy(people = it.people + (person.id to person)) }
              updateCommon {
                it.copy(
                    isAddPersonOpen = false,
                    isAddingPerson = false,
                    personFirstName = "",
                    personLastName = "",
                    personNickname = "",
                    isAddPersonSubmitted = false,
                    shownIds = it.shownIds + person.id,
                )
              }
            }
            .onFailure {
              Log.e(TAG, "Adding the person failed", it)
              setErrorMsg(MediaViewModelErrorMessage.COULD_NOT_ADD_PERSON.messageRes)
            }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Adding the person failed", e)
        setErrorMsg(MediaViewModelErrorMessage.COULD_NOT_ADD_PERSON.messageRes)
      } finally {
        updateCommon { it.copy(isAddingPerson = false) }
      }
    }
  }

  private companion object {
    const val TAG = "MediaViewModel"

    /** How long the user must stop typing before their place search is sent. */
    val PLACE_SEARCH_DEBOUNCE = 300.milliseconds
  }
}
