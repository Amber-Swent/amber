// this code was written by Alaabrine
// with the aid of AI coding agent
package com.github.se.amber.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.github.se.amber.ui.theme.PADDING_BIG
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.lightOrange

/** Test tags for [AmberHeader]. */
object AmberHeaderTestTags {
  const val LOGO = "amberHeaderLogo"
  const val BRAND_NAME = "amberHeaderBrandName"
}

/**
 * Shared onboarding header: logo placeholder and Amber brand name.
 *
 * Used by the create-profile, caregiver-choice, and invitation-redemption screens so the real logo
 * only has to be plugged in once.
 *
 * @param modifier Modifier applied to the header column.
 */
@Composable
fun AmberHeader(modifier: Modifier = Modifier) {
  Column(
      modifier = modifier,
      horizontalAlignment = Alignment.Start,
      verticalArrangement = Arrangement.spacedBy(PADDING_SMALL),
  ) {
    Box(
        modifier =
            Modifier.size(PADDING_BIG * 2)
                .background(lightOrange, CircleShape)
                .testTag(AmberHeaderTestTags.LOGO),
        contentAlignment = Alignment.Center,
    ) {
      Text(
          text = "logo",
          style = MaterialTheme.typography.bodySmall,
          color = Color.Black,
      )
    }
    Text(
        text = "Amber",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.testTag(AmberHeaderTestTags.BRAND_NAME),
    )
  }
}
