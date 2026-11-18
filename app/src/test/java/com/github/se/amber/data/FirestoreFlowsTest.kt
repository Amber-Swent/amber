// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests [observeDocuments]. [observeSnapshots] without metadata changes is checked by
 * `MediaRepositoryFirestoreTest`, through the media lists.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreFlowsTest {
  private val query = mockk<Query>()
  private val registration = mockk<ListenerRegistration>(relaxed = true)
  private val listener = slot<EventListener<QuerySnapshot>>()

  @Before
  fun setUp() {
    // Only the call with INCLUDE is stubbed, so the test fails if the confirmations aren't asked.
    every { query.addSnapshotListener(MetadataChanges.INCLUDE, capture(listener)) } returns
        registration
  }

  @Test
  fun observeDocumentsSkipsUnreadableDocumentsAndFollowsTheSyncFlag() = runTest {
    val lists = mutableListOf<List<Synced<String>>>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observe().toList(lists) }

    // "a" is added offline, so the server hasn't confirmed it yet.
    send(document("a", pending = true), document(UNREADABLE), document("b"))
    // The server confirms it.
    send(document("a"), document(UNREADABLE), document("b"))
    // Only the metadata changes again, for example when going online. The list is the same.
    send(document("a"), document(UNREADABLE), document("b"))

    assertEquals(
        listOf(
            listOf(Synced("A", isSynced = false), Synced("B", isSynced = true)),
            listOf(Synced("A", isSynced = true), Synced("B", isSynced = true)),
        ),
        lists,
    )
  }

  @Test
  fun observeDocumentsEndsWithTheMappedErrorAndStopsListening() = runTest {
    val error =
        async(UnconfinedTestDispatcher(testScheduler)) {
          runCatching { observe().collect() }.exceptionOrNull()
        }

    listener.captured.onEvent(
        null,
        FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED),
    )

    // If the flow never ended, this fails at once (virtual time) instead of hanging.
    val thrown = withTimeout(1_000) { error.await() }
    assertTrue("ended with $thrown", thrown is IllegalStateException)
    assertEquals("mapped PERMISSION_DENIED", thrown?.message)
    verify(exactly = 1) { registration.remove() }
  }

  /** Observes [query], reading each document as its id in capitals. */
  private fun observe(): Flow<List<Synced<String>>> =
      query.observeDocuments(
          map = { document -> document.id.takeIf { it != UNREADABLE }?.uppercase() },
          onError = { IllegalStateException("mapped ${(it as FirebaseFirestoreException).code}") },
      )

  /** Sends a query result with [documents] to the listener. */
  private fun TestScope.send(vararg documents: DocumentSnapshot) {
    val snapshot =
        mockk<QuerySnapshot> { every { this@mockk.documents } returns documents.toList() }
    listener.captured.onEvent(snapshot, null)
    runCurrent()
  }

  /** @return a document named [id], with a write the server hasn't confirmed if [pending]. */
  private fun document(id: String, pending: Boolean = false): DocumentSnapshot = mockk {
    every { this@mockk.id } returns id
    every { metadata.hasPendingWrites() } returns pending
  }

  private companion object {
    /** The id of a document the map function can't read. */
    const val UNREADABLE = "unreadable"
  }
}
