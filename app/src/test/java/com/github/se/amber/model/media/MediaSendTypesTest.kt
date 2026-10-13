// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Checks the model types describing a media on its way to Firebase. */
class MediaSendTypesTest {

  private val picture = MediaItem.Picture(id = "m1", storagePath = "careCircles/c1/media/m1.jpg")

  @Test
  fun sendStatesAreTheDocumentedOnes() {
    assertEquals(
        listOf(SendState.WAITING, SendState.SENDING, SendState.FAILED),
        SendState.entries,
    )
  }

  @Test
  fun outboxEntryChangesStateButKeepsItsMedia() {
    val waiting = OutboxEntry(picture, SendState.WAITING)

    val sending = waiting.copy(state = SendState.SENDING)

    assertEquals(picture, sending.item)
    assertEquals(SendState.SENDING, sending.state)
    assertNotEquals(waiting, sending)
    assertEquals(waiting, OutboxEntry(picture, SendState.WAITING))
  }

  @Test
  fun circleMediaInFirebaseHasNoSendState() {
    val unsent = CircleMedia(picture, SendState.FAILED)

    val sent = unsent.copy(sendState = null)

    assertEquals(picture, sent.item)
    assertNull(sent.sendState)
    assertNotEquals(unsent, sent)
    assertEquals(sent, CircleMedia(picture, null))
  }
}
