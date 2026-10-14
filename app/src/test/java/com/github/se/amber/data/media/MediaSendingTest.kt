// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

/** Checks the types the background send job works with. */
class MediaSendingTest {

  @Test
  fun sendResultsAreTheDocumentedOnes() {
    assertEquals(
        listOf(SendResult.DONE, SendResult.RETRY, SendResult.FAILED),
        SendResult.entries,
    )
  }
}
