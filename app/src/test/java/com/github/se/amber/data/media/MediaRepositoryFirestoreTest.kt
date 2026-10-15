// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Transaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.File
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Checks the methods of [MediaRepositoryFirebase] that use Firestore: ids, the two lists,
 * [MediaRepositoryFirebase.getMedia], [MediaRepositoryFirebase.updateMedia] and
 * [MediaRepositoryFirebase.deleteMedia] (with its file and copy steps). Firestore is mocked (no
 * network), files go through a [FakeMediaFileStorage], and copies on the phone through a real
 * [MediaFileCache] in a temporary folder. [MediaRepositoryFirebase.getFile] and the clears are
 * checked in [MediaRepositoryFirebaseTest].
 */
@OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher, runCurrent
class MediaRepositoryFirestoreTest {

  @get:Rule val tmp = TemporaryFolder()

  /**
   * What was deleted, in order: "delete document" for the Firestore document, then "delete
   * {circleId}:{storagePath}" for the file (recorded by [fileStorage]).
   */
  private val deletions = mutableListOf<String>()

  private val fileStorage = FakeMediaFileStorage(deletions)
  private val cacheDir by lazy { File(tmp.root, "media") }

  private val firestore = mockk<FirebaseFirestore>()
  private val circles = mockk<CollectionReference>()
  private val circleDocument = mockk<DocumentReference>()
  private val mediaCollection = mockk<CollectionReference>()
  private val mediaDocument = mockk<DocumentReference>()
  private val transaction = mockk<Transaction>()
  private val listener = slot<EventListener<QuerySnapshot>>()
  private val registration = mockk<ListenerRegistration>(relaxed = true)

  private val circle = CareCircle(id = "c1", storageBucket = "bucket-c1")
  private val picture =
      MediaItem.Picture(
          id = "m1",
          authorId = "u1",
          storagePath = "careCircles/c1/media/m1.jpg",
          createdAt = 1L,
          description = "Beach",
          width = 4,
          height = 3,
      )
  private val text = MediaItem.Text(id = "m1", authorId = "u1", createdAt = 1L, text = "Hello")

  @Before
  fun setUp() {
    every { firestore.collection("careCircles") } returns circles
    every { circles.document("c1") } returns circleDocument
    every { circleDocument.collection("media") } returns mediaCollection
    every { mediaCollection.document("m1") } returns mediaDocument
    // runs the transaction's code at once on the mocked transaction, like Firebase would online
    every { firestore.runTransaction<Any?>(any()) } answers
        {
          try {
            Tasks.forResult(firstArg<Transaction.Function<Any?>>().apply(transaction))
          } catch (e: Exception) {
            Tasks.forException(e)
          }
        }
    every { transaction.delete(mediaDocument) } answers
        {
          deletions += "delete document"
          transaction
        }
  }

  private fun TestScope.newCache() =
      MediaFileCache(cacheDir, ioDispatcher = StandardTestDispatcher(testScheduler))

  private fun TestScope.newRepository(cache: MediaFileCache = newCache()) =
      MediaRepositoryFirebase(fileStorage, cache, firestore)

  /** A media document as Firestore returns it: [data] null when there is no document. */
  private fun documentOf(id: String, data: Map<String, Any?>?): DocumentSnapshot {
    val reference = mockk<DocumentReference> { every { path } returns "careCircles/c1/media/$id" }
    @Suppress("UNCHECKED_CAST")
    return mockk {
      every { this@mockk.id } returns id
      every { this@mockk.data } returns data as Map<String, Any>?
      every { exists() } returns (data != null)
      every { this@mockk.reference } returns reference
    }
  }

  /** [item]'s document as the repository writes it. */
  private fun documentOf(item: MediaItem) = documentOf(item.id, item.toMap() + ("id" to item.id))

  /** A document a newer app version could write: a kind of media this one doesn't know. */
  private fun unreadableDocument(id: String) = documentOf(id, mapOf("id" to id, "type" to "video"))

  private fun queryResult(vararg documents: DocumentSnapshot): QuerySnapshot = mockk {
    every { this@mockk.documents } returns documents.toList()
  }

  /**
   * The approved-media query: status == APPROVED, newest first; its listener goes to [listener].
   */
  private fun stubApprovedQuery() {
    val approved = mockk<Query>()
    val ordered = mockk<Query>()
    every { mediaCollection.whereEqualTo("status", "APPROVED") } returns approved
    every { approved.orderBy("createdAt", Query.Direction.DESCENDING) } returns ordered
    every { ordered.addSnapshotListener(capture(listener)) } returns registration
  }

  private fun stubStoredDocument(document: DocumentSnapshot) {
    every { mediaDocument.get() } returns Tasks.forResult(document)
    every { transaction.get(mediaDocument) } returns document
  }

  /** Puts a downloaded copy of [storagePath] on the phone, as a download would. */
  private suspend fun MediaFileCache.putCopy(storagePath: String) =
      put(storagePath, tmp.newFile().apply { writeText("content") })

  private fun copiesOnPhone() = cacheDir.listFiles().orEmpty().map { it.name }

  @Test
  fun newMediaIdIsANewDocumentIdOfTheCircleMedia() = runTest {
    every { mediaCollection.document() } returns mockk { every { id } returns "generated" }

    assertEquals("generated", newRepository().newMediaId("c1"))
  }

  @Test
  fun observeApprovedMediaKeepsFirestoreOrderSkipsUnreadableAndEmitsAgain() = runTest {
    stubApprovedQuery()
    val newer = picture.copy(id = "m2", createdAt = 2L)
    val lists = mutableListOf<List<MediaItem>>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      newRepository().observeApprovedMedia("c1").toList(lists)
    }

    // given oldest first: the list must keep Firestore's order, not sort it again
    listener.captured.onEvent(
        queryResult(documentOf(picture), unreadableDocument("m3"), documentOf(newer)),
        null,
    )
    runCurrent()
    listener.captured.onEvent(queryResult(documentOf(picture)), null) // m2 deleted meanwhile
    runCurrent()

    assertEquals(listOf(listOf(picture, newer), listOf(picture)), lists)
  }

  @Test
  fun observeMediaShowingQueriesTheShownIdNewestFirst() = runTest {
    val showing = mockk<Query>()
    val ordered = mockk<Query>()
    every { mediaCollection.whereArrayContains("shownIds", "u2") } returns showing
    every { showing.orderBy("createdAt", Query.Direction.DESCENDING) } returns ordered
    every { ordered.addSnapshotListener(capture(listener)) } returns registration
    val lists = mutableListOf<List<MediaItem>>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      newRepository().observeMediaShowing("c1", "u2").toList(lists)
    }

    val shown = picture.copy(shownIds = listOf("u2"))
    listener.captured.onEvent(queryResult(documentOf(shown)), null)
    runCurrent()

    assertEquals(listOf(listOf(shown)), lists)
  }

  @Test
  fun observeEndsWithTheFirebaseErrorAndStopsListening() = runTest {
    stubApprovedQuery()
    val error =
        async(UnconfinedTestDispatcher(testScheduler)) {
          runCatching { newRepository().observeApprovedMedia("c1").collect() }.exceptionOrNull()
        }

    listener.captured.onEvent(
        null,
        FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED),
    )

    // if the flow never ended, this fails at once (virtual time) instead of hanging
    val thrown = withTimeout(1_000) { error.await() }
    assertTrue("ended with $thrown", thrown is FirebaseFirestoreException)
    assertEquals(
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        (thrown as FirebaseFirestoreException).code,
    )
    verify(exactly = 1) { registration.remove() }
  }

  @Test
  fun observeStopsListeningWhenTheCollectorStops() = runTest {
    stubApprovedQuery()
    val collector =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          newRepository().observeApprovedMedia("c1").collect()
        }
    verify(exactly = 0) { registration.remove() }

    collector.cancel()
    runCurrent()

    verify(exactly = 1) { registration.remove() }
  }

  @Test
  fun getMediaReadsTheStoredMedia() = runTest {
    stubStoredDocument(documentOf(picture))

    assertEquals(picture, newRepository().getMedia("c1", "m1"))
  }

  @Test
  fun getMediaIsNullForAMissingOrUnreadableDocument() = runTest {
    val repository = newRepository()

    stubStoredDocument(documentOf("m1", null))
    assertNull(repository.getMedia("c1", "m1"))

    stubStoredDocument(unreadableDocument("m1"))
    assertNull(repository.getMedia("c1", "m1"))
  }

  @Test
  fun updateMediaWritesEveryFieldAndTheIdTheRulesRequire() = runTest {
    stubStoredDocument(documentOf(picture))
    val written = slot<Map<String, Any>>()
    every { transaction.update(mediaDocument, capture(written)) } returns transaction
    val edited = picture.copy(description = "Beach, 1998")

    newRepository().updateMedia("c1", edited)

    assertEquals(edited.toMap() + ("id" to "m1"), written.captured)
  }

  @Test
  fun updateMediaOfAMissingDocumentFailsWithoutWriting() = runTest {
    stubStoredDocument(documentOf("m1", null))

    val error = runCatching { newRepository().updateMedia("c1", picture) }.exceptionOrNull()

    assertEquals(
        FirebaseFirestoreException.Code.NOT_FOUND,
        (error as? FirebaseFirestoreException)?.code,
    )
    verify(exactly = 0) { transaction.update(any(), any<Map<String, Any>>()) }
  }

  @Test
  fun deleteMediaDeletesTheDocumentThenTheFileThenTheCopyOnThePhone() = runTest {
    stubStoredDocument(documentOf(picture))
    val cache = newCache()
    cache.putCopy(picture.storagePath)

    newRepository(cache).deleteMedia(circle, picture)

    assertEquals(listOf("delete document", "delete c1:careCircles/c1/media/m1.jpg"), deletions)
    assertEquals(emptyList<String>(), copiesOnPhone())
  }

  @Test
  fun deleteMediaKeepsTheCopyOnThePhoneWhenTheFileDeletionFails() = runTest {
    stubStoredDocument(documentOf(picture))
    val cache = newCache()
    cache.putCopy(picture.storagePath)
    fileStorage.deleteError = IOException("offline")

    val error = runCatching { newRepository(cache).deleteMedia(circle, picture) }.exceptionOrNull()

    assertTrue("failed with $error", error is IOException)
    // the document is gone, the file and the copy are left for a second call
    assertEquals(listOf("delete document"), deletions)
    assertEquals(listOf("careCircles_c1_media_m1.jpg"), copiesOnPhone())
  }

  @Test
  fun deleteMediaDeletesNothingElseWhenFirebaseRefusesTheDocumentDeletion() = runTest {
    stubStoredDocument(documentOf(picture))
    every { transaction.delete(mediaDocument) } throws
        FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
    val cache = newCache()
    cache.putCopy(picture.storagePath)

    val error = runCatching { newRepository(cache).deleteMedia(circle, picture) }.exceptionOrNull()

    // e.g. not its author: the file and the copy must stay, or the document would point at nothing
    assertEquals(
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        (error as? FirebaseFirestoreException)?.code,
    )
    assertEquals(emptyList<String>(), deletions)
    assertEquals(listOf("careCircles_c1_media_m1.jpg"), copiesOnPhone())
  }

  @Test
  fun deleteMediaCalledAgainSkipsTheMissingDocumentAndDeletesTheFile() = runTest {
    stubStoredDocument(documentOf("m1", null))

    newRepository().deleteMedia(circle, picture)

    verify(exactly = 0) { transaction.delete(any()) }
    assertEquals(listOf("delete c1:careCircles/c1/media/m1.jpg"), deletions)
  }

  @Test
  fun deleteMediaOfATextDeletesOnlyTheDocument() = runTest {
    stubStoredDocument(documentOf(text))

    newRepository().deleteMedia(circle.copy(storageBucket = ""), text)

    assertEquals(listOf("delete document"), deletions)
  }

  @Test
  fun deleteMediaRefusedBeforeDeletingAnything() = runTest {
    stubStoredDocument(documentOf(picture))
    val repository = newRepository()
    val refused =
        mapOf(
            "media of another circle" to
                suspend {
                  repository.deleteMedia(
                      circle,
                      picture.copy(storagePath = "careCircles/c2/media/m1.jpg"),
                  )
                },
            "circle without a bucket" to
                suspend { repository.deleteMedia(circle.copy(storageBucket = ""), picture) },
        )

    for ((case, call) in refused) {
      val error = runCatching { call() }.exceptionOrNull()
      assertTrue("$case: failed with $error", error is IllegalArgumentException)
    }
    verify(exactly = 0) { firestore.runTransaction<Any?>(any()) }
    assertEquals(emptyList<String>(), deletions)
  }
}
