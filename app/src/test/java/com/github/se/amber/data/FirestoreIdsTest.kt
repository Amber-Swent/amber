// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreIdsTest {

  @Test
  fun aGeneratedIdIsValid() {
    assertTrue(isValidDocumentId("AbC123xyz-_"))
  }

  @Test
  fun aBlankIdIsInvalid() {
    assertFalse(isValidDocumentId(""))
    assertFalse(isValidDocumentId("   "))
  }

  @Test
  fun anIdWithSpacesAroundItIsInvalid() {
    assertFalse(isValidDocumentId(" circle"))
    assertFalse(isValidDocumentId("circle\n"))
  }

  @Test
  fun anIdWithASlashIsInvalid() {
    // It would point to a document further down the tree.
    assertFalse(isValidDocumentId("circle/media/m1"))
    assertFalse(isValidDocumentId("/"))
  }
}
