// Written by MeiliV with assistance from OpenAI Codex
package com.github.se.amber.ui.caregiver.upload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.github.se.amber.ui.caregiver.DisplayTextField
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.utils.PopupDialog

/** Test tags for the dialog container and its close button. */
object AddPersonDialogTestTags {
  const val DIALOG = "add_person_dialog"
  const val CLOSE = "add_person_dialog_close"
}

/** Displays the add-person form in the shared popup frame. */
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

  PopupDialog(
      title = "Add a person",
      onDismiss = onDismiss,
      actionText = if (isAddingPerson) "Adding…" else "Add person",
      onAction = {
        onFirstNameChange(firstName.trim())
        onLastNameChange(lastName.trim())
        onNicknameChange(nickname.trim())
        onSave()
      },
      actionEnabled = firstName.isNotBlank() && !isAddingPerson,
      dialogTestTag = AddPersonDialogTestTags.DIALOG,
      dismissTestTag = AddPersonDialogTestTags.CLOSE,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL)) {
      DisplayTextField(value = firstName, onValueChange = onFirstNameChange, label = "First name")
      DisplayTextField(
          value = lastName,
          onValueChange = onLastNameChange,
          label = "Surname - optional",
      )
      DisplayTextField(
          value = nickname,
          onValueChange = onNicknameChange,
          label = "Nickname - optional",
      )
    }
  }
}
