// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.appointment

import com.github.se.amber.model.calendar.MAX_DURATION_MINUTES
import com.github.se.amber.model.location.GeoLocation
import com.google.firebase.firestore.util.CustomClassMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests the validation and the Firestore mapping of [Appointment].
 *
 * The mapping tests use [CustomClassMapper], the SDK's own mapper behind set() and toObject(). It
 * is an internal class, so a Firebase update may move it. These tests then need the new location.
 */
class AppointmentTest {

  private val valid = Appointment(id = "a1", title = "Dr. Martin", startAt = 1_000L)

  private val stored =
      Appointment(
          id = "a1",
          title = "Dr. Martin",
          notes = "Bring the blood test results",
          startAt = 1_000L,
          durationMinutes = 30,
          location = GeoLocation(lat = 46.5, lng = 6.6, placeName = "CHUV"),
          attendeeIds = listOf("u1", "u2"),
          lastEndAt = 1_000L + 30 * 60_000L,
          createdBy = "u1",
          createdAt = 500L,
          updatedBy = "u2",
          updatedAt = 900L,
      )

  @Test
  fun aValidAppointmentHasNoError() {
    assertNull(valid.validationError())
    assertNull(valid.copy(startAt = 1L).validationError())
    assertNull(valid.copy(durationMinutes = 1).validationError())
    assertNull(valid.copy(durationMinutes = MAX_DURATION_MINUTES).validationError())
  }

  @Test
  fun aBlankTitleIsRefused() {
    listOf("", "  ", "\t\n").forEach { title ->
      assertEquals(
          AppointmentValidationError.BLANK_TITLE,
          valid.copy(title = title).validationError(),
      )
    }
  }

  @Test
  fun aMissingStartIsRefused() {
    assertEquals(
        AppointmentValidationError.MISSING_START,
        valid.copy(startAt = 0L).validationError(),
    )
    assertEquals(
        AppointmentValidationError.MISSING_START,
        valid.copy(startAt = -1L).validationError(),
    )
  }

  @Test
  fun aDurationOutsideTheRangeIsRefused() {
    listOf(0, -1, MAX_DURATION_MINUTES + 1).forEach { minutes ->
      assertEquals(
          AppointmentValidationError.INVALID_DURATION,
          valid.copy(durationMinutes = minutes).validationError(),
      )
    }
  }

  @Test
  fun checksRunInTheOrderOfTheEnum() {
    assertEquals(
        AppointmentValidationError.BLANK_TITLE,
        Appointment(title = " ", startAt = 0L, durationMinutes = 0).validationError(),
    )
    assertEquals(
        AppointmentValidationError.MISSING_START,
        valid.copy(startAt = 0L, durationMinutes = 0).validationError(),
    )
  }

  @Test
  fun theStatusHelpersAreNotStoredByFirestore() {
    @Suppress("UNCHECKED_CAST")
    val map = CustomClassMapper.convertToPlainJavaTypes(stored) as Map<String, Any?>

    assertEquals(
        setOf(
            "id",
            "title",
            "notes",
            "startAt",
            "durationMinutes",
            "location",
            "attendeeIds",
            "lastEndAt",
            "createdBy",
            "createdAt",
            "updatedBy",
            "updatedAt",
        ),
        map.keys,
    )
    assertEquals(setOf("lat", "lng", "placeName"), (map["location"] as Map<*, *>).keys)
  }

  @Test
  fun anAppointmentReadBackFromFirestoreIsUnchanged() {
    val map = CustomClassMapper.convertToPlainJavaTypes(stored)

    assertEquals(stored, CustomClassMapper.convertToCustomClass(map, Appointment::class.java, null))
  }

  @Test
  fun aDocumentWrittenBeforeTheNewFieldsGetsTheirDefaults() {
    val oldDocument =
        mapOf(
            "id" to "a1",
            "title" to "Dr. Martin",
            "startAt" to 1_000L,
            "createdBy" to "u1",
            "createdAt" to 500L,
        )

    val read = CustomClassMapper.convertToCustomClass(oldDocument, Appointment::class.java, null)

    assertEquals(valid.copy(createdBy = "u1", createdAt = 500L), read)
    assertEquals(0L, read.lastEndAt)
    assertEquals("", read.updatedBy)
    assertEquals(0L, read.updatedAt)
  }
}
