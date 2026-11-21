// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.firestore

import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.location.MAX_LATITUDE
import com.github.se.amber.model.location.MAX_LONGITUDE
import com.github.se.amber.model.location.Place
import com.github.se.amber.model.location.TestPlaces.JUST_ABOVE_MAX_LATITUDE
import com.github.se.amber.model.location.TestPlaces.JUST_BELOW_MIN_LONGITUDE
import com.github.se.amber.model.location.TestPlaces.LAUSANNE

/**
 * Places, valid or not, that the app's check (isValidToSave) and the security rules must judge the
 * same way. The rules tests run every one through both.
 */
object SavedPlaceCases {

  /** A valid place to start from. */
  val HOME = Place(id = "home", name = "Home", location = LAUSANNE)

  private fun at(lat: Double?, lng: Double?) =
      HOME.copy(location = GeoLocation(lat = lat, lng = lng, placeName = LAUSANNE.placeName))

  /** Each place, by what is special about it. */
  val ALL: Map<String, Place> =
      mapOf(
          "valid" to HOME,
          "no place name, as for I'm here now" to
              HOME.copy(location = LAUSANNE.copy(placeName = null)),
          "at the largest latitude and smallest longitude" to at(MAX_LATITUDE, -MAX_LONGITUDE),
          "at the smallest latitude and largest longitude" to at(-MAX_LATITUDE, MAX_LONGITUDE),
          "empty name" to HOME.copy(name = ""),
          "blank name" to HOME.copy(name = "  "),
          "a place name only" to HOME.copy(location = GeoLocation(placeName = "Home")),
          "no longitude" to at(LAUSANNE.lat, null),
          "latitude above the largest" to at(JUST_ABOVE_MAX_LATITUDE, LAUSANNE.lng),
          "latitude below the smallest" to at(-JUST_ABOVE_MAX_LATITUDE, LAUSANNE.lng),
          "longitude below the smallest" to at(LAUSANNE.lat, JUST_BELOW_MIN_LONGITUDE),
          "longitude above the largest" to at(LAUSANNE.lat, -JUST_BELOW_MIN_LONGITUDE),
          "the point a lost position gets, (0, 0)" to at(0.0, 0.0),
      )

  /** [place] as Firestore stores it, without the null fields. */
  fun toFields(place: Place): Map<String, Any> =
      mapOf(
          "id" to place.id,
          "name" to place.name,
          "location" to
              listOfNotNull(
                      place.location.lat?.let { "lat" to it },
                      place.location.lng?.let { "lng" to it },
                      place.location.placeName?.let { "placeName" to it },
                  )
                  .toMap(),
      )
}
