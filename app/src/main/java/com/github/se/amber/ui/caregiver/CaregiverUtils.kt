package com.github.se.amber.ui.caregiver

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.github.se.amber.ui.caregiver.upload.UploadTestTags
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.PADDING_SMALL
import com.github.se.amber.ui.theme.orange

@Composable
fun DisplayCaregiverTitle(title: String, modifier: Modifier) {
  Spacer(modifier = Modifier.height(PADDING_SMALL))
  Text(
      modifier = modifier.testTag(UploadTestTags.TITLE),
      text = title,
      color = orange,
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
  )
  Spacer(modifier = Modifier.height(PADDING_MEDIUM))
}
