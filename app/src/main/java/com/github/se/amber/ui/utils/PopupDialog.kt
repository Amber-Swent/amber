// Written by MeiliV, with assistance from OpenAI Codex.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.utils

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.se.amber.R
import com.github.se.amber.ui.caregiver.DisplayCaregiverButton
import com.github.se.amber.ui.caregiver.DisplayCaregiverTitle
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.DIALOG_WIDTH
import com.github.se.amber.ui.theme.POPUP_DIALOG_MIN_HEIGHT_FRACTION
import com.github.se.amber.ui.theme.ROUND_CORNER

/**
 * Shared popup frame with a title, close button, caller content, and one bottom action.
 *
 * The surface uses the theme colors and applies IME padding so form content remains accessible
 * while the keyboard is open. It is at least [POPUP_DIALOG_MIN_HEIGHT_FRACTION] of the screen high,
 * and taller if its content needs more. Dismissal and action callbacks are owned by the caller.
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
    val minHeight =
        with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } *
            POPUP_DIALOG_MIN_HEIGHT_FRACTION
    Surface(
        modifier =
            modifier.fillMaxWidth(DIALOG_WIDTH).heightIn(min = minHeight).testTag(dialogTestTag),
        shape = RoundedCornerShape(ROUND_CORNER),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
      // The title and the content stay on top, the action button goes to the bottom: when the popup
      // is taller than its content (see the minimum height), the free space is left between them
      Column(
          modifier =
              Modifier.imePadding()
                  .verticalScroll(rememberScrollState())
                  .padding(CAREGIVER_PADDING_MEDIUM),
          verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_MEDIUM)) {
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
                  contentDescription = stringResource(R.string.popupDialogClose),
                  tint = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
          content()
        }
        DisplayCaregiverButton(
            modifier =
                Modifier.align(Alignment.CenterHorizontally)
                    .padding(top = CAREGIVER_PADDING_MEDIUM),
            onClick = onAction,
            enabled = actionEnabled,
            text = actionText,
            buttonTestTag = actionTestTag,
        )
      }
    }
  }
}
