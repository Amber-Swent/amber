/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload

import android.R.attr.label
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.ui.caregiver.DisplayBoxWithIconText
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.caregiver.DisplayTextField
import com.github.se.amber.ui.caregiver.upload.UploadPictureTestTags.UPLOAD_BUTTON
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.BORDER_THICKNESS
import com.github.se.amber.ui.theme.MAX_CHAR_DESCRIPTION
import com.github.se.amber.ui.theme.MAX_LINE_DESCRIPTION
import com.github.se.amber.ui.theme.PADDING_BIG
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.ROUND_CORNER
import com.github.se.amber.ui.theme.TEXT_FIELD_BIG
import com.github.se.amber.ui.theme.lightGray
import com.github.se.amber.ui.theme.lightOrange
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object UploadPictureTestTags {
  const val TITLE = "upload_picture_title"
  const val TAKE_PICTURE = "upload_picture_take"
  const val SELECT_PICTURE = "upload_picture_select"
  const val PICTURE_SELECTED = "upload_picture_selected"
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
  val scope = rememberCoroutineScope()

  // Camera: the camera app writes into this file (exposed through a FileProvider)
  // TODO: unExtract once the Backend as been set up for it - M2
  //  var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
  //  val cameraLauncher =
  //      rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
  //        val file = pendingCameraPath?.let(::File)
  //        if (success && file != null) {
  //          val (w, h) = imageSize(file)
  //          viewModel.setPicture(file, w, h)
  //        } else if (!success) {
  //          viewModel.setErrorMsg("No picture was taken")
  //        }
  //      }

  // Gallery: copy the picked Uri to a local file so the repository can upload a File
  val galleryLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
          scope.launch {
            runCatching { withContext(Dispatchers.IO) { readPickedImage(context, uri) } }
                .onSuccess { viewModel.setPicture(it.file, it.width, it.height) }
                .onFailure { viewModel.setErrorMsg("Could not read the selected picture") }
          }
        }
      }

  LaunchedEffect(errorMessage) {
    if (errorMessage != null) {
      Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
      viewModel.clearErrorMsg()
    }
  }

  // The upload is over: leave the screen only once it really succeeded
  LaunchedEffect(uiState.isUploaded) { if (uiState.isUploaded) onUpload() }

  val isFormValid =
      uiState.pictureFile != null &&
          uiState.description.isNotBlank() &&
          // TODO : unExtract once DisplayDatePicker is merge
          //  uiState.date != null &&
          !uiState.isUploading

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
          DisplayBoxWithIconText(
              modifier = Modifier.testTag(UploadPictureTestTags.TAKE_PICTURE),
              icon = Icons.Outlined.PhotoCamera,
              contentDescription = "Photo",
              label = "Take a new Picture",
              onClick = {},
              // TODO: unExtract once the Backend as been set up for it - M2
              // place it as function for onCLick
              //                {
              //            try {
              //              val file = File.createTempFile("camera_", ".jpg", context.cacheDir)
              //              pendingCameraPath = file.absolutePath
              //              val uri =
              //                  FileProvider.getUriForFile(context,
              // "${context.packageName}.fileprovider", file)
              //              cameraLauncher.launch(uri)
              //            } catch (e: ActivityNotFoundException) {
              //              viewModel.setErrorMsg("No camera app available")
              //            } catch (e: Exception) {
              //                Log.e("UploadPicture", "Camera launch failed", e)
              //                viewModel.setErrorMsg("Camera error: ${e.message}")
              //            }
              //          }
          )

          DisplayBoxWithIconText(
              modifier = Modifier.testTag(UploadPictureTestTags.SELECT_PICTURE),
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
                modifier = Modifier.testTag(UploadPictureTestTags.PICTURE_SELECTED),
            )
          }

          DisplayTextField(
              modifier = Modifier.height(TEXT_FIELD_BIG),
              value = uiState.description,
              onValueChange = { viewModel.setDescription(it) },
              label = "Description",
              placeholder = "Enter a one-line description",
              emptyErrorMessage = "Description cannot be empty",
              maxLines = MAX_LINE_DESCRIPTION,
              maxChar = MAX_CHAR_DESCRIPTION,
          )

          DisplayWhoIsInvolved(
              options = options,
              shownIds = uiState.shownIds,
              toggleShownId = viewModel::toggleShownId,
          )

          // TODO : UnExtract once DatePicker's PR is merged
          //            DisplayScrollableDatePicker(
          //                onDateSelected = { year, month, day ->
          //                viewModel.setDate(MemoryDate(year, month, day))}
          //            )

          Spacer(modifier = Modifier.height(PADDING_MEDIUM))

          Button(
              onClick = { viewModel.uploadPicture() },
              modifier = Modifier.fillMaxWidth(),
              colors = ButtonDefaults.buttonColors(containerColor = lightOrange),
              enabled = isFormValid,
          ) {
            Text(
                modifier = Modifier.testTag(UPLOAD_BUTTON),
                text = if (uiState.isUploading) "Uploading…" else "Upload Picture",
            )
          }
        }
      },
  )
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

    Text(text = "Take a new Picture", color = Color.Black)
  }
}

private fun imageSize(file: File): Pair<Int, Int> {
  val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeFile(file.absolutePath, opts)
  return opts.outWidth to opts.outHeight
}

private fun copyUriToCache(context: Context, uri: Uri): File {
  val file = File.createTempFile("picked_", ".jpg", context.cacheDir)
  context.contentResolver.openInputStream(uri)!!.use { input ->
    file.outputStream().use { input.copyTo(it) }
  }
  return file
}

/** A picture copied to a local file, with its pixel dimensions. */
private data class PickedImage(val file: File, val width: Int, val height: Int)

private fun readPickedImage(context: Context, uri: Uri): PickedImage {
  val file = copyUriToCache(context, uri)
  val (width, height) = imageSize(file)
  return PickedImage(file, width, height)
}

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
        modifier = Modifier.fillMaxWidth(),
    ) {
        if ( options.isEmpty() ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
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
                    text = "No one are part of your care circle",
                    color = Color.Black
                )
            }

        }else {
            options.forEach { (id, label) ->
                FilterChip(
                    selected = id in shownIds,
                    onClick = { toggleShownId(id) },
                    label = { Text(label) },
                    modifier = Modifier.testTag("involved_$id"),
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
