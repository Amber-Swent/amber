// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.utils

import android.R.attr.contentDescription
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_ICON_SIZE
import com.github.se.amber.ui.theme.PADDING_MINI

/**
 * Displays an information icon followed by one or more sentences of hint text, in the muted
 * `outline` color unless [color] says otherwise, shared by the screens that guide the user.
 *
 * @param modifier modifier applied to the whole indication, for example its test tag.
 * @param contentDescription description of the information icon, for accessibility.
 * @param sentences the sentences of text shown one under the other, next to the icon.
 * @param color the color of the icon and of the text, e.g. `colorScheme.error` for an error.
 */
@Composable
fun DisplayInformation(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Info,
    contentDescription: String = "",
    sentences: List<String> = emptyList(),
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    size: Dp = CAREGIVER_ICON_SIZE,
    color: Color = MaterialTheme.colorScheme.outline,
) {
  Row(
      modifier = modifier,
      horizontalArrangement = Arrangement.spacedBy(PADDING_MINI),
  ) {
    Icon(
        modifier = Modifier.size(size),
        imageVector = icon,
        contentDescription = contentDescription,
        tint = color,
    )
    Column {
      sentences.forEach { sentence ->
        Text(
            text = sentence,
            style = style,
            color = color,
        )
      }
    }
  }
}

/** Preview of [DisplayInformation] using the Amber application theme. */
@Preview(showBackground = true)
@Composable
internal fun DisplayInformationPreview() {
  AmberAppTheme(dynamicColor = false) {
    Column {
      DisplayInformation(
          contentDescription = "Information",
          sentences = listOf("First line of the hint.", "Second line of the hint."),
      )
      DisplayInformation(
          contentDescription = "Error",
          sentences = listOf("An error message."),
          color = MaterialTheme.colorScheme.error,
      )
    }
  }
}
