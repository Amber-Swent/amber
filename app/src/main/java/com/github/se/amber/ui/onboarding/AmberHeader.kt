// this code was written by Alaabrine
// with the aid of AI coding agent
// Modified by Yuhan YANG, with assistance from Cursor.
package com.github.se.amber.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.github.se.amber.R
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.SURFACE_BIG

/**
 * Shared onboarding header: Amber logo and brand name.
 *
 * Used by the create-profile, caregiver-choice, invitation-redemption, and create-care-circle
 * screens so the logo only has to be plugged in once. The brand name is centered across the logo
 * width, so it sits under the icon.
 *
 * @param modifier Modifier applied to the header column.
 * @param horizontalAlignment Horizontal alignment of the logo and brand name in a wider parent.
 */
@Composable
fun AmberHeader(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
  Column(
      modifier = modifier.testTag(AmberHeaderTestTags.HEADER),
      horizontalAlignment = horizontalAlignment,
      verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL),
  ) {
    Image(
        painter = painterResource(R.drawable.amber_icon),
        contentDescription = "Amber logo",
        modifier = Modifier.size(SURFACE_BIG),
    )
    Text(
        text = "Amber",
        // Same width as the logo, so the word sits under the icon rather than the parent.
        modifier = Modifier.width(SURFACE_BIG),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
    )
  }
}

/** Test tags used by [AmberHeader]. */
object AmberHeaderTestTags {
  const val HEADER = "AmberHeader_test_tag_header"
}
