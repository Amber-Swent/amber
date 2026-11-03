// Modified by Yuhan YANG, with assistance from Cursor.
package com.github.se.amber.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.github.se.amber.ui.onboarding.DividerTestTags.HORIZONTAL_DIVIDER_LEFT
import com.github.se.amber.ui.onboarding.DividerTestTags.HORIZONTAL_DIVIDER_RIGHT
import com.github.se.amber.ui.onboarding.DividerTestTags.ROW
import com.github.se.amber.ui.onboarding.DividerTestTags.TEXT
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.ROUND_CORNER
import com.github.se.amber.ui.theme.WEIGHT_ONE
import com.github.se.amber.ui.theme.lightGray

/** Test tags for [Divider]. */
object DividerTestTags {
  const val HORIZONTAL_DIVIDER_LEFT = "divider_horitonzal_divider_left"
  const val HORIZONTAL_DIVIDER_RIGHT = "divider_horitonzal_divider_right"
  const val TEXT = "divider_text"
  const val ROW = "divider_container"
}

/**
 * Shared [OutlinedTextField] colors for onboarding forms.
 *
 * Keeps outline, label, placeholder, cursor, and error colors aligned across create-profile,
 * create-care-circle, and invitation-redemption screens.
 */
@Composable
fun onboardingOutlinedTextFieldColors(): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.outline,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor = MaterialTheme.colorScheme.onSurface,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface,
        cursorColor = MaterialTheme.colorScheme.onSurface,
        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface,
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface,
        errorBorderColor = MaterialTheme.colorScheme.error,
        errorLabelColor = MaterialTheme.colorScheme.error,
        errorCursorColor = MaterialTheme.colorScheme.error,
        errorSupportingTextColor = MaterialTheme.colorScheme.error,
    )

/**
 * Single-line outlined field used by onboarding forms.
 *
 * @param value current text.
 * @param onValueChange called when the text changes.
 * @param label border label shown above the field.
 * @param modifier modifier applied to the text field.
 * @param placeholder hint shown inside an empty focused field, or null when the label is enough.
 * @param placeholderTestTag optional test tag on the placeholder text.
 * @param error supporting error text, or null when the field is valid.
 * @param errorTestTag optional test tag on the supporting error text.
 * @param enabled whether the field accepts input.
 */
@Composable
fun OnboardingOutlinedTextField(
    value: String = "",
    onValueChange: (String) -> Unit = {},
    label: String = "",
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    placeholderTestTag: String? = null,
    error: String? = null,
    errorTestTag: String? = null,
    enabled: Boolean = true,
) {
  OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      modifier = modifier.fillMaxWidth(),
      enabled = enabled,
      label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
      placeholder =
          placeholder?.let { hint ->
            {
              Text(
                  text = hint,
                  modifier = placeholderTestTag?.let { Modifier.testTag(it) } ?: Modifier,
                  style = MaterialTheme.typography.labelMedium,
              )
            }
          },
      supportingText =
          error?.let { message ->
            {
              Text(
                  text = message,
                  style = MaterialTheme.typography.bodySmall,
                  modifier = errorTestTag?.let { Modifier.testTag(it) } ?: Modifier,
              )
            }
          },
      isError = error != null,
      singleLine = true,
      shape = RoundedCornerShape(ROUND_CORNER),
      colors = onboardingOutlinedTextFieldColors(),
  )
}

/**
 * Displays two horizontal dividers separated by a centered text label.
 *
 * @param modifier modifier applied to the divider row.
 * @param text the text display in between
 * @param style text style used for the "or" label.
 */
@Composable
fun Divider(
    modifier: Modifier = Modifier,
    text: String = "or",
    style: TextStyle = MaterialTheme.typography.titleMedium,
) {
  Row(
      modifier = modifier.fillMaxWidth().testTag(ROW),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
  ) {
    HorizontalDivider(
        modifier = Modifier.weight(WEIGHT_ONE).testTag(HORIZONTAL_DIVIDER_LEFT),
        // independent of DarkMode and lightMode
        color = lightGray,
    )

    Text(
        text = text,
        color = MaterialTheme.colorScheme.outline,
        style = style,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = CAREGIVER_PADDING_SMALL).testTag(TEXT),
    )

    HorizontalDivider(
        modifier = Modifier.weight(WEIGHT_ONE).testTag(HORIZONTAL_DIVIDER_RIGHT),
        color = lightGray,
    )
  }
}
