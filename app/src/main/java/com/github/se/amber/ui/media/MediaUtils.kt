// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.media

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.model.user.Person
import com.github.se.amber.ui.patient.DisplayPatientButton
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.BORDER_THICKNESS
import com.github.se.amber.ui.theme.PADDING_MINI
import com.github.se.amber.ui.theme.PATIENT_BUTTON_HEIGHT_SMALL
import com.github.se.amber.ui.theme.ROUND_CORNER
import java.util.Locale

object MediaUtilsTestTags {
  const val DISPLAY_BOX = "media_utils_display_box"
}

/**
 * Transforms a [MemoryDate] into a displayable string.
 *
 * The format depends on the precision of the date:
 * - year only: "yyyy"
 * - year and month: "MM/yyyy"
 * - full date: "dd/MM/yyyy"
 *
 * A media without a date has no [MemoryDate]: the screen shows its own translated "Unknown date".
 *
 * @param date the date to transform
 * @return the string representation of the date
 */
fun memoryDateIntoString(date: MemoryDate): String {
  val month = date.month?.value ?: return "%04d".format(date.year)
  val day = date.day ?: return "%02d/%04d".format(month, date.year)
  return "%02d/%02d/%04d".format(day, month, date.year)
}

/**
 * Transforms a [GeoLocation] into a displayable string.
 *
 * It uses the place name if there is one, otherwise the coordinates (lat, lng).
 *
 * @param location the location to transform
 * @return the string representation of the location, or null when it has neither a name nor
 *   coordinates. The screen then shows its own translated "Unknown location".
 */
fun geoLocationIntoString(location: GeoLocation): String? {
  val name = location.placeName
  if (!name.isNullOrBlank()) return name

  val lat = location.lat
  val lng = location.lng
  if (lat != null && lng != null) return "%.4f, %.4f".format(Locale.US, lat, lng)

  return null
}

/**
 * Displays a box with visible orange outlines around its content.
 *
 * @param modifier modifier to apply to the box
 * @param content content displayed inside the box
 */
@Composable
fun DisplayBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
  Surface(
      modifier = modifier.fillMaxWidth(),
      shape = RoundedCornerShape(ROUND_CORNER),
      border = BorderStroke(BORDER_THICKNESS, MaterialTheme.colorScheme.outline),
  ) {
    Box(Modifier.padding(PADDING_MINI)) { content() }
  }
}

/**
 * Displays a clickable button for a person related to a media.
 *
 * @param person the person to display
 * @param onClick called with the id of the person when the button is clicked
 * @param modifier modifier to apply to the button
 */
@Composable
fun DisplayRelatedPersonButton(
    modifier: Modifier = Modifier,
    person: Person = Person(),
    onClick: (String) -> Unit = {},
    testTag: String? = null,
) {
  val nameToUse = person.nickname?.takeIf { it.isNotBlank() } ?: person.firstName
  // the column is already placed here in advance for when the profile picture will be implemented
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    DisplayPatientButton(
        modifier = modifier.padding(PADDING_MINI),
        buttonHeight = PATIENT_BUTTON_HEIGHT_SMALL,
        outlined = true,
        fillWidth = false,
        text = nameToUse,
        textStyle = MaterialTheme.typography.titleMedium,
        onClick = { onClick(person.id) },
        testTag = testTag,
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun DisplayRelatedPersonButtonPreview() {
  AmberAppTheme { DisplayRelatedPersonButton(person = Person(id = "1", firstName = "Alice")) }
}
