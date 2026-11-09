// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
// Moved by LaMaier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.caregiver.upload.media

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.github.se.amber.R
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.ui.caregiver.DisplayTextField
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.DIVIDER_THICKNESS
import com.github.se.amber.ui.theme.MAX_CHAR_PLACE_SEARCH

/** Test tags used by the "Location - optional" section of the upload screens. */
object UploadMediaLocationTestTags {
  const val SECTION = "upload_media_location_section"
  const val SAVED_PLACE = "upload_media_location_saved_place"
  const val SEARCH_FIELD = "upload_media_location_search_field"
  const val SUGGESTION = "upload_media_location_suggestion"
  const val SEARCHING = "upload_media_location_searching"
  const val NO_RESULT = "upload_media_location_no_result"
  const val TOO_SHORT = "upload_media_location_too_short"
  const val SEARCH_FAILED = "upload_media_location_search_failed"
  const val RETRY_SEARCH = "upload_media_location_retry_search"
}

/**
 * Displays where the media was captured: chips for the circle's saved places, and a field to search
 * any other real place, with the matching suggestions to pick from.
 *
 * @param savedPlaceNamesById The circle's saved places: each saved place's ID mapped to its display
 *   name ([com.github.se.amber.model.location.Place.name]).
 * @param state The search, its suggestions and the picked place.
 * @param onSavedPlaceClick The callback used to pick or drop a saved place.
 * @param onQueryChange The callback invoked when the searched text changes.
 * @param onSuggestionClick The callback used to pick a suggested place.
 * @param onRetrySearch The callback used to search the typed place again after its search failed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DisplayLocation(
    savedPlaceNamesById: Map<String, String> = emptyMap(),
    state: MediaLocationUIState = MediaLocationUIState(),
    onSavedPlaceClick: (String) -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onSuggestionClick: (GeoLocation) -> Unit = {},
    onRetrySearch: () -> Unit = {},
) {
  Column(
      modifier = Modifier.fillMaxWidth().testTag(UploadMediaLocationTestTags.SECTION),
      verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_SMALL),
  ) {
    Text(
        stringResource(R.string.locationTitle),
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
              modifier = Modifier.testTag(UploadMediaLocationTestTags.SAVED_PLACE),
          )
        }
      }
    }
    Column(modifier = Modifier.testTag(UploadMediaLocationTestTags.SEARCH_FIELD)) {
      DisplayTextField(
          value = state.query,
          onValueChange = onQueryChange,
          label = stringResource(R.string.locationSearchLabel),
          placeholder = stringResource(R.string.locationSearchPlaceholder),
          maxChar = MAX_CHAR_PLACE_SEARCH,
          singleLine = true,
      )
    }
    // Only shown while a typed place is still to be picked. The hints block the upload until the
    // user acts: the "search failed" one stays after the error toast is gone.
    if (state.pickedPlace == null && state.query.isNotBlank()) {
      when {
        state.isSearching ->
            DisplayLocationHint(
                stringResource(R.string.locationSearching),
                UploadMediaLocationTestTags.SEARCHING,
            )
        !state.isLongEnoughToSearch ->
            DisplayLocationHint(
                pluralStringResource(
                    R.plurals.locationQueryTooShort,
                    MIN_PLACE_QUERY_LENGTH,
                    MIN_PLACE_QUERY_LENGTH,
                ),
                UploadMediaLocationTestTags.TOO_SHORT,
            )
        state.searchFailed ->
            Column {
              DisplayLocationHint(
                  stringResource(R.string.locationSearchFailed),
                  UploadMediaLocationTestTags.SEARCH_FAILED,
              )
              TextButton(
                  onClick = onRetrySearch,
                  modifier = Modifier.testTag(UploadMediaLocationTestTags.RETRY_SEARCH),
              ) {
                Text(stringResource(R.string.locationTryAgain))
              }
            }
        state.suggestions.isEmpty() ->
            DisplayLocationHint(
                stringResource(R.string.locationNoPlaceFound),
                UploadMediaLocationTestTags.NO_RESULT,
            )
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
              .testTag(UploadMediaLocationTestTags.SUGGESTION),
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
