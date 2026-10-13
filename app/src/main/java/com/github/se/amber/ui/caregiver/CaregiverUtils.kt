/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.github.se.amber.ui.theme.MAX_CHAR_DEFAULT
import com.github.se.amber.ui.theme.MAX_LINE_DEFAULT
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.ROUND_CORNER
import com.github.se.amber.ui.theme.darkOrange
import com.github.se.amber.ui.theme.lightGray
import com.github.se.amber.ui.theme.orange

/**
 * Displays the title for caregiver's Screen
 *
 * @param title title to display
 * @param modifier modifier to apply to the text (can for e.g. contains the necessary TestTag)
 */
@Composable
fun DisplayCaregiverTitle(title: String, modifier: Modifier) {
  Text(
      modifier = modifier.padding(top = PADDING_SMALL, bottom = PADDING_MEDIUM),
      text = title,
      color = orange,
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
  )
}


/**
 * Displays a text field with optional validation feedback.
 *
 * @param value The current text field value.
 * @param onValueChange The callback invoked when the text changes.
 * @param label The label displayed for the text field.
 * @param modifier The modifier applied to the text field.
 * @param placeholder The optional placeholder displayed when the field is empty.
 * @param emptyErrorMessage The optional error message displayed when the field is empty after
 *   focus.
 * @param minLines The minimum number of lines displayed by the text field.
 * @param maxLines The maximum number of lines displayed by the text field.
 * @param maxChar The maximum number of character displayed by the text field.
 *
 */
@Composable
fun DisplayTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    emptyErrorMessage: String? = null,
    minLines: Int = 1,
    maxLines: Int = MAX_LINE_DEFAULT,
    maxChar : Int = MAX_CHAR_DEFAULT
) {
    assert(maxChar > 0)
    assert(minLines > 0 )

    var hasBeenFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label, color = Color.Black) },
        placeholder = placeholder?.let { { Text(it) } },
        modifier =
            modifier
                .fillMaxWidth()
                .onFocusChanged { focusState -> if (focusState.isFocused) hasBeenFocused = true }
                .focusRequester(focusRequester),
        shape = RoundedCornerShape(ROUND_CORNER),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = orange,
                unfocusedBorderColor = lightGray,
            ),
        minLines = minLines,
        maxLines = maxLines,
    )

    Column() {
    if (value.length >= maxChar && hasBeenFocused) {
        Text(
            text = "Maximum $maxChar characters reached",
            color = darkOrange,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (value.lines().size >= maxLines && hasBeenFocused) {
        Text(
            text = "Maximum $maxLines lines reached",
            color = darkOrange,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (emptyErrorMessage != null && value.isBlank() && hasBeenFocused) {
        Text(
            text = emptyErrorMessage,
            color = darkOrange,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
