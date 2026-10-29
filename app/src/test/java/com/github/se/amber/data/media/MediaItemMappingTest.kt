// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
// Modified with assistance from OpenAI Codex.
package com.github.se.amber.data.media

import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaStatus
import com.github.se.amber.model.media.MemoryDate
import java.time.Month
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Checks how a [MediaItem] is turned into stored fields and back. */
class MediaItemMappingTest {

  private val picture =
      MediaItem.Picture(
          id = "m1",
          authorId = "u1",
          storagePath = "careCircles/c1/media/m1.jpg",
          createdAt = 1_700_000_000_000L,
          description = "Grandma's garden",
          date = MemoryDate(year = 1974, month = Month.JULY),
          location = GeoLocation(lat = 46.5, lng = 6.6, placeName = "Lausanne"),
          shownIds = listOf("u2", "p1"),
          authorizedIds = listOf("u3"),
          status = MediaStatus.PENDING,
          width = 4032,
          height = 3024,
      )

  private val audio =
      MediaItem.Audio(
          id = "m2",
          authorId = "u1",
          storagePath = "careCircles/c1/media/m2.m4a",
          createdAt = 1_700_000_000_001L,
          durationMs = 93_000L,
          transcript = "Hello Arthur",
      )

  private val text =
      MediaItem.Text(id = "m3", authorId = "u1", createdAt = 1_700_000_000_002L, text = "Hi!")

  @Test
  fun everyKindOfMediaComesBackUnchanged() {
    for (item in listOf(picture, audio, audio.copy(transcript = null), text)) {
      assertEquals(item, mediaItemFromMap(item.id, item.toMap()))
    }
  }

  @Test
  fun monthIsStoredAsAnIntegerAndReadAsMonth() {
    val storedDate = picture.toMap()["date"] as Map<*, *>
    assertEquals(7, storedDate["month"])
    assertEquals(
        Month.JULY,
        (mediaItemFromMap("m1", picture.toMap()) as MediaItem.Picture).date?.month,
    )
  }

  @Test
  fun invalidStoredMonthCannotBeRead() {
    val fields = picture.toMap() + ("date" to mapOf("year" to 1974, "month" to 13))
    assertNull(mediaItemFromMap("m1", fields))
  }

  @Test
  fun theTypeIsStoredAndTheIdIsNot() {
    assertEquals("picture", picture.toMap()["type"])
    assertEquals("audio", audio.toMap()["type"])
    assertEquals("text", text.toMap()["type"])
    // the id is the document's id, or the outbox file's name
    for (item in listOf(picture, audio, text)) assertFalse("id" in item.toMap())
  }

  @Test
  fun theIdComesFromTheCallerNotFromTheMap() {
    val read = mediaItemFromMap("other", picture.toMap() + ("id" to "m1"))

    assertEquals("other", read?.id)
  }

  @Test
  fun missingFieldsTakeTheirDefaults() {
    val read = mediaItemFromMap("m1", mapOf("type" to "picture"))

    assertEquals(MediaItem.Picture(id = "m1"), read)
    assertEquals(MediaStatus.APPROVED, read?.status)
  }

  @Test
  fun aMissingOrUnknownTypeCantBeRead() {
    val fields = picture.toMap()

    assertNull(mediaItemFromMap("m1", fields - "type"))
    assertNull(mediaItemFromMap("m1", fields + ("type" to "video"))) // from a newer app version
    assertNull(mediaItemFromMap("m1", fields + ("type" to 1)))
  }

  @Test
  fun aFieldOfTheWrongKindCantBeRead() {
    val fields = picture.toMap()
    val badFields =
        mapOf(
            "createdAt" to "yesterday",
            "description" to 42,
            "shownIds" to listOf("u2", 7),
            "authorizedIds" to "u3",
            "status" to "ARCHIVED", // unknown status
            "width" to 4032.5, // a fraction where a whole number is expected
            "height" to Long.MAX_VALUE, // too big for an Int
            "date" to "1974",
            "location" to mapOf("lat" to "north"),
        )

    for ((key, value) in badFields) {
      assertNull("$key = $value was read", mediaItemFromMap("m1", fields + (key to value)))
    }
  }

  @Test
  fun numbersAreReadWhateverTheirKind() {
    // Firestore gives integers as Long; JSON gives a whole Double back as an Int
    val read =
        mediaItemFromMap(
            "m1",
            mapOf(
                "type" to "picture",
                "createdAt" to 1_700_000_000, // an Int
                "width" to 4032L, // a Long
                "height" to 3024.0, // a whole Double
                "location" to mapOf("lat" to 46, "lng" to 6.6f), // an Int and a Float
            ),
        )
            as MediaItem.Picture

    assertEquals(1_700_000_000L, read.createdAt)
    assertEquals(4032, read.width)
    assertEquals(3024, read.height)
    assertEquals(46.0, read.location!!.lat, 0.0)
    assertEquals(6.6, read.location!!.lng, 1e-6)
  }

  @Test
  fun anUnknownDateOrPlaceStaysUnknown() {
    val item = picture.copy(date = null, location = null)

    val fields = item.toMap()
    val read = mediaItemFromMap("m1", fields)

    assertNull(fields["date"])
    assertNull(read?.date)
    assertNull(read?.location)
    // a date known only by its year keeps no month or day
    val yearOnly = picture.copy(date = MemoryDate(year = 1974))
    assertEquals(yearOnly, mediaItemFromMap("m1", yearOnly.toMap()))
  }
}
