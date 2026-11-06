// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.utils

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.se.amber.ui.caregiver.DisplayCaregiverButton
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.DIALOG_WIDTH
import com.github.se.amber.ui.theme.ROUND_CORNER

/**
 * Shared popup frame with a title, close button, caller content, and one bottom action.
 *
 * The surface uses the theme colors and applies IME padding so form content remains accessible
 * while the keyboard is open. Dismissal and action callbacks are owned by the caller.
 *
 * @param modifier modifier applied to the popup surface.
 * @param title text shown in the popup header.
 * @param onDismiss called by the close button and system dismissal.
 * @param actionText label for the bottom action button.
 * @param onAction called when the enabled action button is pressed.
 * @param actionEnabled whether the bottom action can be pressed.
 * @param properties platform dialog behavior.
 * @param dialogTestTag test tag for the popup surface.
 * @param titleTestTag test tag for the title.
 * @param dismissTestTag test tag for the close button.
 * @param actionTestTag test tag for the bottom action button.
 * @param content caller-provided body between the header and action button.
 */
@Composable
fun PopupDialog(
    modifier: Modifier = Modifier,
    title: String = "",
    onDismiss: () -> Unit = {},
    actionText: String = "",
    onAction: () -> Unit = {},
    actionEnabled: Boolean = true,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    dialogTestTag: String = "",
    titleTestTag: String = "",
    dismissTestTag: String = "",
    actionTestTag: String = "",
    content: @Composable () -> Unit = {},
) {
  Dialog(onDismissRequest = onDismiss, properties = properties) {
    Surface(
        modifier = modifier.fillMaxWidth(DIALOG_WIDTH).testTag(dialogTestTag),
        shape = RoundedCornerShape(ROUND_CORNER),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
      Column(
          modifier =
              Modifier.imePadding()
                  .verticalScroll(rememberScrollState())
                  .padding(CAREGIVER_PADDING_MEDIUM),
          verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_MEDIUM),
      ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          DisplayCaregiverTitle(
              modifier = Modifier.weight(1f).testTag(titleTestTag),
              title = title,
          )
          IconButton(
              onClick = onDismiss,
              modifier = Modifier.testTag(dismissTestTag),
          ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
        content()
        DisplayCaregiverButton(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            onClick = onAction,
            enabled = actionEnabled,
            text = actionText,
            buttonTestTag = actionTestTag,
        )
      }
    }
  }
}
