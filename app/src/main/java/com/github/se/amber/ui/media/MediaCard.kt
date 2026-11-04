package com.github.se.amber.ui.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.ui.patient.PatientUtilsTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.BORDER_THICKNESS
import com.github.se.amber.ui.theme.PADDING_MINI
import com.github.se.amber.ui.theme.PATIENT_PADDING_MINI
import com.github.se.amber.ui.theme.PATIENT_PADDING_SMALL
import com.github.se.amber.ui.theme.ROUND_CORNER
import java.util.Locale

/**
 * Displays the card related to the Media, including the date, the description, the location and the
 * persons related to it
 *
 * This functions must only be used on scrollable screens.
 *
 * @param modifier modifier to apply to the card
 * @param media the media item we want to apply the description of. By default a picture.
 * @param testTag test tag of the card
 */
@Composable
fun MediaCard(
    modifier: Modifier = Modifier,
    media: MediaItem = MediaItem.Picture(),
    testTag: String = PatientUtilsTestTags.TITLE,
    mediaCardViewModel: MediaCardViewModel = viewModel(),
) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  val uiState by mediaCardViewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(media) { mediaCardViewModel.load(media) }

  Column(modifier = modifier.fillMaxWidth()) {
    // see+/- button and date
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Button(
          onClick = { expanded = !expanded },
          shape = RoundedCornerShape(ROUND_CORNER),
          colors =
              ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.tertiary,
                  contentColor = MaterialTheme.colorScheme.onTertiary,
              ),
      ) {
        Text(
            text = if (expanded) "- see less" else "+ see more",
            style = MaterialTheme.typography.titleMedium,
        )
      }
      Text(
          text = memoryDateIntoString(media.date),
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.align(Alignment.Top).padding(end = PATIENT_PADDING_MINI),
      )
    }
    // content that pop when the user click on the "- see more" button
    AnimatedVisibility(visible = expanded) {
      Column(
          verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL),
          modifier = Modifier.padding(PATIENT_PADDING_SMALL),
      ) {
        // to display the description and name of the people who wrote it
        OrangeBox() {
          Column(
              verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL),
              modifier = Modifier.fillMaxWidth(),
          ) {
            Text(media.description)
            Text(
                text = "Added by " + uiState.addedByNickname,
                modifier = Modifier.align(Alignment.End),
            )
          }
        }
        // to display the persons related to this media if there are any
        if (uiState.relatedNicknames.isNotEmpty()) {
          OrangeBox() {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL)) {
              items(uiState.relatedNicknames) { nickname ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(nickname) }
              }
            }
          }
        }
        // to display the location if it is not null
        media.location?.let { location -> OrangeBox { Text(geoLocationIntoString(location)) } }
      }
    }
  }
}

/** transform the MemoryDate into a string */
private fun memoryDateIntoString(date: MemoryDate?): String {
  if (date == null) return "Unknown date"
  val month = date.month?.value ?: return "%04d".format(date.year)
  val day = date.day ?: return "%02d/%04d".format(month, date.year)
  return "%02d/%02d/%04d".format(day, month, date.year)
}

/** transform a GeoLocation into a string */
/** transform a GeoLocation into a string */
private fun geoLocationIntoString(location: GeoLocation): String {
  val name = location.placeName
  if (!name.isNullOrBlank()) return name

  val lat = location.lat
  val lng = location.lng
  if (lat != null && lng != null) return "%.4f, %.4f".format(Locale.US, lat, lng)

  return "Unknown location"
}

/** create the box composable with visible outlines */
@Composable
private fun OrangeBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
  Surface(
      modifier = modifier.fillMaxWidth(),
      shape = RoundedCornerShape(ROUND_CORNER),
      border = BorderStroke(BORDER_THICKNESS, MaterialTheme.colorScheme.secondary),
  ) {
    Box(Modifier.padding(PADDING_MINI)) { content() }
  }
}

@Preview(showBackground = true)
@Composable
fun DisplayMediaCardPreview() {
  AmberAppTheme { MediaCard() }
}
