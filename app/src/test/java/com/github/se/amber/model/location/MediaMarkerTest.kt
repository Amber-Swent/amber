// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.location

import com.github.se.amber.model.location.TestPlaces.GENEVA
import com.github.se.amber.model.location.TestPlaces.JUST_ABOVE_MAX_LATITUDE
import com.github.se.amber.model.location.TestPlaces.JUST_INSIDE_SAME_PLACE_RADIUS
import com.github.se.amber.model.location.TestPlaces.LAUSANNE
import com.github.se.amber.model.location.TestPlaces.latitude
import com.github.se.amber.model.location.TestPlaces.longitude
import com.github.se.amber.model.location.TestPlaces.movedNorth
import com.github.se.amber.model.media.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaMarkerTest {

  private val justInsideRadius = JUST_INSIDE_SAME_PLACE_RADIUS

  private fun picture(id: String, location: GeoLocation?) =
      MediaItem.Picture(id = id, location = location)

  private fun List<MediaItem>.pinMedia() = toMediaMarkers().map { it.media }

  @Test
  fun onlyMediaWithARealPointGetAPin() {
    // Which points are real is tested in GeoLocationTest; here, one of each kind is left out
    val atPoint = picture("p1", LAUSANNE)
    val media =
        listOf(
            atPoint,
            picture("p2", null),
            MediaItem.Text(id = "t1"),
            picture("p3", GeoLocation(placeName = "Grandma's house")),
            picture("p4", LAUSANNE.copy(lat = JUST_ABOVE_MAX_LATITUDE)),
            picture("p5", GeoLocation(lat = 0.0, lng = 0.0)),
        )

    assertEquals(listOf(listOf(atPoint)), media.pinMedia())
  }

  @Test
  fun mediaNearAPinsOldestMediaShareItNewestFirstAndPinsDontChain() {
    // Given newest first, as the repository gives them
    val oldest = picture("p1", LAUSANNE)
    val insidePoint = LAUSANNE.movedNorth(justInsideRadius)
    val inside = MediaItem.Text(id = "t1", location = insidePoint)
    val elsewhere = MediaItem.Audio(id = "a1", location = GENEVA)
    // Within the radius of inside, but twice as far from the pin's oldest media: a pin of its own
    val nearNewerOnly = picture("p2", insidePoint.movedNorth(justInsideRadius))

    assertEquals(
        listOf(listOf(nearNewerOnly), listOf(elsewhere), listOf(inside, oldest)),
        listOf(nearNewerOnly, elsewhere, inside, oldest).pinMedia(),
    )
  }

  @Test
  fun aPinKeepsThePointAndIdOfItsOldestMediaAndPinsFollowTheirNewestMedia() {
    val oldest = picture("p1", LAUSANNE)
    val before = listOf(picture("p2", GENEVA), oldest).toMediaMarkers()
    assertEquals(listOf("p2", "p1"), before.map { it.id })

    val newer = picture("p3", LAUSANNE.movedNorth(justInsideRadius))
    val after = listOf(newer, picture("p2", GENEVA), oldest).toMediaMarkers()

    assertEquals(listOf("p1", "p2"), after.map { it.id })
    assertEquals(LAUSANNE.latitude, after[0].lat, 0.0)
    assertEquals(LAUSANNE.longitude, after[0].lng, 0.0)
    assertEquals(listOf(newer, oldest), after[0].media)
    assertNotEquals(after[0].id, after[1].id)
  }

  @Test
  fun aPinTakesThePlaceNameMostOfItsMediaHaveTheNewestOneBetweenEqualsOrNone() {
    fun named(id: String, name: String?) = picture(id, LAUSANNE.copy(placeName = name))

    val home = named("p1", "Home")
    val grandmas = named("p2", "Grandma's house")
    val olderGrandmas = named("p3", "Grandma's house")
    val unnamed = named("p4", null)
    val blank = named("p5", " ")

    assertEquals(
        "Grandma's house",
        listOf(home, grandmas, unnamed, olderGrandmas, blank).toMediaMarkers().single().placeName,
    )
    assertEquals("Home", listOf(home, grandmas).toMediaMarkers().single().placeName)
    assertNull(listOf(unnamed, blank).toMediaMarkers().single().placeName)
  }
}
