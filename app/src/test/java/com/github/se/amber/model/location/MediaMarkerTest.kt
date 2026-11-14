// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.location

import com.github.se.amber.model.location.TestPlaces.GENEVA
import com.github.se.amber.model.location.TestPlaces.JUST_ABOVE_MAX_LATITUDE
import com.github.se.amber.model.location.TestPlaces.LAUSANNE
import com.github.se.amber.model.location.TestPlaces.cellCenter
import com.github.se.amber.model.location.TestPlaces.latitude
import com.github.se.amber.model.location.TestPlaces.longitude
import com.github.se.amber.model.location.TestPlaces.movedNorthByCells
import com.github.se.amber.model.media.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaMarkerTest {

  // Moves measured from a cell's center: a fifth of a cell stays in it, half a cell reaches its
  // edge, and a tenth of a cell from the edge is on one side of it or the other
  private val withinCell = 0.2
  private val toCellEdge = 0.5
  private val pastEdge = 0.1
  private val oneCell = 1.0

  private fun picture(id: String, location: GeoLocation?) =
      MediaItem.Picture(id = id, location = location)

  private fun List<MediaItem>.pinMedia() = toMediaMarkers().map { it.media }

  @Test
  fun `only media with a real point get a pin`() {
    // Which points are real is tested in GeoLocationTest; here, one of each kind is left out
    val atPoint = picture("p1", LAUSANNE)
    val media =
        listOf(
            atPoint,
            picture("p2", null),
            MediaItem.Text(id = "t1"),
            picture("p3", GeoLocation(placeName = "Grandma's house")),
            picture("p4", LAUSANNE.copy(lat = JUST_ABOVE_MAX_LATITUDE)),
        )

    assertEquals(listOf(listOf(atPoint)), media.pinMedia())
  }

  @Test
  fun `media share a pin by grid cell, newest first, and a cell edge splits them`() {
    val center = LAUSANNE.cellCenter()
    val newest = picture("p1", center)
    val elsewhere = MediaItem.Audio(id = "a1", location = GENEVA)
    val sameCell = MediaItem.Text(id = "t1", location = center.movedNorthByCells(withinCell))
    val nextCell = picture("p2", center.movedNorthByCells(oneCell))
    assertEquals(
        listOf(listOf(newest, sameCell), listOf(elsewhere), listOf(nextCell)),
        listOf(newest, elsewhere, sameCell, nextCell).pinMedia(),
    )

    val edge = center.movedNorthByCells(toCellEdge)
    val belowEdge = picture("p3", edge.movedNorthByCells(-pastEdge))
    val aboveEdge = picture("p4", edge.movedNorthByCells(pastEdge))
    assertEquals(
        listOf(listOf(belowEdge), listOf(aboveEdge)),
        listOf(belowEdge, aboveEdge).pinMedia(),
    )
  }

  @Test
  fun `pins are in the order of their newest media, at the point of their newest media`() {
    val newestPoint = GENEVA.cellCenter().movedNorthByCells(withinCell)
    val markers =
        listOf(
                picture("p1", newestPoint),
                picture("p2", LAUSANNE),
                picture("p3", GENEVA.cellCenter()),
            )
            .toMediaMarkers()

    assertEquals(listOf("Geneva", "Lausanne"), markers.map { it.placeName })
    assertEquals(newestPoint.latitude, markers[0].lat, 0.0)
    assertEquals(newestPoint.longitude, markers[0].lng, 0.0)
  }

  @Test
  fun `a pin takes the first non-blank place name of its media, or none`() {
    val noName = picture("p1", LAUSANNE.copy(placeName = null))
    val blankName = picture("p2", LAUSANNE.copy(placeName = " "))
    val named = picture("p3", LAUSANNE.copy(placeName = "Café de la Gare"))

    assertEquals(
        "Café de la Gare",
        listOf(noName, blankName, named).toMediaMarkers().single().placeName,
    )
    assertNull(listOf(noName, blankName).toMediaMarkers().single().placeName)
  }

  @Test
  fun `a spot keeps its id when its media change, and another spot has another id`() {
    val before = listOf(picture("p1", LAUSANNE)).toMediaMarkers().single()
    val after = listOf(picture("p2", LAUSANNE), picture("p1", LAUSANNE)).toMediaMarkers().single()

    assertEquals(before.id, after.id)
    assertNotEquals(before.id, listOf(picture("p3", GENEVA)).toMediaMarkers().single().id)
  }
}
