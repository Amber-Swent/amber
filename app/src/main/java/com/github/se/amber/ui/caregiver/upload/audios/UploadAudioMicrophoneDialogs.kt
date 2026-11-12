// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.upload.audios

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.github.se.amber.R
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.MICROPHONE_DIALOG_TEXT_HEIGHT_FRACTION
import com.github.se.amber.ui.utils.PopupDialog

/**
 * Displays the popup that explains why the microphone is needed, when [isOpen] is true. It is shown
 * before the Android permission popup, which is only requested once the user has understood.
 *
 * @param isOpen whether the popup is visible.
 * @param onDismiss called when the popup is closed without being understood.
 * @param onAction called when the user taps "I've understood".
 */
@Composable
fun MicrophoneConsentDialog(
    isOpen: Boolean = false,
    onDismiss: () -> Unit = {},
    onAction: () -> Unit = {},
) {
  MicrophoneDialog(
      isOpen = isOpen,
      firstTextRes = R.string.uploadAudioMicrophoneConsentTextFirst,
      secondTextRes = R.string.uploadAudioMicrophoneConsentTextSecond,
      onDismiss = onDismiss,
      onAction = onAction,
      dialogTestTag = UploadAudioScreenTestTags.MICROPHONE_CONSENT_DIALOG,
      titleTestTag = UploadAudioScreenTestTags.MICROPHONE_CONSENT_TITLE,
      firstTextTestTag = UploadAudioScreenTestTags.MICROPHONE_CONSENT_TEXT_FIRST,
      secondTextTestTag = UploadAudioScreenTestTags.MICROPHONE_CONSENT_TEXT_SECOND,
      dismissTestTag = UploadAudioScreenTestTags.MICROPHONE_CONSENT_CLOSE,
      actionTestTag = UploadAudioScreenTestTags.MICROPHONE_CONSENT_ACTION,
  )
}

/**
 * Displays the popup telling the user that the microphone was refused twice, so Android no longer
 * asks and the permission can only be given in the phone's settings, when [isOpen] is true.
 *
 * @param isOpen whether the popup is visible.
 * @param onDismiss called when the popup is closed, by its button or its close button.
 */
@Composable
fun MicrophoneSettingsDialog(
    isOpen: Boolean = false,
    onDismiss: () -> Unit = {},
) {
  MicrophoneDialog(
      isOpen = isOpen,
      firstTextRes = R.string.uploadAudioMicrophoneSettingsTextFirst,
      secondTextRes = R.string.uploadAudioMicrophoneSettingsTextSecond,
      onDismiss = onDismiss,
      onAction = onDismiss,
      dialogTestTag = UploadAudioScreenTestTags.MICROPHONE_SETTINGS_DIALOG,
      titleTestTag = UploadAudioScreenTestTags.MICROPHONE_SETTINGS_TITLE,
      firstTextTestTag = UploadAudioScreenTestTags.MICROPHONE_SETTINGS_TEXT_FIRST,
      secondTextTestTag = UploadAudioScreenTestTags.MICROPHONE_SETTINGS_TEXT_SECOND,
      dismissTestTag = UploadAudioScreenTestTags.MICROPHONE_SETTINGS_CLOSE,
      actionTestTag = UploadAudioScreenTestTags.MICROPHONE_SETTINGS_ACTION,
  )
}

/**
 * The [PopupDialog] shared by the two microphone popups: same title and button, only the text and
 * the callbacks differ.
 *
 * @param isOpen whether the popup is visible.
 * @param firstTextRes the first sentence of the explanation, as a string resource.
 * @param secondTextRes the second sentence of the explanation, as a string resource.
 * @param onDismiss called when the popup is closed with its close button or by the system.
 * @param onAction called when the user taps the button.
 * @param dialogTestTag test tag of the popup surface.
 * @param titleTestTag test tag of the title.
 * @param firstTextTestTag test tag of the first sentence.
 * @param secondTextTestTag test tag of the second sentence.
 * @param dismissTestTag test tag of the close button.
 * @param actionTestTag test tag of the button.
 */
@Composable
private fun MicrophoneDialog(
    isOpen: Boolean = false,
    @StringRes firstTextRes: Int = R.string.uploadAudioMicrophoneConsentTextFirst,
    @StringRes secondTextRes: Int = R.string.uploadAudioMicrophoneConsentTextSecond,
    onDismiss: () -> Unit = {},
    onAction: () -> Unit = {},
    dialogTestTag: String = "",
    titleTestTag: String = "",
    firstTextTestTag: String = "",
    secondTextTestTag: String = "",
    dismissTestTag: String = "",
    actionTestTag: String = "",
) {
  if (!isOpen) return

  PopupDialog(
      title = stringResource(R.string.uploadAudioMicrophoneTitle),
      onDismiss = onDismiss,
      actionText = stringResource(R.string.uploadAudioMicrophoneUnderstood),
      onAction = onAction,
      dialogTestTag = dialogTestTag,
      titleTestTag = titleTestTag,
      dismissTestTag = dismissTestTag,
      actionTestTag = actionTestTag,
  ) {
    // As tall as the date picker popup, so the popups of the app look alike
    val screenHeight =
        with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    Column(
        modifier = Modifier.heightIn(min = screenHeight * MICROPHONE_DIALOG_TEXT_HEIGHT_FRACTION),
        verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_MEDIUM),
    ) {
      Text(
          modifier = Modifier.testTag(firstTextTestTag),
          text = stringResource(firstTextRes),
          style = MaterialTheme.typography.bodyLarge,
      )
      Text(
          modifier = Modifier.testTag(secondTextTestTag),
          text = stringResource(secondTextRes),
          style = MaterialTheme.typography.bodyLarge,
      )
    }
  }
}

/** Preview of [MicrophoneConsentDialog] using the Amber application theme. */
@Preview(showBackground = true)
@Composable
internal fun MicrophoneConsentDialogPreview() {
  AmberAppTheme(dynamicColor = false) { MicrophoneConsentDialog(isOpen = true) }
}

/** Preview of [MicrophoneSettingsDialog] using the Amber application theme. */
@Preview(showBackground = true)
@Composable
internal fun MicrophoneSettingsDialogPreview() {
  AmberAppTheme(dynamicColor = false) { MicrophoneSettingsDialog(isOpen = true) }
}
