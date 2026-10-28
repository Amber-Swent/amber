/* Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.
 * Modified with assistance from OpenAI Codex.
 */
package com.github.se.amber.ui.caregiver

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.github.se.amber.ui.caregiver.DisplayBoxWithIconTextTestTags.ICON_BUTTON
import com.github.se.amber.ui.caregiver.DisplayCaregiverButtonTestTags.BUTTON
import com.github.se.amber.ui.caregiver.DisplayTextFieldTestTags.ERROR_MESSAGE
import com.github.se.amber.ui.caregiver.DisplayTextFieldTestTags.MAX_CHAR_MESSAGE
import com.github.se.amber.ui.caregiver.DisplayTextFieldTestTags.MAX_LINES_MESSAGE
import com.github.se.amber.ui.caregiver.DisplayTextFieldTestTags.TEXT
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.BORDER_THICKNESS
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_HEIGHT_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_BUTTON_WIDTH
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.MAX_CHAR_DEFAULT
import com.github.se.amber.ui.theme.MAX_LINE_DEFAULT
import com.github.se.amber.ui.theme.MIN_LINE_DEFAULT
import com.github.se.amber.ui.theme.PADDING_BIG
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.ROUND_CORNER

/**
 * Displays a title shared by caregiver screens.
 *
 * @param title Text to display.
 * @param modifier Modifier applied to the title, including any caller-provided test tag.
 */
@Composable
fun DisplayCaregiverTitle(title: String, modifier: Modifier = Modifier) {
  Text(
      modifier = modifier.padding(top = PADDING_SMALL, bottom = PADDING_MEDIUM),
      text = title,
      color = MaterialTheme.colorScheme.secondary,
      style = MaterialTheme.typography.headlineSmall,
  )
}

/** Test tags owned by [DisplayCaregiverButton]. */
object DisplayCaregiverButtonTestTags {
  const val TEXT = "caregiver_button_text"
  const val ICON = "caregiver_button_icon"
  const val BUTTON = "caregiver_button"
}

/**
 * Displays a caregiver button with optional icon content.
 *
 * An icon and its content description must either both be provided or both be omitted. If there is
 * no icon, the text is centered. If there is an icon, the text is on the left and the icon on the
 * right
 *
 * @param modifier modifier applied to the button.
 * @param onClick callback invoked when the button is clicked.
 * @param enabled whether the button is enabled and can be clicked.
 * @param icon optional icon displayed on the button.
 * @param description content description for [icon], required when an icon is provided.
 * @param text text displayed on the button.
 * @param style style displayed by the text.
 * @param height minimum height of the button; content can make it taller.
 * @param widthPercentage fraction of the available width occupied by the button.
 * @param paddingTextAndIcon padding applied between the button content and its edges.
 */
@Composable
fun DisplayCaregiverButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    enabled: Boolean = true,
    icon: ImageVector? = null,
    description: String = "",
    text: String = "Text",
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    height: Dp = CAREGIVER_BUTTON_HEIGHT_MEDIUM,
    widthPercentage: Float = CAREGIVER_BUTTON_WIDTH,
    paddingTextAndIcon: Dp = CAREGIVER_PADDING_SMALL,
) {
  require((icon == null && description.isEmpty()) || (icon != null && description.isNotEmpty())) {
    "An icon requires a description, and a description requires an icon"
  }
  Button(
      onClick = onClick,
      enabled = enabled,
      // A minimum height lets multiline text expand without being clipped.
      modifier = modifier.fillMaxWidth(widthPercentage).heightIn(min = height).testTag(BUTTON),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.tertiary,
              contentColor = MaterialTheme.colorScheme.onTertiary,
          ),
      shape = RoundedCornerShape(ROUND_CORNER),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(paddingTextAndIcon),
        horizontalArrangement = if (icon == null) Arrangement.Center else Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text = text,
          modifier = Modifier.testTag(DisplayCaregiverButtonTestTags.TEXT),
          color = MaterialTheme.colorScheme.onTertiary,
          textAlign = TextAlign.Center,
          style = style,
      )
      if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.testTag(DisplayCaregiverButtonTestTags.ICON),
            tint = MaterialTheme.colorScheme.onTertiary,
        )
      }
    }
  }
}

/** Test tags owned by [DisplayTextField]. */
object DisplayTextFieldTestTags {
  const val TEXT = "display_textField_text"
  const val MAX_LINES_MESSAGE = "display_textField_max_lines"
  const val MAX_CHAR_MESSAGE = "display_textField_max_char"
  const val ERROR_MESSAGE = "display_textField_error_message"
}

/**
 * Displays a text field with optional validation feedback.
 *
 * @param modifier The modifier applied to the text field.
 * @param value The current text field value.
 * @param onValueChange The callback invoked when the text changes.
 * @param label The label displayed for the text field.
 * @param placeholder The optional placeholder displayed when the field is empty.
 * @param emptyErrorMessage The optional error message displayed when the field is empty after
 *   focus.
 * @param minLines The minimum number of lines displayed by the text field.
 * @param maxLines The maximum number of lines displayed by the text field and the warning
 *   threshold.
 * @param maxChar Character count at which a warning is displayed after focus.
 */
@Composable
fun DisplayTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    emptyErrorMessage: String? = null,
    minLines: Int = MIN_LINE_DEFAULT,
    maxLines: Int = MAX_LINE_DEFAULT,
    maxChar: Int = MAX_CHAR_DEFAULT,
) {
  assert(maxChar > 0)
  assert(minLines > 0)

  var hasBeenFocused by remember { mutableStateOf(false) }

  OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      label = { Text(text = label, color = MaterialTheme.colorScheme.onSurface) },
      placeholder = placeholder?.let { { Text(it) } },
      modifier =
          modifier
              .fillMaxWidth()
              .onFocusChanged { focusState -> if (focusState.isFocused) hasBeenFocused = true }
              .testTag(TEXT),
      shape = RoundedCornerShape(ROUND_CORNER),
      colors =
          OutlinedTextFieldDefaults.colors(
              focusedBorderColor = MaterialTheme.colorScheme.secondary,
              unfocusedBorderColor = MaterialTheme.colorScheme.tertiary,
          ),
      minLines = minLines,
      maxLines = maxLines,
  )

  Column {
    if (value.length >= maxChar && hasBeenFocused) {
      Text(
          modifier = Modifier.testTag(MAX_CHAR_MESSAGE),
          text = "Maximum $maxChar characters reached",
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
      )
    }
    if (value.lines().size >= maxLines && hasBeenFocused) {
      Text(
          modifier = Modifier.testTag(MAX_LINES_MESSAGE),
          text = "Maximum $maxLines lines reached",
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
      )
    }
    if (emptyErrorMessage != null && value.isBlank() && hasBeenFocused) {
      Text(
          modifier = Modifier.testTag(ERROR_MESSAGE),
          text = emptyErrorMessage,
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

/** Test tags owned by [DisplayBoxWithIconText]. */
object DisplayBoxWithIconTextTestTags {
  const val ICON_BUTTON = "box_with_icon_text_icon_button"
}

/**
 * Displays a bordered row with a circular icon button and a label.
 *
 * @param modifier The modifier applied to the action container.
 * @param icon The icon shown inside the button.
 * @param contentDescription The accessibility description of the icon.
 * @param label The text displayed next to the button.
 * @param onClick The callback invoked when the button is pressed.
 */
@Composable
fun DisplayBoxWithIconText(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    contentDescription: String,
    label: String,
    onClick: () -> Unit,
) {
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .border(
                  width = BORDER_THICKNESS,
                  color = MaterialTheme.colorScheme.tertiary,
                  shape = RoundedCornerShape(ROUND_CORNER),
              )
              .clip(RoundedCornerShape(ROUND_CORNER))
              .padding(PADDING_MEDIUM),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(PADDING_BIG),
  ) {
    FilledIconButton(
        modifier = Modifier.testTag(ICON_BUTTON),
        onClick = onClick,
        shape = CircleShape,
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            ),
    ) {
      Icon(
          imageVector = icon,
          contentDescription = contentDescription,
          tint = MaterialTheme.colorScheme.onTertiary,
      )
    }

    Text(text = label, color = MaterialTheme.colorScheme.onSurface)
  }
}

/** Preview of [DisplayCaregiverButton] using the Amber application theme. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun DisplayCaregiverButtonPreview() {
  AmberAppTheme { DisplayCaregiverButton() }
}
