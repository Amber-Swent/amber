/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.BORDER_THICKNESS
import com.github.se.amber.ui.theme.MAX_LINE
import com.github.se.amber.ui.theme.PADDING_BIG
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.ROUND_CORNER
import com.github.se.amber.ui.theme.TEXT_FIELD
import com.github.se.amber.ui.theme.TEXT_FIELD_BIG
import com.github.se.amber.ui.theme.darkOrange
import com.github.se.amber.ui.theme.lightGray
import com.github.se.amber.ui.theme.lightOrange
import com.github.se.amber.ui.theme.orange

object UploadPictureTestTags {
  const val TITLE = "upload_picture_title"
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
  val errorMessage = uiState.errorMsg
  val context = LocalContext.current

  LaunchedEffect(errorMessage) {
    if (errorMessage != null) {
      Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
      viewModel.clearErrorMsg()
    }
  }

  // FIXME : need to verified there is a picture select
  //  Don't know how.
  //  Will have to see depending on how we implement the UploadPictureViewModel
  val isFormValid =
      uiState.title.isNotBlank() &&
          uiState.description.isNotBlank() &&
          uiState.involvedName.isNotBlank()

  Scaffold(
      topBar = {
        Column(modifier = Modifier.fillMaxWidth().padding(PADDING_SMALL)) {
          TopNavigationMenu(Modifier, TopNavigationAction.CANCEL, goBack)
          DisplayCaregiverTitle("Upload Picture", Modifier.testTag(UploadPictureTestTags.TITLE))
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
          DisplayTakePicture(Modifier, { viewModel.takePicture() })
          DisplaySelectPicture(Modifier, { viewModel.selectPicture() })

          DisplayTextField(
              value = uiState.title,
              onValueChange = { viewModel.setTitle(it) },
              label = "Title",
              placeholder = "Name the story",
              emptyErrorMessage = "Title cannot be empty",
              modifier = Modifier.height(TEXT_FIELD),
          )

          DisplayTextField(
              value = uiState.description,
              onValueChange = { viewModel.setDescription(it) },
              label = "Description",
              placeholder = "Enter a one-line description",
              emptyErrorMessage = "Description cannot be empty",
              modifier = Modifier.height(TEXT_FIELD_BIG),
          )

          DisplayTextField(
              value = uiState.involvedName,
              onValueChange = { viewModel.setInvolvedName(it) },
              label = "Who is Involved",
              placeholder = "Name all person involved",
              emptyErrorMessage = "They can't be no-one involved",
              modifier = Modifier.height(TEXT_FIELD),
          )

          // TODO : use DisplayScrollableDatePicker() once merge and update the uiState accordingly

          Spacer(modifier = Modifier.height(PADDING_MEDIUM))

          Button(
              onClick = {
                viewModel.uploadPicture()
                onUpload()
              },
              modifier = Modifier.fillMaxWidth(),
              colors = ButtonDefaults.buttonColors(containerColor = lightOrange),
              enabled = isFormValid,
          ) {
            Text("Upload Picture")
          }
        }
      },
  )
}

/**
 * Displays a text field with optional validation feedback.
 *
 * @param value The current text field value.
 * @param onValueChange The callback invoked when the text changes.
 * @param label The label displayed for the text field.
 * @param modifier The modifier applied to the text field.
 * @param placeholder The optional placeholder displayed when the field is empty.
 * @param emptyErrorMessage The optional error message displayed when the field is empty after
 *   focus.
 * @param minLines The minimum number of lines displayed by the text field.
 * @param maxLines The maximum number of lines displayed by the text field.
 */
@Composable
private fun DisplayTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    emptyErrorMessage: String? = null,
    minLines: Int = 1,
    maxLines: Int = MAX_LINE,
) {
  var hasBeenFocused by remember { mutableStateOf(false) }

  OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      label = { Text(text = label, color = Color.Black) },
      placeholder = placeholder?.let { { Text(it) } },
      modifier =
          modifier.fillMaxWidth().onFocusChanged { focusState ->
            if (focusState.isFocused) hasBeenFocused = true
          },
      shape = RoundedCornerShape(ROUND_CORNER),
      colors =
          OutlinedTextFieldDefaults.colors(
              focusedBorderColor = orange,
              unfocusedBorderColor = lightGray,
          ),
      minLines = minLines,
      maxLines = maxLines,
  )
  // TODO: should show message when reach max-line
  // TODO: should grow description textField size when lines take more space that the locate one
  // TODO: should keep the area focus on where the user click

  if (emptyErrorMessage != null && value.isBlank() && hasBeenFocused) {
    Text(
        text = emptyErrorMessage,
        color = darkOrange,
        style = MaterialTheme.typography.bodySmall,
    )
  }
}

/**
 * Displays the action for taking a new picture.
 *
 * @param modifier The modifier applied to the action container.
 * @param takePicture The callback invoked when the action is selected.
 */
@Composable
fun DisplayTakePicture(modifier: Modifier, takePicture: () -> Unit) {
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .border(
                  width = BORDER_THICKNESS,
                  color = lightGray,
                  shape = RoundedCornerShape(ROUND_CORNER),
              )
              .clip(RoundedCornerShape(ROUND_CORNER))
              .padding(PADDING_MEDIUM),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(PADDING_BIG),
  ) {
    FilledIconButton(
        onClick = takePicture,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = lightOrange),
    ) {
      Icon(
          imageVector = Icons.Outlined.PhotoCamera,
          contentDescription = "Photo Camera",
          tint = Color.Black,
      )
    }

    Text(text = "Take a Picture", color = Color.Black)
  }
}

/**
 * Displays the action for selecting an existing picture.
 *
 * @param modifier The modifier applied to the action button.
 * @param selectPicture The callback invoked when the action is selected.
 */
@Composable
fun DisplaySelectPicture(modifier: Modifier, selectPicture: () -> Unit) {
  // TODO: to be implement
  // the following is a Placeholder

  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .border(
                  width = BORDER_THICKNESS,
                  color = lightGray,
                  shape = RoundedCornerShape(ROUND_CORNER),
              )
              .clip(RoundedCornerShape(ROUND_CORNER))
              .padding(PADDING_MEDIUM),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(PADDING_BIG),
  ) {
    FilledIconButton(
        onClick = selectPicture,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = lightOrange),
    ) {
      Icon(
          imageVector = Icons.Outlined.Photo,
          contentDescription = "Photo",
          tint = Color.Black,
      )
    }

    Text(text = "Upload an existing Picture", color = Color.Black)
  }
}

/** Preview of [UploadPictureScreen] using the Amber application theme. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun UploadPictureScreenPreview() {
  AmberAppTheme(dynamicColor = false) { UploadPictureScreen() }
}
