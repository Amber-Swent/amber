/* Written by Neha Chakraborty, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload.picture

import androidx.lifecycle.SavedStateHandle
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.media.MemoryDate
import java.io.File
import java.time.Month

private const val DESCRIPTION = "draft_description"
private const val DATE_YEAR = "draft_date_year"
private const val DATE_MONTH = "draft_date_month"
private const val DATE_DAY = "draft_date_day"
private const val SHOWN_IDS = "draft_shown_ids"
private const val PICTURE_PATH = "draft_picture_path"
private const val PICTURE_WIDTH = "draft_picture_width"
private const val PICTURE_HEIGHT = "draft_picture_height"
private const val PLACE_QUERY = "draft_place_query"
private const val PLACE_LAT = "draft_place_lat"
private const val PLACE_LNG = "draft_place_lng"
private const val PLACE_NAME = "draft_place_name"
private const val PLACE_ID = "draft_place_id"

/**
 * Keeps what the user filled in, which Android restores if it kills the app while another one is in
 * front: the camera most of all. Only plain values can be saved this way. Place suggestions aren't
 * kept, as a new search finds them again.
 */
internal fun SavedStateHandle.saveDraft(state: UploadPictureUIState) {
  val picked = state.location.pickedPlace
  set(DESCRIPTION, state.description)
  set(DATE_YEAR, state.date?.year)
  set(DATE_MONTH, state.date?.month?.value)
  set(DATE_DAY, state.date?.day)
  set(SHOWN_IDS, ArrayList(state.shownIds))
  set(PICTURE_PATH, state.pictureFile?.path)
  set(PICTURE_WIDTH, state.pictureWidth)
  set(PICTURE_HEIGHT, state.pictureHeight)
  set(PLACE_QUERY, state.location.query)
  set(PLACE_LAT, picked?.lat)
  set(PLACE_LNG, picked?.lng)
  set(PLACE_NAME, picked?.placeName)
  set(PLACE_ID, state.location.selectedPlaceId)
}

/**
 * Applies a saved draft to [initial], or returns it unchanged if none was saved. A picture whose
 * file is gone is left out, as it could not be uploaded.
 */
internal fun SavedStateHandle.restoreDraft(initial: UploadPictureUIState): UploadPictureUIState {
  if (!contains(DESCRIPTION)) return initial
  val picture = get<String>(PICTURE_PATH)?.let(::File)?.takeIf { it.exists() }
  val hasPickedPlace = listOf(PLACE_LAT, PLACE_LNG, PLACE_NAME).any { get<Any>(it) != null }
  return initial.copy(
      description = get(DESCRIPTION) ?: "",
      date =
          get<Int>(DATE_YEAR)?.let { year ->
            MemoryDate(year, get<Int>(DATE_MONTH)?.let(Month::of), get(DATE_DAY))
          },
      shownIds = get<ArrayList<String>>(SHOWN_IDS).orEmpty().toSet(),
      pictureFile = picture,
      pictureWidth = if (picture != null) get(PICTURE_WIDTH) ?: 0 else 0,
      pictureHeight = if (picture != null) get(PICTURE_HEIGHT) ?: 0 else 0,
      location =
          initial.location.copy(
              query = get(PLACE_QUERY) ?: "",
              pickedPlace =
                  if (hasPickedPlace) GeoLocation(get(PLACE_LAT), get(PLACE_LNG), get(PLACE_NAME))
                  else null,
              selectedPlaceId = get(PLACE_ID),
          ),
  )
}
