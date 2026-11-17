// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FirestoreIdsTest {

  @Test
  fun onlyAnIdOfOneDocumentIsValid() {
    val cases =
        listOf(
            "AbC123xyz-_" to true, // the kind of id Firestore generates
            "" to false,
            "   " to false,
            " circle" to false, // spaces around it
            "circle\n" to false,
            "circle/media/m1" to false, // a document further down the tree
            "/" to false,
        )

    for ((id, expected) in cases) {
      assertEquals("isValidDocumentId(\"$id\")", expected, isValidDocumentId(id))
    }
  }
}
