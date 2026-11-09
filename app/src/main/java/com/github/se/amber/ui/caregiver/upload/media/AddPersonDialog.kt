// Written by MeiliV with assistance from OpenAI Codex
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.upload.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.se.amber.R
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
 * The action is enabled when [canAddPerson] is true; the ViewModel decides it, so the rule lives in
 * one place. On action, trimmed field values are sent through their change callbacks before
 * [onSave] runs.
 *
 * @param isOpen whether the dialog is visible.
 * @param isAddingPerson whether an add operation is in progress.
 * @param canAddPerson whether the action is enabled.
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
    canAddPerson: Boolean = false,
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
      title = stringResource(R.string.addPersonTitle),
      onDismiss = onDismiss,
      actionText =
          stringResource(if (isAddingPerson) R.string.addPersonAdding else R.string.addPersonAdd),
      onAction = {
        onFirstNameChange(firstName.trim())
        onLastNameChange(lastName.trim())
        onNicknameChange(nickname.trim())
        onSave()
      },
      actionEnabled = canAddPerson,
      dialogTestTag = AddPersonDialogTestTags.DIALOG,
      dismissTestTag = AddPersonDialogTestTags.CLOSE,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL)) {
      NameField(
          firstName,
          onFirstNameChange,
          stringResource(R.string.addPersonFirstName),
          firstNameError,
      )
      NameField(
          lastName,
          onLastNameChange,
          stringResource(R.string.addPersonSurname),
          lastNameError,
      )
      NameField(
          nickname,
          onNicknameChange,
          stringResource(R.string.addPersonNickname),
          nicknameError,
      )
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
