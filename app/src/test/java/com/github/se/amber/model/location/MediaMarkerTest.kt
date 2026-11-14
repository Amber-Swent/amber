// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.location

import com.github.se.amber.model.media.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaMarkerTest {

  private val lausanne = GeoLocation(lat = 46.5197, lng = 6.6323, placeName = "Lausanne")
  private val geneva = GeoLocation(lat = 46.2044, lng = 6.1432, placeName = "Geneva")

  private fun picture(id: String, location: GeoLocation?) =
      MediaItem.Picture(id = id, location = location)

  @Test
  fun `only media at a real point get a pin, up to the limits of the coordinates`() {
    val atPoint = picture("p1", lausanne)
    val atLimits = picture("p2", GeoLocation(lat = -90.0, lng = 180.0))
    val media =
        listOf(
            atPoint,
            picture("p3", null),
            MediaItem.Text(id = "t1"),
            picture("p4", GeoLocation(placeName = "Grandma's house")),
            picture("p5", GeoLocation(lat = 46.5)),
            picture("p6", GeoLocation(lat = 91.0, lng = 6.6)),
            picture("p7", GeoLocation(lat = Double.NaN, lng = 6.6)),
            picture("p8", GeoLocation(lat = 46.5, lng = Double.POSITIVE_INFINITY)),
            atLimits,
        )

    assertEquals(listOf(listOf(atPoint), listOf(atLimits)), media.toMediaMarkers().map { it.media })
  }

  @Test
  fun `media at the same spot share one pin, newest first`() {
    val newer = picture("p1", lausanne)
    val other = MediaItem.Audio(id = "a1", location = geneva)
    val older = MediaItem.Text(id = "t1", location = lausanne.copy(placeName = null))

    val markers = listOf(newer, other, older).toMediaMarkers()

    assertEquals(listOf(listOf(newer, older), listOf(other)), markers.map { it.media })
  }

  @Test
  fun `points in the same 5-decimal cell share a pin, points in the next cell don't`() {
    val base = picture("p1", GeoLocation(lat = 46.519700, lng = 6.632300))
    val sameCell = picture("p2", GeoLocation(lat = 46.519701, lng = 6.632304))
    val nextCell = picture("p3", GeoLocation(lat = 46.519800, lng = 6.632300))

    val markers = listOf(base, sameCell, nextCell).toMediaMarkers()

    assertEquals(listOf(listOf(base, sameCell), listOf(nextCell)), markers.map { it.media })
  }

  @Test
  fun `points closer than 1 m but on both sides of a cell edge get two pins`() {
    // 46.519704 rounds down and 46.519706 up: about 20 cm apart, in two cells
    val below = picture("p1", GeoLocation(lat = 46.519704, lng = 6.6323))
    val above = picture("p2", GeoLocation(lat = 46.519706, lng = 6.6323))

    assertEquals(2, listOf(below, above).toMediaMarkers().size)
  }

  @Test
  fun `pins are in the order of their newest media, at the point of their newest media`() {
    val newestGeneva = picture("p1", geneva.copy(lat = 46.204401))
    val markers =
        listOf(newestGeneva, picture("p2", lausanne), picture("p3", geneva)).toMediaMarkers()

    assertEquals(listOf("Geneva", "Lausanne"), markers.map { it.placeName })
    assertEquals(46.204401, markers[0].lat, 0.0)
    assertEquals(geneva.lng!!, markers[0].lng, 0.0)
  }

  @Test
  fun `a pin takes the first non-blank place name of its media, or none`() {
    val unnamed = picture("p1", lausanne.copy(placeName = " "))
    val named = picture("p2", lausanne.copy(placeName = "Café de la Gare"))

    assertEquals("Café de la Gare", listOf(unnamed, named).toMediaMarkers().single().placeName)
    assertNull(listOf(unnamed).toMediaMarkers().single().placeName)
  }

  @Test
  fun `a spot keeps its id when its media change, and another spot has another id`() {
    val before = listOf(picture("p1", lausanne)).toMediaMarkers().single()
    val after = listOf(picture("p2", lausanne), picture("p1", lausanne)).toMediaMarkers().single()

    assertEquals(before.id, after.id)
    assertNotEquals(before.id, listOf(picture("p3", geneva)).toMediaMarkers().single().id)
  }
}
