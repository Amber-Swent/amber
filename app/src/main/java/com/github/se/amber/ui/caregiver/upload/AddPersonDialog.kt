/* Written with assistance from OpenAI Codex. */
package com.github.se.amber.ui.caregiver.upload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import com.github.se.amber.ui.caregiver.DisplayCaregiverButton
import com.github.se.amber.ui.caregiver.DisplayTextField
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.ROUND_CORNER

/** Tags owned by the shared add-person dialog. */
object AddPersonDialogTestTags {
  const val DIALOG = "upload_picture_add_person_dialog"
  const val CLOSE = "upload_picture_close_add_person"
}

/** Displays the add-person form used by caregiver upload screens. The caller owns its state. */
@Composable
fun AddPersonDialog(
    isOpen: Boolean = false,
    isAddingPerson: Boolean = false,
    firstName: String = "",
    lastName: String = "",
    nickname: String = "",
    onFirstNameChange: (String) -> Unit = {},
    onLastNameChange: (String) -> Unit = {},
    onNicknameChange: (String) -> Unit = {},
    onDismiss: () -> Unit = {},
    onSave: () -> Unit = {},
) {
  if (!isOpen) return

  Dialog(onDismissRequest = onDismiss) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(AddPersonDialogTestTags.DIALOG),
        shape = RoundedCornerShape(ROUND_CORNER),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
      Column(
          modifier =
              Modifier.imePadding()
                  .verticalScroll(rememberScrollState())
                  .padding(CAREGIVER_PADDING_MEDIUM),
          verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL),
      ) {
        Box(modifier = Modifier.fillMaxWidth()) {
          Text(
              text = "Add a person",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.align(Alignment.CenterStart),
          )
          IconButton(
              onClick = onDismiss,
              modifier = Modifier.align(Alignment.TopEnd).testTag(AddPersonDialogTestTags.CLOSE),
          ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
        DisplayTextField(value = firstName, onValueChange = onFirstNameChange, label = "Firstname")
        DisplayTextField(
            value = lastName,
            onValueChange = onLastNameChange,
            label = "Surname - optional",
        )
        DisplayTextField(value = nickname, onValueChange = onNicknameChange, label = "Nickname")
        DisplayCaregiverButton(
            onClick = {
              onFirstNameChange(firstName.trim())
              onLastNameChange(lastName.trim())
              onNicknameChange(nickname.trim())
              onSave()
            },
            enabled = firstName.isNotBlank() && !isAddingPerson,
            text = if (isAddingPerson) "Adding…" else "Add person",
        )
      }
    }
  }
}
