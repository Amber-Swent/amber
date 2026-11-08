// Written by MeiliV with assistance from OpenAI Codex
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.upload.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.github.se.amber.ui.caregiver.DisplayTextField
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.MAX_CHAR_NAME
import com.github.se.amber.ui.utils.PopupDialog

/** Test tags for the dialog container and its close button. */
object AddPersonDialogTestTags {
  const val DIALOG = "add_person_dialog"
  const val CLOSE = "add_person_dialog_close"
}

/**
 * Displays a controlled add-person form inside [PopupDialog] when [isOpen] is true.
 *
 * The action is enabled only when [canSave]: in the upload screens, a valid first name, valid
 * optional names, and no add operation in progress. A name with characters a name can't have shows
 * its error under its field. On action, trimmed field values are sent through their change
 * callbacks before [onSave] runs.
 *
 * @param isOpen whether the dialog is visible.
 * @param isAddingPerson whether an add operation is in progress.
 * @param firstName current required first name.
 * @param lastName current optional surname.
 * @param nickname current optional nickname.
 * @param firstNameError why [firstName] is refused, or null.
 * @param lastNameError why [lastName] is refused, or null.
 * @param nicknameError why [nickname] is refused, or null.
 * @param canSave whether the action is enabled; by default, when the first name is nonblank and no
 *   add operation is in progress.
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
    firstNameError: String? = null,
    lastNameError: String? = null,
    nicknameError: String? = null,
    canSave: Boolean = firstName.isNotBlank() && !isAddingPerson,
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
      actionEnabled = canSave,
      dialogTestTag = AddPersonDialogTestTags.DIALOG,
      dismissTestTag = AddPersonDialogTestTags.CLOSE,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL)) {
      DisplayTextField(
          value = firstName,
          onValueChange = onFirstNameChange,
          label = "First name",
          maxChar = MAX_CHAR_NAME,
          singleLine = true,
          error = firstNameError,
      )
      DisplayTextField(
          value = lastName,
          onValueChange = onLastNameChange,
          label = "Surname - optional",
          maxChar = MAX_CHAR_NAME,
          singleLine = true,
          error = lastNameError,
      )
      DisplayTextField(
          value = nickname,
          onValueChange = onNicknameChange,
          label = "Nickname - optional",
          maxChar = MAX_CHAR_NAME,
          singleLine = true,
          error = nicknameError,
      )
    }
  }
}
