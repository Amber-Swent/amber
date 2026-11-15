// Written by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.caregiver.upload.text

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.github.se.amber.R
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.model.validation.InputValidation
import com.github.se.amber.ui.caregiver.DisplayCaregiverButton
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.caregiver.DisplayTextField
import com.github.se.amber.ui.caregiver.upload.media.AddPersonDialog
import com.github.se.amber.ui.caregiver.upload.media.DisplayLocation
import com.github.se.amber.ui.caregiver.upload.media.DisplayScrollableDatePicker
import com.github.se.amber.ui.caregiver.upload.media.DisplayWhoIsInvolved
import com.github.se.amber.ui.caregiver.upload.media.MediaLocationUIState
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_WIDTH_ENORMOUS
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.MAX_CHAR_DESCRIPTION
import com.github.se.amber.ui.theme.MAX_LINE_DESCRIPTION
import com.github.se.amber.ui.theme.SURFACE_MEDIUM
import com.github.se.amber.ui.theme.TEXT_FIELD_BIG
import java.time.LocalDate

/** Test tags used by the caregiver Upload Text screen. */
object UploadTextScreenTestTags {
  const val TITLE = "upload_text_title"
  const val STORY_TITLE = "upload_text_story_title"
  const val DESCRIPTION = "upload_text_description"
  const val SELECT_DATE = "upload_text_selected_date"
  const val UPLOAD_BUTTON = "upload_text_button"
}

/**
 * Displays the text upload screen.
 *
 * The form matches the picture upload screen, with a title field in place of the camera and gallery
 * buttons. What is typed stays on this screen until a view model owns it.
 *
 * @param onUpload called when the caregiver uploads a story whose title and description are filled.
 * @param goBack called when the caregiver cancels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadTextScreen(onUpload: () -> Unit = {}, goBack: () -> Unit = {}) {
  var storyTitle by rememberSaveable { mutableStateOf("") }
  var description by rememberSaveable { mutableStateOf("") }
  var location by remember { mutableStateOf(MediaLocationUIState()) }
  var isAddPersonOpen by rememberSaveable { mutableStateOf(false) }
  var personFirstName by rememberSaveable { mutableStateOf("") }
  var personLastName by rememberSaveable { mutableStateOf("") }
  var personNickname by rememberSaveable { mutableStateOf("") }
  // The take-picture row is the icon plus its vertical padding. The title field uses that height.
  val titleFieldHeight = SURFACE_MEDIUM + CAREGIVER_PADDING_MEDIUM + CAREGIVER_PADDING_MEDIUM

  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Column(modifier = Modifier.fillMaxWidth().padding(CAREGIVER_PADDING_SMALL)) {
                TopNavigationMenu(Modifier, TopNavigationAction.CANCEL, goBack)
                DisplayCaregiverTitle(
                    Modifier.testTag(UploadTextScreenTestTags.TITLE),
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
          Column(Modifier.testTag(UploadTextScreenTestTags.STORY_TITLE)) {
            DisplayTextField(
                modifier = Modifier.height(titleFieldHeight),
                value = storyTitle,
                onValueChange = { storyTitle = it },
                label = stringResource(R.string.uploadTextTitleLabel),
                placeholder = stringResource(R.string.uploadTextTitlePlaceholder),
                emptyErrorMessage = stringResource(R.string.uploadTextTitleEmpty),
                maxChar = InputValidation.NAME_MAX_LENGTH,
                singleLine = true,
            )
          }

          Column(Modifier.testTag(UploadTextScreenTestTags.DESCRIPTION)) {
            DisplayTextField(
                modifier = Modifier.height(TEXT_FIELD_BIG),
                value = description,
                onValueChange = { description = it },
                label = stringResource(R.string.uploadTextDescriptionLabel),
                placeholder = stringResource(R.string.uploadTextDescriptionPlaceholder),
                emptyErrorMessage = stringResource(R.string.uploadTextDescriptionEmpty),
                maxLines = MAX_LINE_DESCRIPTION,
                maxChar = MAX_CHAR_DESCRIPTION,
            )
          }

          DisplayWhoIsInvolved(onAddPerson = { isAddPersonOpen = true })

          DisplayLocation(
              state = location,
              onQueryChange = { location = location.copy(query = it) },
          )

          DisplayScrollableDatePicker(
              modifier = Modifier.testTag(UploadTextScreenTestTags.SELECT_DATE),
              initialDate = MemoryDate(LocalDate.now().year),
          )
        }
      },
      bottomBar = {
        DisplayCaregiverButton(
            onClick = onUpload,
            modifier =
                Modifier.navigationBarsPadding()
                    .padding(CAREGIVER_PADDING_MEDIUM)
                    .testTag(UploadTextScreenTestTags.UPLOAD_BUTTON),
            enabled = storyTitle.isNotBlank() && description.isNotBlank(),
            text = stringResource(R.string.uploadTextUpload),
            widthPercentage = CAREGIVER_BUTTON_WIDTH_ENORMOUS,
        )
      },
  )

  AddPersonDialog(
      isOpen = isAddPersonOpen,
      canAddPerson = personFirstName.isNotBlank(),
      firstName = personFirstName,
      lastName = personLastName,
      nickname = personNickname,
      onFirstNameChange = { personFirstName = it },
      onLastNameChange = { personLastName = it },
      onNicknameChange = { personNickname = it },
      onDismiss = { isAddPersonOpen = false },
      onSave = { isAddPersonOpen = false },
  )
}

/** Preview of [UploadTextScreen] using the Amber application theme. */
@Preview(showBackground = true)
@Composable
internal fun UploadTextScreenPreview() {
  AmberAppTheme(dynamicColor = false) { UploadTextScreen() }
}
