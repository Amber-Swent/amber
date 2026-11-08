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
 * The action is enabled only when the first name is nonblank, no field shows an error, and no add
 * operation is in progress. On action, trimmed field values are sent through their change callbacks
 * before [onSave] runs.
 *
 * @param isOpen whether the dialog is visible.
 * @param isAddingPerson whether an add operation is in progress.
 * @param firstName current required first name.
 * @param lastName current optional surname.
 * @param nickname current optional nickname.
 * @param firstNameError why [firstName] is refused, or null.
 * @param lastNameError why [lastName] is refused, or null.
 * @param nicknameError why [nickname] is refused, or null.
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
      actionEnabled =
          firstName.isNotBlank() &&
              !isAddingPerson &&
              listOf(firstNameError, lastNameError, nicknameError).all { it == null },
      dialogTestTag = AddPersonDialogTestTags.DIALOG,
      dismissTestTag = AddPersonDialogTestTags.CLOSE,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL)) {
      NameField(firstName, onFirstNameChange, "First name", firstNameError)
      NameField(lastName, onLastNameChange, "Surname - optional", lastNameError)
      NameField(nickname, onNicknameChange, "Nickname - optional", nicknameError)
    }
  }
}

/** One single-line name field of [AddPersonDialog], with the names' length limit. */
@Composable
private fun NameField(
    value: String = "",
    onValueChange: (String) -> Unit = {},
    label: String = "",
    error: String? = null,
) {
  DisplayTextField(
      value = value,
      onValueChange = onValueChange,
      label = label,
      maxChar = MAX_CHAR_NAME,
      singleLine = true,
      error = error,
  )
}
