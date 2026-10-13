// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

import com.github.se.amber.data.media.SendResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Checks the types describing a media on its way to Firebase. */
class MediaSendTypesTest {

  private val picture = MediaItem.Picture(id = "m1", storagePath = "careCircles/c1/media/m1.jpg")

  @Test
  fun sendStatesAndResultsAreTheDocumentedOnes() {
    assertEquals(
        listOf(SendState.WAITING, SendState.SENDING, SendState.FAILED),
        SendState.entries.map { SendState.valueOf(it.name) },
    )
    assertEquals(
        listOf(SendResult.DONE, SendResult.RETRY, SendResult.FAILED),
        SendResult.entries.map { SendResult.valueOf(it.name) },
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
