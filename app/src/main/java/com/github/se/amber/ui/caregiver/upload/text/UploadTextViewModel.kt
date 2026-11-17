// Written by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.caregiver.upload.text

import androidx.lifecycle.ViewModel
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.ui.caregiver.upload.media.CommonMediaUIState
import com.github.se.amber.ui.caregiver.upload.media.MediaEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * UI state of the text upload screen.
 *
 * @property title the story title typed by the caregiver. This is [MediaItem.Text.title].
 * @property text the story body typed in the description box. This is [MediaItem.Text.text].
 * @property common the date, place and people shared with the other upload screens. A text story
 *   keeps [CommonMediaUIState.description] empty.
 */
data class UploadTextUIState(
    val title: String = "",
    val text: String = "",
    val common: CommonMediaUIState = CommonMediaUIState(),
) {
  /** Whether the story has a title and a body, so the upload button can be pressed. */
  val canUpload: Boolean
    get() = title.isNotBlank() && text.isNotBlank()
}

/**
 * Holds the text upload screen state.
 *
 * The functions name the actions the screen can take. A body that only has a `//Todo:` comment is
 * not implemented yet. Leaving the screen stays in the composable: this class does not navigate.
 */
class UploadTextViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(UploadTextUIState())
  val uiState: StateFlow<UploadTextUIState> = _uiState.asStateFlow()

  private val _events = Channel<MediaEvent>(Channel.BUFFERED)
  val events: Flow<MediaEvent> = _events.receiveAsFlow()

  /** Stores [title] as the story title. */
  fun setTitle(title: String) {
    // Todo: save the title in the UI state
  }

  /**
   * The description saved on a text story.
   *
   * A text story has no shared description: [MediaItem.description] stays empty. The words typed in
   * the description box are [UploadTextUIState.text].
   */
  fun mediaDescription(): String = ""

  /** Stores [text] as the story body from the description box. */
  fun setText(text: String) {
    // Todo: save the story body in MediaItem.Text.text
  }

  /** Stores [date] as the day the story happened. */
  fun setDate(date: MemoryDate?) {
    // Todo: save the date in the shared media state
  }

  /** Stores [query] as the place being searched. */
  fun setPlaceQuery(query: String) {
    // Todo: save the place query and search for a location
  }

  /** Opens the dialog for adding a person to the story. */
  fun onAddPerson() {
    // Todo: open the add-person dialog
  }

  /** Closes the add-person dialog without saving. */
  fun dismissAddPerson() {
    // Todo: close the add-person dialog
  }

  /** Stores [firstName] in the add-person dialog. */
  fun setPersonFirstName(firstName: String) {
    // Todo: save the first name typed in the dialog
  }

  /** Stores [lastName] in the add-person dialog. */
  fun setPersonLastName(lastName: String) {
    // Todo: save the last name typed in the dialog
  }

  /** Stores [nickname] in the add-person dialog. */
  fun setPersonNickname(nickname: String) {
    // Todo: save the nickname typed in the dialog
  }

  /** Saves the person typed in the add-person dialog. */
  fun addPerson() {
    // Todo: add the typed person to the story
  }

  /** Clears the error currently shown on the screen. */
  fun clearErrorMsg() {
    // Todo: clear the error shown on the screen
  }

  /** Uploads the story. The screen navigates only after [MediaEvent.Uploaded]. */
  fun uploadText() {
    // Todo: save the text story, then send MediaEvent.Uploaded
  }
}
