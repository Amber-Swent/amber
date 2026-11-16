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
  fun errorsOfTheSameSubtypeAreEqual() {
    assertEquals(AppointmentError.NotFound(), AppointmentError.NotFound())
    assertEquals(AppointmentError.NotFound().hashCode(), AppointmentError.NotFound().hashCode())
    assertEquals(
        AppointmentWriteFailure("a1", AppointmentWrite.UPDATE, AppointmentError.NotFound()),
        AppointmentWriteFailure("a1", AppointmentWrite.UPDATE, AppointmentError.NotFound()),
    )
  }

  @Test
  fun errorsOfDifferentSubtypesDifferBothWays() {
    val notFound = AppointmentError.NotFound()
    val denied = AppointmentError.PermissionDenied()

    assertNotEquals(notFound, denied)
    assertNotEquals(denied, notFound)
  }

  @Test
  fun dataClassErrorsAlsoCompareTheirFields() {
    val blank = AppointmentError.InvalidAppointment(AppointmentValidationError.BLANK_TITLE)

    assertEquals(
        blank,
        AppointmentError.InvalidAppointment(AppointmentValidationError.BLANK_TITLE),
    )
    assertEquals(
        blank.hashCode(),
        AppointmentError.InvalidAppointment(AppointmentValidationError.BLANK_TITLE).hashCode(),
    )
    assertNotEquals(
        blank,
        AppointmentError.InvalidAppointment(AppointmentValidationError.MISSING_START),
    )
  }

  @Test
  fun unknownErrorsAreEqualOnlyWithTheSameSource() {
    val source = IllegalStateException("boom")

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
