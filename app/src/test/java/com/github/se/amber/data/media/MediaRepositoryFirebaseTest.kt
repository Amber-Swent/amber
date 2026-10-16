// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.OutboxEntry
import com.github.se.amber.model.media.SendState
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Checks the part of [MediaRepositoryFirebase] that stays on the phone: adding media to the outbox,
 * [MediaRepositoryFirebase.getFile], retrying and discarding unsent media, the clears and the
 * scheduling of waiting sends. Files go through a [FakeMediaFileStorage] (no network), sends
 * through a [FakeMediaSendScheduler] (nothing runs), and the outbox and the cache are real, in a
 * temporary folder. Robolectric gives the outbox Android's org.json. The send itself and the
 * methods using Firestore are checked in [MediaRepositoryFirestoreTest].
 */
@RunWith(RobolectricTestRunner::class)
class MediaRepositoryFirebaseTest {

  @get:Rule val tmp = TemporaryFolder()

  private val fileStorage = FakeMediaFileStorage()
  private val scheduler = FakeMediaSendScheduler()
  private val cacheDir by lazy { File(tmp.root, "media") }
  private val outboxDir by lazy { File(tmp.root, "media-outbox") }

  private val c1 = CareCircle(id = "c1", storageBucket = "bucket-c1")
  private val c2 = CareCircle(id = "c2", storageBucket = "bucket-c2")

  private fun picture(circleId: String, mediaId: String) =
      MediaItem.Picture(
          id = mediaId,
          authorId = "u1",
          storagePath = "careCircles/$circleId/media/$mediaId.jpg",
      )

  private val text = MediaItem.Text(id = "t1", authorId = "u1", text = "Hello")

  /** A file holding [content], as the camera would give it. */
  private fun source(content: String = "bytes"): File = tmp.newFile().apply { writeText(content) }

  /** A new outbox on [outboxDir], as after an app restart. */
  private fun TestScope.newOutbox() =
      MediaOutbox(outboxDir, ioDispatcher = StandardTestDispatcher(testScheduler))

  private fun TestScope.newRepository(outbox: MediaOutbox = newOutbox()) =
      MediaRepositoryFirebase(
          fileStorage,
          MediaFileCache(cacheDir, ioDispatcher = StandardTestDispatcher(testScheduler)),
          outbox,
          mockk<FirebaseFirestore>(),
          scheduler,
      )

  private fun cachedFileNames() = cacheDir.listFiles().orEmpty().map { it.name }.sorted()

  private suspend fun MediaRepositoryFirebase.unsentIds(circleId: String) =
      observeOutbox(circleId).first().map { it.item.id }

  @Test
  fun getFileDownloadsFromTheGivenCircle() = runTest {
    val repository = newRepository()

    val file = repository.getFile(c1, picture("c1", "m1"))

    assertEquals("content of careCircles/c1/media/m1.jpg", file.readText())
    assertEquals(listOf("download c1:careCircles/c1/media/m1.jpg"), fileStorage.calls)
  }

  @Test
  fun getFileOfMediaFromAnotherCircleIsRejectedWithoutDownload() = runTest {
    val repository = newRepository()

    // c10's id starts like c1's: still another circle
    for (other in listOf("c2", "c10")) {
      val error = runCatching { repository.getFile(c1, picture(other, "m1")) }.exceptionOrNull()
      assertTrue("$other's media was accepted", error is IllegalArgumentException)
    }
    assertEquals(emptyList<String>(), fileStorage.calls)
  }

  @Test
  fun getFileOfAnUnsentMediaGivesItsOutboxFileAndATextHasNone() = runTest {
    val repository = newRepository()
    repository.addMedia(c1, picture("c1", "m1"), source("unsent bytes"))

    assertEquals("unsent bytes", repository.getFile(c1, picture("c1", "m1")).readText())
    assertEquals(emptyList<String>(), fileStorage.calls) // nothing downloaded

    val error = runCatching { repository.getFile(c1, text) }.exceptionOrNull()
    assertTrue("failed with $error", error is IllegalArgumentException)
    assertTrue("failed with $error", error?.message.orEmpty().contains("is a text"))
  }

  @Test
  fun addMediaSavesTheMediaThenSchedulesItsSend() = runTest {
    val repository = newRepository()
    val source = source()
    // a send starting at once must find its media in the outbox
    val savedWhenScheduled = mutableListOf<Boolean>()
    scheduler.onSchedule = { circleId, mediaId ->
      savedWhenScheduled += File(outboxDir, "$circleId/$mediaId.json").exists()
    }

    repository.addMedia(c1, picture("c1", "m1"), source)
    repository.addMedia(c1, text, null)

    assertEquals(
        listOf(
            OutboxEntry(picture("c1", "m1"), SendState.WAITING),
            OutboxEntry(text, SendState.WAITING),
        ),
        repository.observeOutbox("c1").first(),
    )
    assertEquals(listOf("schedule c1/m1", "schedule c1/t1"), scheduler.calls)
    assertEquals(listOf(true, true), savedWhenScheduled)
    assertFalse(source.exists()) // moved into the outbox
  }

  @Test
  fun addMediaRefusesWhatFirebaseWouldRefuseBeforeSavingAnything() = runTest {
    val repository = newRepository()
    val item = picture("c1", "m1")
    val refused: Map<String, suspend () -> Unit> =
        mapOf(
            "no author" to { repository.addMedia(c1, item.copy(authorId = ""), source()) },
            "text with a file" to { repository.addMedia(c1, text, source()) },
            "text with a path" to
                {
                  repository.addMedia(c1, text.copy(storagePath = item.storagePath), null)
                },
            "picture without a file" to { repository.addMedia(c1, item, null) },
            "empty file" to { repository.addMedia(c1, item, tmp.newFile()) },
            "missing file" to { repository.addMedia(c1, item, File(tmp.root, "missing.jpg")) },
            "wrong extension" to
                {
                  val gif = item.copy(storagePath = "careCircles/c1/media/m1.gif")
                  repository.addMedia(c1, gif, source())
                },
            "media of another circle" to { repository.addMedia(c1, picture("c2", "m1"), source()) },
            "circle without a bucket" to
                {
                  repository.addMedia(c1.copy(storageBucket = ""), item, source())
                },
        )

    for ((case, call) in refused) {
      val error = runCatching { call() }.exceptionOrNull()
      assertTrue("$case: failed with $error", error is IllegalArgumentException)
    }
    // refused at once, rather than failing in the background later
    assertEquals(emptyList<String>(), repository.unsentIds("c1"))
    assertEquals(emptyList<String>(), scheduler.calls)
  }

  @Test
  fun aMediaWhoseSchedulingFailedIsSavedAndScheduledAtTheNextStart() = runTest {
    val outbox = newOutbox()
    val repository = newRepository(outbox)
    scheduler.scheduleError = IllegalStateException("WorkManager isn't set up")

    // returns normally: the media is saved, and adding it again would be refused
    repository.addMedia(c1, picture("c1", "waiting"), source())
    repository.addMedia(c1, picture("c1", "failed"), source())
    outbox.startSending("c1", "failed")
    outbox.markFailed("c1", "failed")
    scheduler.scheduleError = null
    scheduler.calls.clear()

    newRepository().scheduleWaitingSends()

    // a failed media is sent again only when the user retries it
    assertEquals(listOf("schedule c1/waiting"), scheduler.calls)
  }

  @Test
  fun retrySendReschedulesOnceForTwoQuickTaps() = runTest {
    val outbox = newOutbox()
    val repository = newRepository(outbox)
    repository.addMedia(c1, picture("c1", "m1"), source())
    outbox.startSending("c1", "m1")
    outbox.markFailed("c1", "m1")
    scheduler.calls.clear()

    repository.retrySend("c1", "m1")
    repository.retrySend("c1", "m1")

    // a second reschedule would replace the send the first one started
    assertEquals(listOf("reschedule c1/m1"), scheduler.calls)
    assertEquals(SendState.WAITING, outbox.get("c1", "m1")?.state)
  }

  @Test
  fun discardUnsentCancelsItsSendAndRemovesItAndItsFile() = runTest {
    val repository = newRepository()
    repository.addMedia(c1, picture("c1", "m1"), source())

    repository.discardUnsent("c1", "m1")

    assertEquals(listOf("schedule c1/m1", "cancel c1/m1"), scheduler.calls)
    assertEquals(emptyList<String>(), repository.unsentIds("c1"))
    assertEquals(emptyList<String>(), File(outboxDir, "c1").list().orEmpty().toList())
  }

  @Test
  fun clearLocalMediaOfACircleKeepsOtherCircles() = runTest {
    val repository = newRepository()
    repository.getFile(c1, picture("c1", "m1"))
    repository.getFile(c2, picture("c2", "m1"))
    repository.addMedia(c1, picture("c1", "m2"), source())
    repository.addMedia(c2, picture("c2", "m2"), source())
    scheduler.calls.clear()

    repository.clearLocalMedia("c1")

    assertEquals(listOf("careCircles_c2_media_m1.jpg"), cachedFileNames())
    assertEquals(emptyList<String>(), repository.unsentIds("c1"))
    assertEquals(listOf("m2"), repository.unsentIds("c2"))
    assertEquals(listOf("cancelCircle c1"), scheduler.calls)
  }

  @Test
  fun clearLocalMediaDeletesEveryCircle() = runTest {
    val repository = newRepository()
    repository.getFile(c1, picture("c1", "m1"))
    repository.getFile(c2, picture("c2", "m1"))
    repository.addMedia(c2, picture("c2", "m2"), source())
    scheduler.calls.clear()

    repository.clearLocalMedia()

    assertEquals(emptyList<String>(), cachedFileNames())
    assertEquals(emptyList<String>(), repository.unsentIds("c2"))
    assertEquals(listOf("cancelAll"), scheduler.calls)
    repository.getFile(c1, picture("c1", "m1")) // downloaded again
    assertEquals(3, fileStorage.calls.size)
  }

  @Test
  fun clearLocalMediaWithAnInvalidIdClearsNothing() = runTest {
    val repository = newRepository()
    repository.getFile(c1, picture("c1", "m1"))
    repository.addMedia(c1, picture("c1", "m2"), source())
    scheduler.calls.clear()

    // the outbox accepts '_' and the cache '.': neither may clear anything for such an id
    for (badId in listOf("", "c_1", ".", "c1/..")) {
      val error = runCatching { repository.clearLocalMedia(badId) }.exceptionOrNull()
      assertTrue("'$badId': failed with $error", error is IllegalArgumentException)
    }
    assertEquals(listOf("careCircles_c1_media_m1.jpg"), cachedFileNames())
    assertEquals(listOf("m2"), repository.unsentIds("c1"))
    assertEquals(emptyList<String>(), scheduler.calls)
  }
}
