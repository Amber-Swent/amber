/** Written by camomille299, with assistance from Claude (Anthropic) via claude.ai. */
package com.github.se.amber.ui.media

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.ui.patient.DisplayPatientButton
import com.github.se.amber.ui.patient.PatientUtilsTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PATIENT_BUTTON_HEIGHT_SMALL
import com.github.se.amber.ui.theme.PATIENT_BUTTON_WIDTH_BIG
import com.github.se.amber.ui.theme.PATIENT_PADDING_MINI
import com.github.se.amber.ui.theme.PATIENT_PADDING_SMALL
import com.github.se.amber.ui.theme.WEIGHT_ONE
import com.github.se.amber.ui.theme.WEIGHT_TWO

/**
 * Test tags used to identify the elements of the [MediaCard]. Each tag is made unique by the id of
 * the media it belongs to.
 */
object MediaCardTestTags {
  fun toggleButtonTextShow(id: String) = "media_card_toggle_button_text_show_$id"

  fun toggleButtonTextHide(id: String) = "media_card_toggle_button_text_hide_$id"

  fun date(id: String) = "media_card_date_$id"

  fun loading(id: String) = "media_card_loading_$id"

  fun description(id: String) = "media_card_description_$id"

  fun addedBy(id: String) = "media_card_added_by_$id"

  fun relatedList(id: String) = "media_card_related_list_$id"

  fun relatedTitle(id: String) = "media_card_related_title_$id"

  fun relatedItem(id: String, index: Int) = "media_card_related_item_${id}_$index"

  fun location(id: String) = "media_card_location_$id"
}

/**
 * Displays the card related to the Media, including the date, the description, the location and the
 * persons related to it
 *
 * This function must only be used on scrollable screens.
 *
 * @param modifier modifier to apply to the card
 * @param media the media item we want to apply the description of. By default a picture.
 * @param testTag test tag of the card
 * @param mediaCardViewModel view model of the card, which loads the nicknames of the author and of
 *   the persons related to the media. One instance per media id.
 */
@Composable
fun MediaCard(
    modifier: Modifier = Modifier,
    media: MediaItem = MediaItem.Picture(),
    testTag: String = PatientUtilsTestTags.TITLE,
    mediaCardViewModel: MediaCardViewModel = viewModel(key = media.id),
) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  val uiState by mediaCardViewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  LaunchedEffect(media, expanded) { if (expanded) mediaCardViewModel.load(media) }

  LaunchedEffect(uiState.errorMsg) {
    uiState.errorMsg?.let {
      Toast.makeText(context, it, Toast.LENGTH_LONG).show()
      mediaCardViewModel.clearErrorMsg()
    }
  }

  Column(modifier = modifier.fillMaxWidth().testTag(testTag)) {
    // see+/- button and date
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      val textToDisplay: String = if (expanded) "hide infos" else "show infos"
      val actualTestTag =
          if (expanded) MediaCardTestTags.toggleButtonTextHide(media.id)
          else MediaCardTestTags.toggleButtonTextShow(media.id)
      DisplayPatientButton(
          modifier = Modifier.weight(WEIGHT_ONE).padding(start = PATIENT_PADDING_MINI),
          buttonHeight = PATIENT_BUTTON_HEIGHT_SMALL,
          buttonWidth = PATIENT_BUTTON_WIDTH_BIG,
          text = textToDisplay,
          textStyle = MaterialTheme.typography.titleMedium,
          onClick = { expanded = !expanded },
          testTag = actualTestTag,
      )
      Column(
          horizontalAlignment = Alignment.End,
          modifier =
              Modifier.weight(WEIGHT_TWO).align(Alignment.Top).padding(end = PATIENT_PADDING_MINI),
      ) {
        Text(
            text = memoryDateIntoString(media.date),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag(MediaCardTestTags.date(media.id)),
        )
        // to display the location if it is not null
        media.location?.let { location ->
          Text(
              text = geoLocationIntoString(location),
              modifier = Modifier.testTag(MediaCardTestTags.location(media.id)),
          )
        }
      }
    }
    // content that pop when the user click on the "- see more" button
    AnimatedVisibility(visible = expanded) {
      when {
        uiState.isLoading ->
            CircularProgressIndicator(
                modifier = Modifier.testTag(MediaCardTestTags.loading(media.id))
            )
        else -> {
          Column(
              verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL),
              modifier = Modifier.padding(PATIENT_PADDING_SMALL),
          ) {
            // to display the description and name of the people who wrote it
            DisplayBox() {
              Column(
                  verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL),
                  modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                    text = media.description,
                    modifier = Modifier.testTag(MediaCardTestTags.description(media.id)),
                )
                Text(
                    text = "Added by " + uiState.addedByNickname,
                    modifier =
                        Modifier.align(Alignment.End).testTag(MediaCardTestTags.addedBy(media.id)),
                )
              }
            }
            // to display the persons related to this media if there are any
            if (uiState.relatedNicknames.isNotEmpty()) {
              DisplayBox() {
                Column {
                  Text(
                      text = "Memory shared with",
                      modifier = Modifier.testTag(MediaCardTestTags.relatedTitle(media.id)),
                  )
                  LazyRow(
                      horizontalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL),
                      modifier = Modifier.testTag(MediaCardTestTags.relatedList(media.id)),
                  ) {
                    itemsIndexed(uiState.relatedNicknames) { index, nickname ->
                      Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val isLast = index == uiState.relatedNicknames.lastIndex
                        Text(
                            text = if (isLast) nickname else "$nickname,",
                            modifier =
                                Modifier.testTag(MediaCardTestTags.relatedItem(media.id, index)),
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun DisplayMediaCardPreview() {
  AmberAppTheme { MediaCard() }
}
