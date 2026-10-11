package com.github.se.amber.ui.caregiver

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.orange

/**
 * Displays the title for caregiver's [Screen]
 *
 * @param title title to display
 * @param modifier modifier to apply to the text (can for e.g. contains the necessary TestTag)
 */
@Composable
fun DisplayCaregiverTitle(title: String, modifier: Modifier) {
  Spacer(modifier = Modifier.height(PADDING_SMALL))
  Text(
      modifier = modifier,
      text = title,
      color = orange,
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
  )
  Spacer(modifier = Modifier.height(PADDING_MEDIUM))
}
