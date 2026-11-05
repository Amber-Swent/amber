// Written by MeiliV with assistance from OpenAI Codex
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.upload.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.se.amber.ui.caregiver.DisplayTextField
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.utils.PopupDialog

/** Test tags for the dialog container, its close button, its fields and its save button. */
object AddPersonDialogTestTags {
  const val DIALOG = "add_person_dialog"
  const val CLOSE = "add_person_dialog_close"
  const val FIRST_NAME = "add_person_dialog_first_name"
  const val SURNAME = "add_person_dialog_surname"
  const val NICKNAME = "add_person_dialog_nickname"
  const val SAVE = "add_person_dialog_save"
}

/**
 * Displays a controlled add-person form inside [PopupDialog] when [isOpen] is true.
 *
 * The action is enabled only when the first name is nonblank and no add operation is in progress.
 * On action, trimmed field values are sent through their change callbacks before [onSave] runs.
 *
 * @param isOpen whether the dialog is visible.
 * @param isAddingPerson whether an add operation is in progress.
 * @param firstName current required first name.
 * @param lastName current optional surname.
 * @param nickname current optional nickname.
 * @param onFirstNameChange receives first name edits and its trimmed value on save.
 * @param onLastNameChange receives surname edits and its trimmed value on save.
 * @param onNicknameChange receives nickname edits and its trimmed value on save.
 * @param onDismiss called when the dialog is dismissed or closed.
 * @param onSave called after the trimmed values have been sent to their callbacks.
 */
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
      actionTestTag = AddPersonDialogTestTags.SAVE,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL)) {
      DisplayTextField(
          modifier = Modifier.testTag(AddPersonDialogTestTags.FIRST_NAME),
          value = firstName,
          onValueChange = onFirstNameChange,
          label = "First name",
      )
      DisplayTextField(
          modifier = Modifier.testTag(AddPersonDialogTestTags.SURNAME),
          value = lastName,
          onValueChange = onLastNameChange,
          label = "Surname - optional",
      )
      DisplayTextField(
          modifier = Modifier.testTag(AddPersonDialogTestTags.NICKNAME),
          value = nickname,
          onValueChange = onNicknameChange,
          label = "Nickname - optional",
      )
    }
  }
}
