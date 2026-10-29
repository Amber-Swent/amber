// this code was written by Alaabrine
// with the aid of AI coding agent
// Modified by Yuhan YANG, with assistance from Cursor.
package com.github.se.amber.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.github.se.amber.R
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.SURFACE_BIG

/**
 * Shared onboarding header: Amber logo and brand name.
 *
 * Used by the create-profile, caregiver-choice, invitation-redemption, and create-care-circle
 * screens so the logo only has to be plugged in once.
 *
 * @param modifier Modifier applied to the header column.
 * @param horizontalAlignment Horizontal alignment of the logo and brand name.
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
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
  }
}

/** Test tags used by [AmberHeader]. */
object AmberHeaderTestTags {
  const val HEADER = "AmberHeader_test_tag_header"
}
