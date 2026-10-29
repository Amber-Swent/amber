package com.github.se.amber.ui.onboarding

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.github.se.amber.ui.onboarding.choice.CaregiverChoiceScreen
import com.github.se.amber.ui.onboarding.DividerTestTags.HORIZONTAL_DIVIDER_LEFT
import com.github.se.amber.ui.onboarding.DividerTestTags.HORIZONTAL_DIVIDER_RIGHT
import com.github.se.amber.ui.onboarding.DividerTestTags.ROW
import com.github.se.amber.ui.onboarding.DividerTestTags.TEXT
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.WEIGHT_ONE
import com.github.se.amber.ui.theme.lightGray

/** Test tags for [CaregiverChoiceScreen]. */
object DividerTestTags {
  const val HORIZONTAL_DIVIDER_LEFT = "divider_horitonzal_divider_left"
  const val HORIZONTAL_DIVIDER_RIGHT = "divider_horitonzal_divider_right"
  const val TEXT = "divider_text"
  const val ROW = "divider_container"
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
