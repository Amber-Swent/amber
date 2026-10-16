// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.CircleMedia
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaStatus
import com.github.se.amber.model.media.SendState
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
import com.google.firebase.storage.StorageException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.File
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Checks the methods of [MediaRepositoryFirebase] that use Firestore: ids, the lists (with the
 * outbox for [MediaRepositoryFirebase.observeCircleMedia]), [MediaRepositoryFirebase.getMedia],
 * [MediaRepositoryFirebase.updateMedia], [MediaRepositoryFirebase.deleteMedia] (with its file and
 * copy steps) and the send ([MediaRepositoryFirebase.send]). Firestore is mocked (no network),
 * files go through a [FakeMediaFileStorage], sends are scheduled on a [FakeMediaSendScheduler], and
 * the outbox and the copies on the phone are real, in a temporary folder (Robolectric gives the
 * outbox Android's org.json). Adding, retrying and discarding unsent media,
 * [MediaRepositoryFirebase.getFile] and the clears are checked in [MediaRepositoryFirebaseTest].
 */
@OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher, runCurrent, currentTime
@RunWith(RobolectricTestRunner::class)
class MediaRepositoryFirestoreTest {

  @get:Rule val tmp = TemporaryFolder()

  /**
   * What reached Firebase, in order: "upload {bucket}:{storagePath}" and "delete
   * {circleId}:{storagePath}" for files (recorded by [fileStorage]), "create document" and "delete
   * document" for the Firestore document.
   */
  private val events = mutableListOf<String>()

  private val fileStorage = FakeMediaFileStorage(events)
  private val scheduler = FakeMediaSendScheduler()
  private val cacheDir by lazy { File(tmp.root, "media") }
  private val outboxDir by lazy { File(tmp.root, "media-outbox") }

  private val firestore = mockk<FirebaseFirestore>()
  private val circles = mockk<CollectionReference>()
  private val circleDocument = mockk<DocumentReference>()
  private val mediaCollection = mockk<CollectionReference>()
  private val mediaDocument = mockk<DocumentReference>()
  private val transaction = mockk<Transaction>()
  private val created = slot<Any>()
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
          events += "delete document"
          transaction
        }
    every { transaction.set(mediaDocument, capture(created)) } answers
        {
          events += "create document"
          transaction
        }
  }

  private fun TestScope.newCache() =
      MediaFileCache(cacheDir, ioDispatcher = StandardTestDispatcher(testScheduler))

  private fun TestScope.newOutbox() =
      MediaOutbox(outboxDir, ioDispatcher = StandardTestDispatcher(testScheduler))

  private fun TestScope.newRepository(
      cache: MediaFileCache = newCache(),
      outbox: MediaOutbox = newOutbox(),
  ) = MediaRepositoryFirebase(fileStorage, cache, outbox, firestore, scheduler)

  /** A file holding [content], as the camera would give it. */
  private fun source(content: String = "bytes"): File = tmp.newFile().apply { writeText(content) }

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

  /** No document yet: the media was never sent. */
  private fun noDocument() = documentOf("m1", null)

  /** Firestore answering [code] to the send's transaction (e.g. offline: UNAVAILABLE). */
  private fun stubFirestoreError(code: FirebaseFirestoreException.Code) {
    every { transaction.get(mediaDocument) } throws FirebaseFirestoreException("refused", code)
  }

  /** The error Firebase Storage reports for an HTTP [httpCode] (403: not authorized). */
  private fun storageError(httpCode: Int) =
      requireNotNull(StorageException.fromExceptionAndHttpCode(null, httpCode))

  private suspend fun MediaOutbox.stateOf(mediaId: String) = get("c1", mediaId)?.state

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
  fun updateMediaWritesOnlyTheEditableFields() = runTest {
    stubStoredDocument(documentOf(picture))
    val written = slot<Map<String, Any>>()
    every { transaction.update(mediaDocument, capture(written)) } returns transaction
    // an older copy: its status, creation time and size differ from the stored ones
    val edited =
        picture.copy(
            description = "Beach, 1998",
            shownIds = listOf("u2"),
            status = MediaStatus.REJECTED,
            createdAt = 5L,
            width = 8,
        )

    newRepository().updateMedia("c1", edited)

    assertEquals(
        mapOf(
            "description" to "Beach, 1998",
            "date" to null,
            "location" to null,
            "shownIds" to listOf("u2"),
            "authorizedIds" to emptyList<String>(),
        ),
        written.captured,
    )
  }

  @Test
  fun updateMediaOfATextAlsoWritesItsText() = runTest {
    stubStoredDocument(documentOf(text))
    val written = slot<Map<String, Any>>()
    every { transaction.update(mediaDocument, capture(written)) } returns transaction

    newRepository().updateMedia("c1", text.copy(text = "Edited"))

    assertEquals("Edited", written.captured["text"])
    assertEquals(
        setOf("description", "date", "location", "shownIds", "authorizedIds", "text"),
        written.captured.keys,
    )
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

    assertEquals(listOf("delete document", "delete c1:careCircles/c1/media/m1.jpg"), events)
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
    assertEquals(listOf("delete document"), events)
    assertEquals(listOf("careCircles_c1_media_m1.jpg"), copiesOnPhone())
  }

  @Test
  fun deleteMediaDeletesNothingElseWhenFirebaseRefusesTheDocumentDeletion() = runTest {
    // Firebase checks the rules when it commits the transaction, and then refuses it as a whole
    every { firestore.runTransaction<Any?>(any()) } returns
        Tasks.forException(
            FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        )
    val cache = newCache()
    cache.putCopy(picture.storagePath)

    val error = runCatching { newRepository(cache).deleteMedia(circle, picture) }.exceptionOrNull()

    // e.g. not its author: the file and the copy must stay, or the document would point at nothing
    assertEquals(
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        (error as? FirebaseFirestoreException)?.code,
    )
    assertEquals(emptyList<String>(), events)
    assertEquals(listOf("careCircles_c1_media_m1.jpg"), copiesOnPhone())
  }

  @Test
  fun deleteMediaCalledAgainSkipsTheMissingDocumentAndDeletesTheFile() = runTest {
    stubStoredDocument(documentOf("m1", null))

    newRepository().deleteMedia(circle, picture)

    verify(exactly = 0) { transaction.delete(any()) }
    assertEquals(listOf("delete c1:careCircles/c1/media/m1.jpg"), events)
  }

  @Test
  fun deleteMediaOfATextDeletesOnlyTheDocument() = runTest {
    stubStoredDocument(documentOf(text))

    newRepository().deleteMedia(circle.copy(storageBucket = ""), text)

    assertEquals(listOf("delete document"), events)
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
    assertEquals(emptyList<String>(), events)
  }

  @Test
  fun sendUploadsTheFileThenCreatesTheDocumentThenKeepsTheFileOnThePhone() = runTest {
    stubStoredDocument(noDocument())
    val outbox = newOutbox()
    val repository = newRepository(outbox = outbox)
    repository.addMedia(circle, picture, source("photo bytes"))

    assertEquals(SendResult.DONE, repository.send("c1", "m1"))

    // the file first, so the document never points at a missing file
    assertEquals(
        listOf("upload bucket-c1:careCircles/c1/media/m1.jpg", "create document"),
        events,
    )
    assertEquals("photo bytes", fileStorage.lastUploadedContent)
    assertEquals(picture.toMap() + ("id" to "m1"), created.captured)
    assertNull(outbox.get("c1", "m1"))
    // the file moved into the cache: it opens without a download
    assertEquals("photo bytes", repository.getFile(circle, picture).readText())
  }

  @Test
  fun sendKeepsADocumentAnEarlierAttemptCreated() = runTest {
    // e.g. the app was killed after creating it, and the media was edited since
    stubStoredDocument(documentOf(picture.copy(description = "Edited since")))
    val outbox = newOutbox()
    val repository = newRepository(outbox = outbox)
    repository.addMedia(circle, picture, source())

    assertEquals(SendResult.DONE, repository.send("c1", "m1"))

    verify(exactly = 0) { transaction.set(any<DocumentReference>(), any<Any>()) }
    assertNull(outbox.get("c1", "m1"))
  }

  @Test
  fun sendOfATextOnlyCreatesItsDocument() = runTest {
    stubStoredDocument(noDocument())
    val repository = newRepository()
    repository.addMedia(circle, text, null)

    assertEquals(SendResult.DONE, repository.send("c1", "m1"))

    assertEquals(listOf("create document"), events)
    assertEquals(text.toMap() + ("id" to "m1"), created.captured)
  }

  @Test
  fun aFailureThatMayGoAwayIsCountedAndTheTenthInARowFails() = runTest {
    val outbox = newOutbox()
    val repository = newRepository(outbox = outbox)
    repository.addMedia(circle, picture, source())
    val mayGoAway =
        mapOf(
            "Firestore unavailable (e.g. offline)" to
                {
                  stubFirestoreError(FirebaseFirestoreException.Code.UNAVAILABLE)
                },
            // what Storage answers when it sends a request without the user's token, e.g. on a
            // network that isn't really working yet
            "Storage: not authenticated" to { fileStorage.onUpload = { throw storageError(401) } },
            "Storage: not authorized" to { fileStorage.onUpload = { throw storageError(403) } },
        )

    for ((case, stub) in mayGoAway) {
      stub()
      val results = List(MediaSender.MAX_FAILED_ATTEMPTS) { repository.send("c1", "m1") }

      assertEquals(case, List(9) { SendResult.RETRY } + SendResult.FAILED, results)
      assertEquals(case, SendState.FAILED, outbox.stateOf("m1"))
      // a failed media has no job left: its next run, if any, does nothing
      assertEquals(case, SendResult.DONE, repository.send("c1", "m1"))
      repository.retrySend("c1", "m1") // a new series of attempts for the next case
    }
  }

  @Test
  fun aFailureRetryingCantFixFailsAtOnce() = runTest {
    val outbox = newOutbox()
    val repository = newRepository(outbox = outbox)
    repository.addMedia(circle, picture, source())
    val permanent =
        mapOf(
            "Firestore refusing the user" to
                {
                  stubFirestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED)
                },
            "the file refused before sending" to
                {
                  fileStorage.onUpload = { throw IllegalArgumentException("file over 25 MiB") }
                },
        )

    for ((case, stub) in permanent) {
      stub()
      assertEquals(case, SendResult.FAILED, repository.send("c1", "m1"))
      assertEquals(case, SendState.FAILED, outbox.stateOf("m1"))
      repository.retrySend("c1", "m1") // back to WAITING for the next case
    }
  }

  @Test
  fun anAttemptOverTheTimeLimitStopsItsUploadAndIsCounted() = runTest {
    var uploadStopped = false
    fileStorage.onUpload = {
      try {
        awaitCancellation() // an upload that never ends
      } finally {
        uploadStopped = true
      }
    }
    val outbox = newOutbox()
    val repository = newRepository(outbox = outbox)
    repository.addMedia(circle, picture, source())

    assertEquals(SendResult.RETRY, repository.send("c1", "m1"))

    assertTrue(uploadStopped)
    assertTrue(currentTime >= MediaSender.ATTEMPT_TIMEOUT_MS)
    assertEquals(SendState.WAITING, outbox.stateOf("m1"))
    // counted: 9 more failures make the 10th, so an upload that always hangs still ends FAILED
    fileStorage.onUpload = {}
    stubFirestoreError(FirebaseFirestoreException.Code.UNAVAILABLE)
    val results = List(MediaSender.MAX_FAILED_ATTEMPTS - 1) { repository.send("c1", "m1") }
    assertEquals(SendResult.FAILED, results.last())
  }

  @Test
  fun aCancelledSendIsWaitingAgainAndCountsNothing() = runTest {
    fileStorage.onUpload = { awaitCancellation() }
    val outbox = newOutbox()
    val repository = newRepository(outbox = outbox)
    repository.addMedia(circle, picture, source())
    val run = launch { repository.send("c1", "m1") }
    runCurrent()
    assertEquals(SendState.SENDING, outbox.stateOf("m1"))

    // the system stops the run, e.g. the connection was lost
    run.cancel()
    run.join()

    assertEquals(SendState.WAITING, outbox.stateOf("m1"))
    // had the cancellation counted, the 9th failure below would be the 10th
    fileStorage.onUpload = {}
    stubFirestoreError(FirebaseFirestoreException.Code.UNAVAILABLE)
    repeat(MediaSender.MAX_FAILED_ATTEMPTS - 1) { repository.send("c1", "m1") }
    assertEquals(SendState.WAITING, outbox.stateOf("m1"))
  }

  @Test
  fun anErrorDuringASendIsRethrownWithItsEntryWaitingAgain() = runTest {
    // e.g. a TODO() left in the code
    fileStorage.onUpload = { throw NotImplementedError("a bug") }
    val outbox = newOutbox()
    val repository = newRepository(outbox = outbox)
    repository.addMedia(circle, picture, source())

    val error = runCatching { repository.send("c1", "m1") }.exceptionOrNull()

    assertTrue("failed with $error", error is NotImplementedError)
    // left SENDING, it would wait for the next start of the app
    assertEquals(SendState.WAITING, outbox.stateOf("m1"))
  }

  @Test
  fun anEntryWhoseFileIsGoneFailsUnlessAnEarlierAttemptSentIt() = runTest {
    val outbox = newOutbox()
    val repository = newRepository(outbox = outbox)
    repository.addMedia(circle, picture, source())
    // e.g. moved into the cache by an attempt that then couldn't remove the entry
    File(outboxDir, "c1/m1.bin").delete()

    stubStoredDocument(noDocument())
    assertEquals(SendResult.FAILED, repository.send("c1", "m1"))

    repository.retrySend("c1", "m1")
    stubStoredDocument(documentOf(picture))
    assertEquals(SendResult.DONE, repository.send("c1", "m1"))
    assertNull(outbox.get("c1", "m1"))
    assertEquals(emptyList<String>(), events) // nothing uploaded or written
  }

  @Test
  fun anAttemptThatNoLongerOwnsItsEntryRetriesOrEndsIfTheEntryIsGone() = runTest {
    val outbox = newOutbox()
    val repository = newRepository(outbox = outbox)
    repository.addMedia(circle, picture, source())

    // e.g. a late cancellation of an older job put it back meanwhile: nothing is counted, and the
    // job must stay, or the entry would be left without one
    fileStorage.onUpload = {
      outbox.resetSending("c1", "m1")
      throw IOException("connection reset")
    }
    assertEquals(SendResult.RETRY, repository.send("c1", "m1"))
    assertEquals(SendState.WAITING, outbox.stateOf("m1"))

    // the same for a failure retrying can't fix: FAILED would end the job
    fileStorage.onUpload = {
      outbox.resetSending("c1", "m1")
      throw IllegalArgumentException("file refused")
    }
    assertEquals(SendResult.RETRY, repository.send("c1", "m1"))
    assertEquals(SendState.WAITING, outbox.stateOf("m1"))

    // discarded meanwhile: nothing left to send
    fileStorage.onUpload = {
      outbox.remove("c1", "m1")
      throw IOException("connection reset")
    }
    assertEquals(SendResult.DONE, repository.send("c1", "m1"))
  }

  @Test
  fun aSendFindingItsEntryAlreadySendingRetriesLater() = runTest {
    stubStoredDocument(noDocument())
    val repository = newRepository()
    repository.addMedia(circle, picture, source())
    var secondRun: SendResult? = null
    // a second run while the first one is still sending, e.g. the job replaced by a retry
    fileStorage.onUpload = { secondRun = repository.send("c1", "m1") }

    assertEquals(SendResult.DONE, repository.send("c1", "m1"))

    assertEquals(SendResult.RETRY, secondRun)
    // a media no longer in the outbox has nothing left to send
    assertEquals(SendResult.DONE, repository.send("c1", "unknown"))
  }

  @Test
  fun observeCircleMediaListsUnsentMediaNewestFirstThenSentMediaEachOnce() = runTest {
    stubApprovedQuery()
    val repository = newRepository()
    val older = picture.copy(id = "m2", storagePath = "careCircles/c1/media/m2.jpg", createdAt = 1)
    val newer = picture.copy(id = "m3", storagePath = "careCircles/c1/media/m3.jpg", createdAt = 5)
    val both = picture.copy(id = "m4", storagePath = "careCircles/c1/media/m4.jpg", createdAt = 9)
    for (item in listOf(older, newer, both)) repository.addMedia(circle, item, source())
    val lists = async { repository.observeCircleMedia("c1").first() }
    runCurrent()

    // m4 is in Firestore while still in the outbox: listed once, as sent
    listener.captured.onEvent(queryResult(documentOf(both), documentOf(picture)), null)

    assertEquals(
        listOf(
            CircleMedia(newer, SendState.WAITING),
            CircleMedia(older, SendState.WAITING),
            CircleMedia(both, sendState = null),
            CircleMedia(picture, sendState = null),
        ),
        lists.await(),
    )
  }

  @Test
  fun aMediaBeingSentIsNeverMissingFromObserveCircleMedia() = runTest {
    stubApprovedQuery()
    stubStoredDocument(noDocument())
    val repository = newRepository()
    val lists = mutableListOf<List<CircleMedia>>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observeCircleMedia("c1").toList(lists)
    }
    listener.captured.onEvent(queryResult(), null)
    repository.addMedia(circle, picture, source())
    runCurrent()

    repository.send("c1", "m1")
    runCurrent()
    // the transaction's document reaches this phone only now, after the entry left the outbox
    listener.captured.onEvent(queryResult(documentOf(picture)), null)
    runCurrent()

    assertEquals(emptyList<CircleMedia>(), lists.first())
    for (list in lists.drop(1)) assertEquals(list.toString(), 1, list.count { it.item == picture })
    assertEquals(listOf(CircleMedia(picture, sendState = null)), lists.last())
  }

  @Test
  fun aMediaDeletedRightAfterItsSendIsNoLongerListed() = runTest {
    stubApprovedQuery()
    stubStoredDocument(noDocument())
    val repository = newRepository()
    val lists = mutableListOf<List<CircleMedia>>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observeCircleMedia("c1").toList(lists)
    }
    listener.captured.onEvent(queryResult(), null)
    repository.addMedia(circle, picture, source())
    repository.send("c1", "m1")
    runCurrent()
    assertEquals(listOf(CircleMedia(picture, sendState = null)), lists.last())

    // deleted before Firestore's list showed it: it must not stay listed until a restart
    stubStoredDocument(documentOf(picture))
    repository.deleteMedia(circle, picture)
    runCurrent()

    assertEquals(emptyList<CircleMedia>(), lists.last())
  }
}
