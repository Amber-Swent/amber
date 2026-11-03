// Written by MeiliV, with assistance from OpenAI Codex.
package com.github.se.amber.ui.utils

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** A common popup shell with a title, close button, content, and one bottom action. */
@Composable
fun PopupDialog(
    title: String,
    onDismiss: () -> Unit,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionEnabled: Boolean = true,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    dialogTestTag: String = "",
    titleTestTag: String = "",
    dismissTestTag: String = "",
    actionTestTag: String = "",
    content: @Composable () -> Unit,
) {
  Dialog(onDismissRequest = onDismiss, properties = properties) {
    Surface(
        modifier = modifier.fillMaxWidth(DIALOG_WIDTH).testTag(dialogTestTag),
        shape = RoundedCornerShape(ROUND_CORNER),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
      Column(
          modifier = Modifier.imePadding().padding(CAREGIVER_PADDING_MEDIUM),
          verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_MEDIUM),
      ) {
        Box(modifier = Modifier.fillMaxWidth()) {
          DisplayCaregiverTitle(
              modifier = Modifier.align(Alignment.CenterStart).testTag(titleTestTag),
              title = title,
          )
          IconButton(
              onClick = onDismiss,
              modifier = Modifier.align(Alignment.TopEnd).testTag(dismissTestTag),
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

