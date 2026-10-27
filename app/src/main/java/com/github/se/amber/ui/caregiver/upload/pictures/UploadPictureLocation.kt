// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.caregiver.upload.picture

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.ui.caregiver.DisplayTextField
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.DIVIDER_THICKNESS
import com.github.se.amber.ui.theme.MAX_CHAR_PLACE_SEARCH

/** Test tags used by the "Location - optional" section of the Upload Picture screen. */
object UploadPictureLocationTestTags {
  const val SECTION = "UploadPictureLocation_test_tag_section"
  const val SAVED_PLACE = "UploadPictureLocation_test_tag_saved_place"
  const val SEARCH_FIELD = "UploadPictureLocation_test_tag_search_field"
  const val SUGGESTION = "UploadPictureLocation_test_tag_suggestion"
  const val SEARCHING = "UploadPictureLocation_test_tag_searching"
  const val NO_RESULT = "UploadPictureLocation_test_tag_no_result"
  const val SEARCH_FAILED = "UploadPictureLocation_test_tag_search_failed"
}

/** Shown when a typed place matches nothing: the upload stays blocked until the user acts. */
internal const val NO_PLACE_FOUND_HINT =
    "No places found. Try another search, or clear the field to upload without a location."

/** Shown when the search itself failed, e.g. offline: the toast with the error is gone by then. */
internal const val PLACE_SEARCH_FAILED_HINT =
    "Couldn't search places. Check your connection, or clear the field to upload without a location."

/**
 * Displays where the picture was taken: chips for the circle's saved places, and a field to search
 * any other real place, with the matching suggestions to pick from.
 *
 * @param savedPlaceNamesById The circle's saved places: each saved place's ID mapped to its display
 *   name ([com.github.se.amber.model.location.Place.name]).
 * @param state The search, its suggestions and the picked place.
 * @param onSavedPlaceClick The callback used to pick or drop a saved place.
 * @param onQueryChange The callback invoked when the searched text changes.
 * @param onSuggestionClick The callback used to pick a suggested place.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DisplayLocation(
    savedPlaceNamesById: Map<String, String> = emptyMap(),
    state: PictureLocationUIState = PictureLocationUIState(),
    onSavedPlaceClick: (String) -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onSuggestionClick: (GeoLocation) -> Unit = {},
) {
  Column(
      modifier = Modifier.fillMaxWidth().testTag(UploadPictureLocationTestTags.SECTION),
      verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL),
  ) {
    Text(
        "Location - optional",
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.bodyLarge,
    )
    if (savedPlaceNamesById.isNotEmpty()) {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL)) {
        savedPlaceNamesById.forEach { (id, name) ->
          FilterChip(
              selected = id == state.selectedPlaceId,
              onClick = { onSavedPlaceClick(id) },
              label = { Text(name) },
              colors = selectableChipColors(),
              modifier = Modifier.testTag(UploadPictureLocationTestTags.SAVED_PLACE),
          )
        }
      }
    }
    Column(modifier = Modifier.testTag(UploadPictureLocationTestTags.SEARCH_FIELD)) {
      DisplayTextField(
          value = state.query,
          onValueChange = onQueryChange,
          label = "Another place",
          placeholder = "Search a city, an address...",
          maxChar = MAX_CHAR_PLACE_SEARCH,
          singleLine = true,
      )
    }
    // Only shown while a typed place is still to be picked
    if (state.location == null && state.query.isNotBlank()) {
      when {
        state.isSearching ->
            DisplayLocationHint("Searching…", UploadPictureLocationTestTags.SEARCHING)
        state.searchFailed ->
            DisplayLocationHint(
                PLACE_SEARCH_FAILED_HINT,
                UploadPictureLocationTestTags.SEARCH_FAILED,
            )
        state.suggestions.isEmpty() ->
            DisplayLocationHint(NO_PLACE_FOUND_HINT, UploadPictureLocationTestTags.NO_RESULT)
        else ->
            Column {
              state.suggestions.forEachIndexed { index, place ->
                if (index > 0) HorizontalDivider(thickness = DIVIDER_THICKNESS)
                DisplayPlaceSuggestion(place = place, onClick = { onSuggestionClick(place) })
              }
            }
      }
    }
  }
}

/**
 * Displays one place suggested by the search, to tap to pick it.
 *
 * @param place The suggested place.
 * @param onClick The callback used to pick this place.
 */
@Composable
internal fun DisplayPlaceSuggestion(place: GeoLocation = GeoLocation(), onClick: () -> Unit = {}) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clickable(onClick = onClick)
              .padding(CAREGIVER_PADDING_SMALL)
              .testTag(UploadPictureLocationTestTags.SUGGESTION),
      horizontalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
        imageVector = Icons.Outlined.Place,
        contentDescription = null, // the place name next to it says it all
        tint = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = place.placeName.orEmpty(),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyMedium,
    )
  }
}

/**
 * Displays a short status line under the place search.
 *
 * @param text The status shown to the user.
 * @param tag The test tag of this status.
 */
@Composable
internal fun DisplayLocationHint(text: String = "", tag: String = "") {
  Text(
      text = text,
      color = MaterialTheme.colorScheme.onBackground,
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.padding(CAREGIVER_PADDING_SMALL).testTag(tag),
  )
}
