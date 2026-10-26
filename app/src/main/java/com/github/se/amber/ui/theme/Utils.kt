package com.github.se.amber.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign

/**
 * Displays two horizontal dividers separated by a centered "or" label.
 *
 * @param modifier modifier applied to the divider row.
 * @param style text style used for the "or" label.
 */
@Composable
fun OrDivider(
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
) {
  Row(
      modifier = modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
  ) {
    HorizontalDivider(
        modifier = Modifier.weight(WEIGHT_ONE),
        color = lightGray,
    )

    Text(
        text = "or",
        color = MaterialTheme.colorScheme.outline,
        style = style,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = CAREGIVER_PADDING_SMALL),
    )

    HorizontalDivider(
        modifier = Modifier.weight(WEIGHT_ONE),
        color = lightGray,
    )
  }
}
