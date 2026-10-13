// This file was written by Neha Chakraborty and Copilot
package com.github.se.amber.model.user

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonTest {

  @Test
  fun hasValidName_acceptsNonBlankFirstName() {
    assertTrue(Person(firstName = "Ada").hasValidName())
  }

  @Test
  fun hasValidName_rejectsBlankOrWhitespaceFirstName() {
    assertFalse(Person().hasValidName())
    assertFalse(Person(firstName = "   ").hasValidName())
  }
}
