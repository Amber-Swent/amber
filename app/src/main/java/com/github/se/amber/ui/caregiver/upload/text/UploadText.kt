// Written by Yuhan YANG, with assistance from Cursor.
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.caregiver.upload.text

import android.content.res.Configuration
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.ui.caregiver.DisplayCaregiverButton
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.caregiver.DisplayTextField
import com.github.se.amber.ui.caregiver.upload.media.AddPersonDialog
import com.github.se.amber.ui.caregiver.upload.media.DisplayLocation
import com.github.se.amber.ui.caregiver.upload.media.DisplayScrollableDatePicker
import com.github.se.amber.ui.caregiver.upload.media.DisplayWhoIsInvolved
import com.github.se.amber.ui.caregiver.upload.media.MediaEvent
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_WIDTH_ENORMOUS
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.MAX_CHAR_DESCRIPTION
import com.github.se.amber.ui.theme.MAX_CHAR_NAME
import com.github.se.amber.ui.theme.MAX_LINE_DESCRIPTION
import com.github.se.amber.ui.theme.TEXT_FIELD_BIG
import com.github.se.amber.ui.theme.TEXT_FIELD_DEFAULT
import com.github.se.amber.ui.utils.asString
import java.time.LocalDate

/** Test tags used by the caregiver Upload Text screen. */
object UploadTextTestTags {
  const val TITLE = "upload_text_title"
  const val STORY_TITLE = "upload_text_story_title"
  const val DESCRIPTION = "upload_text_description"
  const val SELECTED_DATE = "upload_text_selected_date"
  const val UPLOAD_BUTTON = "upload_text_upload_button"
}

/**
 * Displays the text upload screen.
 *
 * The form matches the picture upload screen, with a title field in place of the camera and gallery
 * buttons. The title box is [MediaItem.Text.title], which this screen does not save yet. The
 * description box keeps that label, but its text is the story body, [MediaItem.Text.text]. A text
 * story has no shared caption, so [MediaItem.description] stays empty. What is typed is sent to
 * [viewModel]. [onUpload] and [goBack] only move between screens.
 *
 * @param viewModel the view model that owns the story being typed.
 * @param onUpload called after the view model reports that the story was uploaded.
 * @param goBack called when the caregiver cancels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadTextScreen(
    viewModel: UploadTextViewModel = viewModel(),
    onUpload: () -> Unit = {},
    goBack: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val errorMsgRes = uiState.common.errorMsgRes
  val context = LocalContext.current
  val resources = LocalResources.current

  LaunchedEffect(errorMsgRes) {
    if (errorMsgRes != null) {
      Toast.makeText(context, resources.getString(errorMsgRes), Toast.LENGTH_SHORT).show()
      viewModel.clearErrorMsg()
    }
  }

  val currentOnUpload by rememberUpdatedState(onUpload)
  LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
      when (event) {
        MediaEvent.Uploaded -> currentOnUpload()
      }
    }
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Column(modifier = Modifier.fillMaxWidth().padding(CAREGIVER_PADDING_SMALL)) {
                TopNavigationMenu(Modifier, TopNavigationAction.CANCEL, goBack)
                DisplayCaregiverTitle(
                    Modifier.testTag(UploadTextTestTags.TITLE),
                    stringResource(R.string.uploadTextTitle),
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
          Column(Modifier.testTag(UploadTextTestTags.STORY_TITLE)) {
            DisplayTextField(
                modifier = Modifier.height(TEXT_FIELD_DEFAULT),
                value = uiState.title,
                onValueChange = viewModel::setTitle,
                label = stringResource(R.string.uploadTextTitleLabel),
                placeholder = stringResource(R.string.uploadTextTitlePlaceholder),
                emptyErrorMessage = stringResource(R.string.uploadTextTitleEmpty),
                maxChar = MAX_CHAR_NAME,
                singleLine = true,
            )
          }

          // Labeled "Description" like the audio screen. The value is the story body (`text`),
          // not MediaItem.description, which stays empty for a text story.
          Column(Modifier.testTag(UploadTextTestTags.DESCRIPTION)) {
            DisplayTextField(
                modifier = Modifier.height(TEXT_FIELD_BIG),
                value = uiState.text,
                onValueChange = viewModel::setText,
                label = stringResource(R.string.uploadTextDescriptionLabel),
                placeholder = stringResource(R.string.uploadTextDescriptionPlaceholder),
                emptyErrorMessage = stringResource(R.string.uploadTextDescriptionEmpty),
                maxLines = MAX_LINE_DESCRIPTION,
                maxChar = MAX_CHAR_DESCRIPTION,
            )
          }

          DisplayWhoIsInvolved(onAddPerson = viewModel::onAddPerson)

          DisplayLocation(
              state = uiState.common.location,
              onQueryChange = viewModel::setPlaceQuery,
          )

          DisplayScrollableDatePicker(
              modifier = Modifier.testTag(UploadTextTestTags.SELECTED_DATE),
              initialDate = uiState.common.date ?: MemoryDate(LocalDate.now().year),
              onDateSelected = viewModel::setDate,
          )
        }
      },
      bottomBar = {
        DisplayCaregiverButton(
            onClick = viewModel::uploadMedia,
            modifier =
                Modifier.navigationBarsPadding()
                    .padding(CAREGIVER_PADDING_MEDIUM)
                    .testTag(UploadTextTestTags.UPLOAD_BUTTON),
            enabled = uiState.canUpload,
            text = stringResource(R.string.uploadTextUpload),
            widthPercentage = CAREGIVER_BUTTON_WIDTH_ENORMOUS,
        )
      },
  )

  AddPersonDialog(
      isOpen = uiState.common.isAddPersonOpen,
      isAddingPerson = uiState.common.isAddingPerson,
      canAddPerson = uiState.common.canAddPerson,
      firstName = uiState.common.personFirstName,
      lastName = uiState.common.personLastName,
      nickname = uiState.common.personNickname,
      firstNameError = uiState.common.personFirstNameError?.asString(resources),
      lastNameError = uiState.common.personLastNameError?.asString(resources),
      nicknameError = uiState.common.personNicknameError?.asString(resources),
      onFirstNameChange = viewModel::setPersonFirstName,
      onLastNameChange = viewModel::setPersonLastName,
      onNicknameChange = viewModel::setPersonNickname,
      onDismiss = viewModel::dismissAddPerson,
      onSave = viewModel::addPerson,
  )
}

/** Preview of [UploadTextScreen] in light mode. */
@Preview(showBackground = true, showSystemUi = true, name = "Upload text light")
@Composable
internal fun UploadTextScreenPreview(
    viewModel: UploadTextViewModel = remember { UploadTextViewModel() },
) {
  AmberAppTheme(dynamicColor = false, darkTheme = false) { UploadTextScreen(viewModel = viewModel) }
}

/** Preview of [UploadTextScreen] in dark mode. */
@Preview(
    showBackground = true,
    showSystemUi = true,
    name = "Upload text dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
internal fun UploadTextScreenDarkPreview(
    viewModel: UploadTextViewModel = remember { UploadTextViewModel() },
) {
  AmberAppTheme(dynamicColor = false, darkTheme = true) { UploadTextScreen(viewModel = viewModel) }
}
