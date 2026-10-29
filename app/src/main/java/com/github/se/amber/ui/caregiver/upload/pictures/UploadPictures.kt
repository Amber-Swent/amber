/* Written by LaMaier, with assistance from
 * Claude (Anthropic) via Claude Code.
 * Modified with assistance from OpenAI Codex.
 */
package com.github.se.amber.ui.caregiver.upload.picture

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.ui.caregiver.DisplayBoxWithIconText
import com.github.se.amber.ui.caregiver.DisplayCaregiverButton
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.caregiver.DisplayTextField
import com.github.se.amber.ui.caregiver.upload.AddPersonDialog
import com.github.se.amber.ui.caregiver.upload.DisplayScrollableDatePicker
import com.github.se.amber.ui.caregiver.upload.picture.UploadPictureScreenTestTags.SELECT_DATE
import com.github.se.amber.ui.caregiver.upload.picture.UploadPictureScreenTestTags.UPLOAD_BUTTON
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.BORDER_THICKNESS
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_WIDTH_BIG
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.MAX_CHAR_DESCRIPTION
import com.github.se.amber.ui.theme.MAX_LINE_DESCRIPTION
import com.github.se.amber.ui.theme.ROUND_CORNER
import com.github.se.amber.ui.theme.TEXT_FIELD_BIG
import com.github.se.amber.ui.theme.WEIGHT_ONE

/** Test tags used by the caregiver Upload Picture components. */
object UploadPictureScreenTestTags {
  const val TITLE = "upload_picture_title"
  const val TAKE_PICTURE = "upload_picture_take"
  const val SELECT_PICTURE = "upload_picture_select"
  const val PICTURE_SELECTED = "upload_picture_selected"

  const val SELECT_DATE = "upload_picture_selected_date"
  const val UPLOAD_BUTTON = "upload_picture_button"
}

/** Tags owned by the person selector helper. */
object DisplayWhoIsInvolvedTestTags {
  const val SELECT_PERSON_INVOLVED = "upload_picture_selected_person_involved"
  const val ADD_PERSON = "upload_picture_add_person"
  const val NO_PERSON_INVOLVED = "upload_picture_no_person_involved"
  const val PERSON_INVOLVED = "upload_picture_person_involved"
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
  val errorMessage = uiState.errorMsg
  val context = LocalContext.current

  val galleryLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.selectPicture(it.toString()) }
      }

  LaunchedEffect(errorMessage) {
    if (errorMessage != null) {
      Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
      viewModel.clearErrorMsg()
    }
  }

  // The upload is over: leave the screen only once it really succeeded
  val currentOnUpload by rememberUpdatedState(onUpload)
  LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
      when (event) {
        UploadPictureEvent.Uploaded -> currentOnUpload()
      }
    }
  }

  Scaffold(
      topBar = {
        Column(modifier = Modifier.fillMaxWidth().padding(CAREGIVER_PADDING_SMALL)) {
          TopNavigationMenu(Modifier, TopNavigationAction.CANCEL, goBack)
          DisplayCaregiverTitle(
              "Upload Picture",
              Modifier.testTag(UploadPictureScreenTestTags.TITLE),
          )
        }
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
              contentDescription = "Photo",
              label = "Take a new Picture",
              onClick = {},
              // TODO: implement the Take a Picture function,
              //  once the Backend as been set up for it - M2
          )

          // Display the "Choose a picture from the galeri"
          DisplayBoxWithIconText(
              modifier = Modifier.testTag(UploadPictureScreenTestTags.SELECT_PICTURE),
              icon = Icons.Outlined.Photo,
              contentDescription = "Photo",
              label = "Click to choose an existing Picture",
              onClick = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
          )

          if (uiState.pictureFile != null) {
            Text(
                "Picture selected",
                modifier = Modifier.testTag(UploadPictureScreenTestTags.PICTURE_SELECTED),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
          }

          // Display the Description field
          DisplayTextField(
              modifier = Modifier.height(TEXT_FIELD_BIG),
              value = uiState.description,
              onValueChange = { viewModel.setDescription(it) },
              label = "Description",
              placeholder = "Enter a description of the picture",
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

          DisplayScrollableDatePicker(
              modifier = Modifier.testTag(SELECT_DATE),
              onDateSelected = { year, month, day ->
                viewModel.setDate(MemoryDate(year, month, day))
              },
          )

          DisplayCaregiverButton(
              onClick = { viewModel.uploadPicture() },
              modifier = Modifier.padding(top = CAREGIVER_PADDING_MEDIUM).testTag(UPLOAD_BUTTON),
              enabled = uiState.canUpload,
              text = if (uiState.isUploading) "Uploading…" else "Upload Picture",
              widthPercentage = CAREGIVER_BUTTON_WIDTH_BIG,
          )
        }
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

/**
 * Displays selectable chips for people who are involved in a picture. Displays button that allows
 * the caregiver to add a person to be associated to this picture: This person does not have a
 * userId, will be added to the care circle and will be an Instance of `Person`.
 *
 * @param options The available person IDs and labels.
 * @param shownIds The IDs of the people currently selected.
 * @param toggleShownId The callback used to select or deselect a person.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisplayWhoIsInvolved(
    options: Map<String, String> = emptyMap(), // id -> label
    shownIds: Set<String> = emptySet(),
    toggleShownId: (String) -> Unit = {},
    onAddPerson: () -> Unit = {},
) {
  Column {
    Text(
        "Who is involved - optional",
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.bodyMedium,
    )
    if (options.isEmpty()) {
      Row(
          modifier =
              Modifier.fillMaxWidth()
                  .height(IntrinsicSize.Min) // Takes the height of its tallest child
                  .testTag(DisplayWhoIsInvolvedTestTags.SELECT_PERSON_INVOLVED),
          horizontalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
            modifier =
                Modifier.weight(WEIGHT_ONE)
                    .testTag(DisplayWhoIsInvolvedTestTags.NO_PERSON_INVOLVED)
                    .border(
                        width = BORDER_THICKNESS,
                        color = MaterialTheme.colorScheme.tertiary,
                        shape = RoundedCornerShape(ROUND_CORNER),
                    )
                    .clip(RoundedCornerShape(ROUND_CORNER))
                    .padding(CAREGIVER_PADDING_MEDIUM),
            contentAlignment = Alignment.Center,
        ) {
          Text(
              text = "No one is part of your care circle",
              color = MaterialTheme.colorScheme.onBackground,
              style = MaterialTheme.typography.bodyMedium,
          )
        }
        // Display a button to add another person to the care circle
        AssistChip(
            onClick = { onAddPerson() },
            label = { Text("+", style = MaterialTheme.typography.labelMedium) },
            modifier = Modifier.fillMaxHeight().testTag(DisplayWhoIsInvolvedTestTags.ADD_PERSON),
            colors =
                AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    labelColor = MaterialTheme.colorScheme.onTertiary,
                ),
        )
      }
    } else {
      FlowRow(
          horizontalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL),
          modifier =
              Modifier.fillMaxWidth().testTag(DisplayWhoIsInvolvedTestTags.SELECT_PERSON_INVOLVED),
      ) {
        // Display a chip for each person associated to the photo
        options.forEach { (id, label) ->
          FilterChip(
              selected = id in shownIds,
              onClick = { toggleShownId(id) },
              label = { Text(label, style = MaterialTheme.typography.labelMedium) },
              modifier = Modifier.testTag(DisplayWhoIsInvolvedTestTags.PERSON_INVOLVED),
              colors =
                  FilterChipDefaults.filterChipColors(
                      containerColor = MaterialTheme.colorScheme.tertiary,
                      labelColor = MaterialTheme.colorScheme.onTertiary,
                      selectedContainerColor = MaterialTheme.colorScheme.secondary,
                      selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                  ),
          )
        }
        AssistChip(
            onClick = { onAddPerson() },
            label = { Text("+", style = MaterialTheme.typography.labelMedium) },
            modifier = Modifier.testTag(DisplayWhoIsInvolvedTestTags.ADD_PERSON),
            colors =
                AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    labelColor = MaterialTheme.colorScheme.onTertiary,
                ),
        )
      }
    }
  }
}

/** Preview of [UploadPictureScreen] using the Amber application theme. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun UploadPictureScreenPreview() {

  AmberAppTheme(dynamicColor = false) { UploadPictureScreen() }
}
