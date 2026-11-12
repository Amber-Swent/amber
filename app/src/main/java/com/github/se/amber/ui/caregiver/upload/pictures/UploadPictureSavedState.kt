// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.upload.pictures

import androidx.lifecycle.SavedStateHandle
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.media.MemoryDate
import java.io.File
import java.time.Month

private const val DESCRIPTION = "form_description"
private const val DATE_YEAR = "form_date_year"
private const val DATE_MONTH = "form_date_month"
private const val DATE_DAY = "form_date_day"
private const val SHOWN_IDS = "form_shown_ids"
private const val PICTURE_PATH = "form_picture_path"
private const val PICTURE_WIDTH = "form_picture_width"
private const val PICTURE_HEIGHT = "form_picture_height"
private const val PLACE_QUERY = "form_place_query"
private const val PLACE_LAT = "form_place_lat"
private const val PLACE_LNG = "form_place_lng"
private const val PLACE_NAME = "form_place_name"
private const val PLACE_ID = "form_place_id"
private const val CAPTURING = "form_capturing"

/**
 * Keeps what the user filled in, and whether the camera is open, which Android restores if it kills
 * the app while another one is in front: the camera most of all. Only plain values can be saved
 * this way. Place suggestions aren't kept, as a new search finds them again.
 * [UploadPictureViewModel] relies on this to bring the form back, so it is not leftover code.
 */
internal fun SavedStateHandle.saveForm(state: UploadPictureUIState) {
  val common = state.common
  val picked = common.location.pickedPlace
  set(DESCRIPTION, common.description)
  set(DATE_YEAR, common.date?.year)
  set(DATE_MONTH, common.date?.month?.value)
  set(DATE_DAY, common.date?.day)
  set(SHOWN_IDS, ArrayList(common.shownIds))
  set(PICTURE_PATH, state.pictureFile?.path)
  set(PICTURE_WIDTH, state.pictureWidth)
  set(PICTURE_HEIGHT, state.pictureHeight)
  set(PLACE_QUERY, common.location.query)
  set(PLACE_LAT, picked?.lat)
  set(PLACE_LNG, picked?.lng)
  set(PLACE_NAME, picked?.placeName)
  set(PLACE_ID, common.location.selectedPlaceId)
  set(CAPTURING, state.isCapturing)
}

/**
 * Applies a saved form to [initial], or returns it unchanged if none was saved. A picture whose
 * file is gone is left out, as it could not be uploaded.
 */
internal fun SavedStateHandle.restoreForm(initial: UploadPictureUIState): UploadPictureUIState {
  if (!contains(DESCRIPTION)) return initial
  val picture = get<String>(PICTURE_PATH)?.let(::File)?.takeIf { it.exists() }
  val hasPickedPlace = listOf(PLACE_LAT, PLACE_LNG, PLACE_NAME).any { get<Any>(it) != null }
  return initial.copy(
      pictureFile = picture,
      pictureWidth = if (picture != null) get(PICTURE_WIDTH) ?: 0 else 0,
      pictureHeight = if (picture != null) get(PICTURE_HEIGHT) ?: 0 else 0,
      // The camera may still be open: its result comes back once the app is restored
      isCapturing = get(CAPTURING) ?: false,
      common =
          initial.common.copy(
              description = get(DESCRIPTION) ?: "",
              date =
                  get<Int>(DATE_YEAR)?.let { year ->
                    MemoryDate(year, get<Int>(DATE_MONTH)?.let(Month::of), get(DATE_DAY))
                  },
              shownIds = get<ArrayList<String>>(SHOWN_IDS).orEmpty().toSet(),
              location =
                  initial.common.location.copy(
                      query = get(PLACE_QUERY) ?: "",
                      pickedPlace =
                          if (hasPickedPlace) {
                            GeoLocation(get(PLACE_LAT), get(PLACE_LNG), get(PLACE_NAME))
                          } else null,
                      selectedPlaceId = get(PLACE_ID),
                  ),
          ),
  )
}
