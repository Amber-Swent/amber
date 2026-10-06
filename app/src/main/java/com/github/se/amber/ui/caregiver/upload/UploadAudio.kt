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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.theme.darkOrange
import com.github.se.amber.ui.theme.lightGray
import com.github.se.amber.ui.theme.lightOrange
import com.github.se.amber.ui.theme.orange

enum class AudioState {
  UNRECORD,
  RECORDING,
  RECORD,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadAudio(
    viewModel: UploadAudioViewModel = viewModel(),
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

  val isFormValid =
      uiState.title.isNotBlank() &&
          uiState.description.isNotBlank() &&
          uiState.involvedName.isNotBlank()

  Scaffold(
      topBar = { TopNavigationMenu(Modifier, TopNavigationAction.CANCEL, goBack) },
      content = { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Spacer(modifier = Modifier.height(8.dp))

          Text(
              text = "Upload Audio",
              color = orange,
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
          )

          DisplayAudioField(
              state = uiState.audioState,
              viewModel,
              modifier = Modifier,
          )

          DisplayTextField(
              value = uiState.title,
              onValueChange = { viewModel.setTitle(it) },
              label = "Title",
              placeholder = "Name the story",
              emptyErrorMessage = "Title cannot be empty",
          )

          DisplayTextField(
              value = uiState.description,
              onValueChange = { viewModel.setDescription(it) },
              label = "Description",
              placeholder = "Enter a one-line description",
              emptyErrorMessage = "Description cannot be empty",
              modifier = Modifier.height(200.dp),
          )

          DisplayTextField(
              value = uiState.involvedName,
              onValueChange = { viewModel.setInvolvedName(it) },
              label = "Who is Involved",
              placeholder = "Name all person involved",
              emptyErrorMessage = "They can't be no-one involved",
          )

          // Optional field: no error message
          DisplayTextField(
              value = uiState.date,
              onValueChange = { viewModel.setDate(it) },
              label = "Date",
          )

          Spacer(modifier = Modifier.height(16.dp))

          Button(
              onClick = {
                viewModel.uploadAudio()
                onUpload()
              },
              modifier = Modifier.fillMaxWidth(),
              colors = ButtonDefaults.buttonColors(containerColor = lightOrange),
              enabled = isFormValid,
          ) {
            Text("Upload Text")
          }
        }
      },
  )
}

@Composable
private fun DisplayAudioField(
    state: AudioState,
    viewModel: UploadAudioViewModel,
    modifier: Modifier = Modifier,
) {
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .border(
                  width = 1.dp,
                  color = lightGray,
                  shape = RoundedCornerShape(16.dp),
              )
              .clip(RoundedCornerShape(16.dp))
              .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    when (state) {
      AudioState.UNRECORD -> DisplayUnRecord(viewModel)
      AudioState.RECORDING -> DisplayBeingRecord(viewModel)
      AudioState.RECORD -> DisplayRecord(viewModel)
    }
  }
}

/**
 * An [OutlinedTextField] that shows [emptyErrorMessage] below it once the user has focused it at
 * least once and left it blank. If [emptyErrorMessage] is null, no validation message is shown.
 */
@Composable
private fun DisplayTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    emptyErrorMessage: String? = null,
) {
  var hasBeenFocused by remember { mutableStateOf(false) }

  OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      label = { Text(label) },
      placeholder = placeholder?.let { { Text(it) } },
      modifier =
          modifier.fillMaxWidth().onFocusChanged { focusState ->
            if (focusState.isFocused) hasBeenFocused = true
          },
      shape = RoundedCornerShape(16.dp),
      colors =
          OutlinedTextFieldDefaults.colors(
              focusedBorderColor = orange,
              unfocusedBorderColor = lightGray,
          ),
  )

  if (emptyErrorMessage != null && value.isBlank() && hasBeenFocused) {
    Text(
        text = emptyErrorMessage,
        color = darkOrange,
        style = MaterialTheme.typography.bodySmall,
    )
  }
}

@Composable
private fun DisplayUnRecord(viewModel: UploadAudioViewModel) {
  FilledIconButton(
      onClick = { viewModel.startRecordAudio() },
      modifier = Modifier.size(56.dp),
      shape = CircleShape,
      colors = IconButtonDefaults.filledIconButtonColors(containerColor = lightOrange),
  ) {
    Icon(
        imageVector = Icons.Filled.Mic,
        contentDescription = "Record",
        tint = Color.Black,
    )
  }
  Text("Click on Micro to start Recording")
}

@Composable
private fun DisplayBeingRecord(viewModel: UploadAudioViewModel) {
    TODO("to be implement")
}

@Composable
private fun DisplayRecord(viewModel: UploadAudioViewModel) {
  FilledIconButton(
      onClick = { viewModel.deleteAudio() },
      modifier = Modifier.size(56.dp),
      shape = CircleShape,
      colors = IconButtonDefaults.filledIconButtonColors(containerColor = lightOrange),
  ) {
    Icon(
        imageVector = Icons.Outlined.Delete,
        contentDescription = "Delete",
        tint = Color.Black,
    )
  }// TODO("add graphical audio")
  Text("todo : display AUDIO")

  FilledIconButton(
      onClick = {
        viewModel.stopRecordAudio()
        // TODO: check audio in ui.state is being upload
        // Inside stopRecordAudio() ?
      },
      modifier = Modifier.size(56.dp),
      shape = CircleShape,
      colors = IconButtonDefaults.filledIconButtonColors(containerColor = lightOrange),
  ) {
    Icon(
        imageVector = Icons.Outlined.CropSquare,
        contentDescription = "Stop",
        tint = Color.Black,
    )
  }
}
