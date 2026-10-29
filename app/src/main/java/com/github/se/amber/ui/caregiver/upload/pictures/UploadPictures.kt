/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 * Modified with assistance from OpenAI Codex.
 */
package com.github.se.amber.ui.caregiver.upload.picture

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.ui.caregiver.DisplayBoxWithIconText
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.caregiver.DisplayTextField
import com.github.se.amber.ui.caregiver.upload.DisplayScrollableDatePicker
import com.github.se.amber.ui.caregiver.upload.picture.UploadPictureScreenTestTags.NO_PERSON_INVOLVED
import com.github.se.amber.ui.caregiver.upload.picture.UploadPictureScreenTestTags.PERSON_INVOLVED
import com.github.se.amber.ui.caregiver.upload.picture.UploadPictureScreenTestTags.SELECT_DATE
import com.github.se.amber.ui.caregiver.upload.picture.UploadPictureScreenTestTags.SELECT_PERSON_INVOLVED
import com.github.se.amber.ui.caregiver.upload.picture.UploadPictureScreenTestTags.UPLOAD_BUTTON
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.BORDER_THICKNESS
import com.github.se.amber.ui.theme.MAX_CHAR_DESCRIPTION
import com.github.se.amber.ui.theme.MAX_LINE_DESCRIPTION
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.ROUND_CORNER
import com.github.se.amber.ui.theme.TEXT_FIELD_BIG
import com.github.se.amber.ui.theme.lightGray
import com.github.se.amber.ui.theme.lightOrange
import java.time.LocalDate

/** Test tags used by the caregiver Upload Picture components. */
object UploadPictureScreenTestTags {
  const val TITLE = "upload_picture_title"
  const val TAKE_PICTURE = "upload_picture_take"
  const val SELECT_PICTURE = "upload_picture_select"
  const val PICTURE_SELECTED = "upload_picture_selected"
  const val SELECT_PERSON_INVOLVED = "upload_picture_selected_person_involved"
  const val NO_PERSON_INVOLVED = "upload_picture_no_person_involved"
  const val PERSON_INVOLVED = "upload_picture_person_involved"

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
  val uiState by viewModel.uiState.collectAsState()
  val options by viewModel.involvedOptions.collectAsState()
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
        Column(modifier = Modifier.fillMaxWidth().padding(PADDING_SMALL)) {
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
                    .padding(paddingValues)
                    .padding(PADDING_MEDIUM)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
            verticalArrangement = Arrangement.spacedBy(PADDING_SMALL),
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
          )

          DisplayScrollableDatePicker(
              modifier = Modifier.testTag(SELECT_DATE),
              initialDate = uiState.date ?: MemoryDate(LocalDate.now().year),
              onDateSelected = viewModel::setDate,
          )

          Spacer(modifier = Modifier.height(PADDING_MEDIUM))

          Button(
              onClick = { viewModel.uploadPicture() },
              modifier = Modifier.fillMaxWidth().testTag(UPLOAD_BUTTON),
              colors = ButtonDefaults.buttonColors(containerColor = lightOrange),
              enabled = uiState.canUpload,
          ) {
            Text(
                text = if (uiState.isUploading) "Uploading…" else "Upload Picture",
            )
          }
        }
      },
  )
}

/**
 * Displays selectable chips for people who are involved in a picture.
 *
 * @param options The available person IDs and labels.
 * @param shownIds The IDs of the people currently selected.
 * @param toggleShownId The callback used to select or deselect a person.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisplayWhoIsInvolved(
    options: Map<String, String>, // id -> label
    shownIds: Set<String>,
    toggleShownId: (String) -> Unit,
) {
  Column {
    Text("Who is involved - optional", color = Color.Black)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(PADDING_SMALL),
        modifier = Modifier.fillMaxWidth().testTag(SELECT_PERSON_INVOLVED),
    ) {
      if (options.isEmpty()) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .testTag(NO_PERSON_INVOLVED)
                    .border(
                        width = BORDER_THICKNESS,
                        color = lightGray,
                        shape = RoundedCornerShape(ROUND_CORNER),
                    )
                    .clip(RoundedCornerShape(ROUND_CORNER))
                    .padding(PADDING_MEDIUM),
            contentAlignment = Alignment.Center,
        ) {
          Text(
              text = "No one is part of your care circle",
              color = Color.Black,
          )
        }
      } else {
        options.forEach { (id, label) ->
          FilterChip(
              selected = id in shownIds,
              onClick = { toggleShownId(id) },
              label = { Text(label) },
              modifier = Modifier.testTag(PERSON_INVOLVED),
          )
        }
      }
      // TODO: add a button to add other person in the care circle - M2
    }
  }
}

/** Preview of [UploadPictureScreen] using the Amber application theme. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun UploadPictureScreenPreview() {

  AmberAppTheme(dynamicColor = false) { UploadPictureScreen() }
}
