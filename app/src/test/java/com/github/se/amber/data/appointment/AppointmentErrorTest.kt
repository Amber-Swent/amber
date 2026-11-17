// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment

import com.github.se.amber.model.appointment.AppointmentValidationError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AppointmentErrorTest {

  @Test
  fun anUnknownErrorKeepsTheOriginalExceptionAsItsCause() {
    val source = IllegalStateException("boom")

    assertSame(source, AppointmentError.Unknown(source).cause)
    assertNull(AppointmentError.NotFound().cause)
  }

  @Test
  fun anInvalidAppointmentSaysWhy() {
    val error = AppointmentError.InvalidAppointment(AppointmentValidationError.BLANK_TITLE)

    assertEquals(AppointmentValidationError.BLANK_TITLE, error.reason)
    assertEquals("The appointment is invalid: BLANK_TITLE", error.message)
  }

  @Test
  fun equalityFollowsTheSubtypeAndItsFields() {
    val source = IllegalStateException("boom")
    val blank = AppointmentError.InvalidAppointment(AppointmentValidationError.BLANK_TITLE)

    // Same subtype: equal, with the same hash code, also inside a write failure.
    assertEquals(AppointmentError.NotFound(), AppointmentError.NotFound())
    assertEquals(AppointmentError.NotFound().hashCode(), AppointmentError.NotFound().hashCode())
    assertEquals(
        AppointmentWriteFailure("a1", AppointmentWrite.UPDATE, AppointmentError.NotFound()),
        AppointmentWriteFailure("a1", AppointmentWrite.UPDATE, AppointmentError.NotFound()),
    )

    // Different subtypes: different, both ways.
    assertNotEquals(AppointmentError.NotFound(), AppointmentError.PermissionDenied())
    assertNotEquals(AppointmentError.PermissionDenied(), AppointmentError.NotFound())

    // Data class subtypes also compare their fields.
    assertEquals(blank, AppointmentError.InvalidAppointment(AppointmentValidationError.BLANK_TITLE))
    assertEquals(
        blank.hashCode(),
        AppointmentError.InvalidAppointment(AppointmentValidationError.BLANK_TITLE).hashCode(),
    )
    assertNotEquals(
        blank,
        AppointmentError.InvalidAppointment(AppointmentValidationError.MISSING_START),
    )

    // Unknown compares the original exception itself.
    assertEquals(AppointmentError.Unknown(source), AppointmentError.Unknown(source))
    assertNotEquals(
        AppointmentError.Unknown(source),
        AppointmentError.Unknown(IllegalStateException("boom")),
    )
  }

  @Test
  fun eachFailureRecordsWhereItWasCreated() {
    assertEquals("createdInA", createdInA().stackTrace.first().methodName)
    assertEquals("createdInB", createdInB().stackTrace.first().methodName)
  }

  private fun createdInA() = AppointmentError.NotFound()

  private fun createdInB() = AppointmentError.NotFound()
}
