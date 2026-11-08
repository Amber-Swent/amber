// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.caregiver.upload.audios

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.model.media.AudioRecordingState
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.ui.caregiver.DisplayBoxWithIconText
import com.github.se.amber.ui.caregiver.DisplayCaregiverButton
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.caregiver.DisplayTextField
import com.github.se.amber.ui.caregiver.upload.audios.UploadAudioScreenTestTags.SELECT_DATE
import com.github.se.amber.ui.caregiver.upload.audios.UploadAudioScreenTestTags.UPLOAD_BUTTON
import com.github.se.amber.ui.caregiver.upload.media.AddPersonDialog
import com.github.se.amber.ui.caregiver.upload.media.DisplayScrollableDatePicker
import com.github.se.amber.ui.caregiver.upload.media.DisplayWhoIsInvolved
import com.github.se.amber.ui.media.audio.DisplayAudioRecorder
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_WIDTH_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.MAX_CHAR_DESCRIPTION
import com.github.se.amber.ui.theme.MAX_LINE_DESCRIPTION
import com.github.se.amber.ui.theme.TEXT_FIELD_BIG
import java.time.LocalDate

/** Test tags used by the caregiver Upload Audio components. */
object UploadAudioScreenTestTags {
  const val TITLE = "upload_audio_title"
  const val RECORD_AUDIO = "upload_audio_record"
  const val RECORDING = "upload_audio_recording"
  const val AUDIO_RECORDED = "upload_audio_recorded"
  const val SELECT_DATE = "upload_audio_selected_date"
  const val UPLOAD_BUTTON = "upload_audio_button"
}

/**
 * Displays the Audio upload screen.
 *
 * @param viewModel The view model that manages the screen state and actions.
 * @param onUpload The callback invoked after the upload action is requested.
 * @param goBack The callback invoked when the user cancels the upload.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadAudioScreen(
    viewModel: UploadAudioViewModel = viewModel(),
    onUpload: () -> Unit = {},
    goBack: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val options by viewModel.involvedOptions.collectAsStateWithLifecycle()
  //    val placeOptions by viewModel.placeOptions.collectAsStateWithLifecycle()
  val errorMessage = uiState.errorMsg
  val context = LocalContext.current

  LaunchedEffect(errorMessage) {
    if (errorMessage != null) {
      Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
      viewModel.clearErrorMsg()
    }
  }

  // FIXME : will be unExtract once the ViewModel is implemented.
  //  for the moment, I let it to avoid forgetting

  //      // The upload is over: leave the screen only once it really succeeded
  //      val currentOnUpload by rememberUpdatedState(onUpload)
  //      LaunchedEffect(Unit) {
  //          viewModel.events.collect { event ->
  //              when (event) {
  //                  UploadAudioEvent.Uploaded -> currentOnUpload()
  //              }
  //          }
  //      }

  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Column(modifier = Modifier.fillMaxWidth().padding(CAREGIVER_PADDING_SMALL)) {
                TopNavigationMenu(Modifier, TopNavigationAction.CANCEL, goBack)
                DisplayCaregiverTitle(
                    Modifier.testTag(UploadAudioScreenTestTags.TITLE),
                    "Upload Audio",
                )
              }
            }
        )
      },
      content = { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
                    .padding(CAREGIVER_PADDING_MEDIUM)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
            verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL),
        ) {
          // Record button, then the recording in progress, then the recorded audio
          when (uiState.recordingState) {
            AudioRecordingState.RECORDING ->
                DisplayAudioRecorder(
                    modifier = Modifier.testTag(UploadAudioScreenTestTags.RECORDING),
                    isRecording = true,
                    waveform = uiState.waveform,
                    onDelete = viewModel::deleteRecording,
                    onStop = viewModel::stopRecording, // must update uiState.audioFile
                )
            AudioRecordingState.RECORDED ->
                DisplayAudioRecorder(
                    modifier = Modifier.testTag(UploadAudioScreenTestTags.AUDIO_RECORDED),
                    waveform = uiState.waveform,
                    onDelete = viewModel::deleteRecording,
                )
            AudioRecordingState.IDLE ->
                DisplayBoxWithIconText(
                    modifier = Modifier.testTag(UploadAudioScreenTestTags.RECORD_AUDIO),
                    icon = Icons.Outlined.Mic,
                    contentDescription = "Micro",
                    text = "Click to record an Audio",
                    onClick = viewModel::startRecording,
                )
          }

          // Display the Description field
          DisplayTextField(
              modifier = Modifier.height(TEXT_FIELD_BIG),
              value = uiState.description,
              onValueChange = { viewModel.setDescription(it) },
              label = "Description",
              placeholder = "Enter a description of the Audio",
              emptyErrorMessage = "Description cannot be empty",
              maxLines = MAX_LINE_DESCRIPTION,
              maxChar = MAX_CHAR_DESCRIPTION,
          )

          DisplayWhoIsInvolved(
              options = options,
              shownIds = uiState.shownIds,
              toggleShownId = viewModel::toggleShownId,
              onAddPerson = viewModel::onAddPerson,
          )

          // FIXME: will be unExtract once the global Location logic is implemented.
          //  This will come in another PR.
          //  I don't want to create a new UploadAudioLocation file that duplicate the
          //  UploadPictureLocation.

          //          DisplayLocation(
          //              savedPlaceNamesById = placeOptions,
          //              state = uiState.location,
          //              onSavedPlaceClick = viewModel::selectPlace,
          //              onQueryChange = viewModel::setPlaceQuery,
          //              onSuggestionClick = viewModel::selectPlace,
          //              onRetrySearch = viewModel::retryPlaceSearch,
          //          )

          DisplayScrollableDatePicker(
              modifier = Modifier.testTag(SELECT_DATE),
              initialDate = uiState.date ?: MemoryDate(LocalDate.now().year),
              onDateSelected = viewModel::setDate,
          )
        }
      },
      // Pinned below the form so it stays reachable however long the form grows
      bottomBar = {
        DisplayCaregiverButton(
            onClick = { viewModel.uploadAudio() },
            modifier =
                Modifier.navigationBarsPadding()
                    .padding(CAREGIVER_PADDING_MEDIUM)
                    .testTag(UPLOAD_BUTTON),
            enabled = uiState.canUpload,
            text = if (uiState.isUploading) "Uploading…" else "Upload Audio",
            widthPercentage = CAREGIVER_BUTTON_WIDTH_MEDIUM,
        )
      },
  )

  AddPersonDialog(
      isOpen = uiState.isAddPersonOpen,
      isAddingPerson = uiState.isAddingPerson,
      firstName = uiState.personFirstName,
      lastName = uiState.personLastName,
      nickname = uiState.personNickname,
      onFirstNameChange = viewModel::setPersonFirstName,
      onLastNameChange = viewModel::setPersonLastName,
      onNicknameChange = viewModel::setPersonNickname,
      onDismiss = viewModel::dismissAddPerson,
      onSave = viewModel::addPerson,
  )
}

/** Preview of [UploadAudioScreen] using the Amber application theme. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
internal fun UploadAudioScreenPreview() {

  AmberAppTheme(dynamicColor = false) { UploadAudioScreen() }
}
