// Written by YANG Yuhan, with assistance from
// Grok (xAI) via Cursor.
package com.github.se.amber.ui.patient

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.github.se.amber.ui.theme.PADDING_BIG
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.orange

object PatientPicturesTestTags {
  const val TITLE = "patient_pictures_title"
}

/**
 * Patient pictures content.
 *
 * @param modifier modifier applied to the screen content.
 */
@Composable
fun PatientPictures(modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxSize().padding(horizontal = PADDING_BIG)) {
    Spacer(modifier = Modifier.height(PADDING_SMALL))
    Text(
        text = "You are on Pictures",
        modifier = Modifier.fillMaxWidth().testTag(PatientPicturesTestTags.TITLE),
        color = orange,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
  }
}
