// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
// Modified by MeiliV, with assistance from OpenAI Codex.
// Modified by LaMaier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.upload.pictures

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.ui.caregiver.DisplayBoxWithIconText
import com.github.se.amber.ui.caregiver.DisplayCaregiverButton
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.caregiver.DisplayTextField
import com.github.se.amber.ui.caregiver.upload.media.AddPersonDialog
import com.github.se.amber.ui.caregiver.upload.media.DisplayLocation
import com.github.se.amber.ui.caregiver.upload.media.DisplayScrollableDatePicker
import com.github.se.amber.ui.caregiver.upload.media.DisplayWhoIsInvolved
import com.github.se.amber.ui.caregiver.upload.media.MediaEvent
import com.github.se.amber.ui.caregiver.upload.pictures.UploadPictureScreenTestTags.SELECT_DATE
import com.github.se.amber.ui.caregiver.upload.pictures.UploadPictureScreenTestTags.UPLOAD_BUTTON
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_WIDTH_ENORMOUS
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.MAX_CHAR_DESCRIPTION
import com.github.se.amber.ui.theme.MAX_LINE_DESCRIPTION
import com.github.se.amber.ui.theme.TEXT_FIELD_BIG
import com.github.se.amber.ui.utils.asString
import java.time.LocalDate

/** Test tags used by the caregiver Upload Picture components. */
object UploadPictureScreenTestTags {
  const val TITLE = "upload_picture_title"
  const val TAKE_PICTURE = "upload_picture_take"
  const val SELECT_PICTURE = "upload_picture_select"
  const val PICTURE_SELECTED = "upload_picture_selected"

  const val SELECT_DATE = "upload_picture_selected_date"
  const val UPLOAD_BUTTON = "upload_picture_button"
}

/**
 * Displays the picture upload screen.
 *
 * @param viewModel The view model that manages the screen state and actions.
 * @param onUpload The callback invoked after the upload action is requested.
 * @param goBack The callback invoked when the user cancels the upload.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadPictureScreen(
    viewModel: UploadPictureViewModel = viewModel(),
    onUpload: () -> Unit = {},
    goBack: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val options by viewModel.involvedOptions.collectAsStateWithLifecycle()
  val placeOptions by viewModel.placeOptions.collectAsStateWithLifecycle()
  val errorMsgRes = uiState.common.errorMsgRes
  val context = LocalContext.current
  // Configuration-aware resources, so the texts and the toast follow the app language
  val resources = LocalResources.current

  val galleryLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.selectPicture(it.toString()) }
      }

  LaunchedEffect(errorMsgRes) {
    if (errorMsgRes != null) {
      Toast.makeText(context, resources.getString(errorMsgRes), Toast.LENGTH_SHORT).show()
      viewModel.clearErrorMsg()
    }
  }

  // The upload is over: leave the screen only once it really succeeded
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
                    Modifier.testTag(UploadPictureScreenTestTags.TITLE),
                    stringResource(R.string.uploadPictureTitle),
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
          // Display the "Take a new Picture with the camera"
          DisplayBoxWithIconText(
              modifier = Modifier.testTag(UploadPictureScreenTestTags.TAKE_PICTURE),
              icon = Icons.Outlined.PhotoCamera,
              contentDescription = stringResource(R.string.uploadPictureIcon),
              text = stringResource(R.string.uploadPictureTake),
              onClick = {},
              // TODO: implement the Take a Picture function,
              //  once the Backend as been set up for it - M2
          )

          // Display the "Choose a picture from the galeri"
          DisplayBoxWithIconText(
              modifier = Modifier.testTag(UploadPictureScreenTestTags.SELECT_PICTURE),
              icon = Icons.Outlined.Photo,
              contentDescription = stringResource(R.string.uploadPictureIcon),
              text = stringResource(R.string.uploadPictureChoose),
              onClick = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
          )

          if (uiState.pictureFile != null) {
            Text(
                stringResource(R.string.uploadPictureSelected),
                modifier = Modifier.testTag(UploadPictureScreenTestTags.PICTURE_SELECTED),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
          }

          // Display the Description field
          DisplayTextField(
              modifier = Modifier.height(TEXT_FIELD_BIG),
              value = uiState.common.description,
              onValueChange = { viewModel.setDescription(it) },
              label = stringResource(R.string.uploadPictureDescriptionLabel),
              placeholder = stringResource(R.string.uploadPictureDescriptionPlaceholder),
              emptyErrorMessage = stringResource(R.string.uploadPictureDescriptionEmpty),
              maxLines = MAX_LINE_DESCRIPTION,
              maxChar = MAX_CHAR_DESCRIPTION,
          )

          DisplayWhoIsInvolved(
              options = options.mapValues { (_, label) -> label.asString(resources) },
              shownIds = uiState.common.shownIds,
              toggleShownId = viewModel::toggleShownId,
              onAddPerson = viewModel::onAddPerson,
          )

          DisplayLocation(
              savedPlaceNamesById = placeOptions,
              state = uiState.common.location,
              onSavedPlaceClick = viewModel::selectPlace,
              onQueryChange = viewModel::setPlaceQuery,
              onSuggestionClick = viewModel::selectPlace,
              onRetrySearch = viewModel::retryPlaceSearch,
          )

          DisplayScrollableDatePicker(
              modifier = Modifier.testTag(SELECT_DATE),
              initialDate = uiState.common.date ?: MemoryDate(LocalDate.now().year),
              onDateSelected = viewModel::setDate,
          )
        }
      },
      // Pinned below the form so it stays reachable however long the form grows
      bottomBar = {
        DisplayCaregiverButton(
            onClick = { viewModel.uploadMedia() },
            modifier =
                Modifier.navigationBarsPadding()
                    .padding(CAREGIVER_PADDING_MEDIUM)
                    .testTag(UPLOAD_BUTTON),
            enabled = uiState.canUpload,
            text =
                stringResource(
                    if (uiState.common.isUploading) R.string.uploadPictureUploading
                    else R.string.uploadPictureUpload
                ),
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

/** Preview of [UploadPictureScreen] using the Amber application theme. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun UploadPictureScreenPreview() {

  AmberAppTheme(dynamicColor = false) { UploadPictureScreen() }
}
