// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import android.app.Application
import android.net.Uri
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.google.android.gms.common.api.Status
import com.google.android.gms.tasks.Task
import com.google.firebase.storage.FileDownloadTask
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.StorageTask
import com.google.firebase.storage.UploadTask
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Checks [MediaFileStorageFirebase] against a mocked Firebase Storage: no network. Runs under
 * Robolectric because `upload` builds a real [Uri] for its file.
 */
@OptIn(ExperimentalCoroutinesApi::class) // runCurrent
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE)
class MediaFileStorageFirebaseTest {

  private val circle = CareCircle(id = "c1", storageBucket = "bucket-c1")

  private val destination = File("unused.jpg") // never written: the download is mocked

  @get:Rule val tmp = TemporaryFolder()

  /** The file to upload: one byte, since `upload` refuses an empty file. Its bytes aren't sent. */
  private val upload by lazy { tmp.newFile("picture.jpg").apply { writeBytes(byteArrayOf(1)) } }

  /** A file whose [File.length] is [bytes], without writing them to disk. */
  private fun fileOfLength(bytes: Long) =
      object : File(tmp.root, "sized.jpg") {
        override fun length() = bytes
      }

  private val picture =
      MediaItem.Picture(id = "m1", authorId = "u1", storagePath = "careCircles/c1/media/m1.jpg")

  /**
   * Puts this task in the state that `Task.await()` reads: finished with [failure] (null for
   * success), cancelled if [cancelled], or still running if [complete] is false. Its result is
   * null, which the storage ignores.
   *
   * Stubs the getters kotlinx-coroutines' `await()` reads today; a coroutines upgrade that reads
   * others could break these tests.
   */
  private fun <T : Task<*>> T.inState(
      complete: Boolean = true,
      failure: Exception? = null,
      cancelled: Boolean = false,
  ): T = apply {
    whenever(isComplete).thenReturn(complete)
    whenever(exception).thenReturn(failure)
    whenever(isCanceled).thenReturn(cancelled)
  }

  /** The error Firebase Storage reports for an HTTP [httpCode] (404: not found, 403: denied). */
  private fun storageError(httpCode: Int) =
      requireNotNull(StorageException.fromExceptionAndHttpCode(null, httpCode))

  /**
   * A mocked Firebase Storage whose file references download through [download], upload through
   * [upload] and delete through [deletion].
   */
  private class FakeFirebase(
      download: FileDownloadTask,
      upload: UploadTask,
      deletion: Task<Void>,
  ) {
    val file =
        mock<StorageReference> {
          on { getFile(any<File>()) } doReturn download
          on { putFile(any<Uri>(), any<StorageMetadata>()) } doReturn upload
          on { delete() } doReturn deletion
        }
    val root = mock<StorageReference> { on { child(any()) } doReturn file }
    val storage = mock<FirebaseStorage> { on { reference } doReturn root }
  }

  private fun fakeFirebase(
      download: FileDownloadTask = mock<FileDownloadTask>().inState(),
      upload: UploadTask = mock<UploadTask>().inState(),
      deletion: Task<Void> = mock<Task<Void>>().inState(),
  ) = FakeFirebase(download, upload, deletion)

  /** Runs [call] and returns what it threw, or null if it returned. */
  private suspend fun errorOf(call: suspend () -> Unit): Throwable? =
      try {
        call()
        null
      } catch (e: Throwable) {
        e
      }

  /**
   * Starts [call], cancels it while its transfer [task] is still running, and returns what it
   * threw. Checks that [task] wasn't cancelled before; each test checks it was cancelled after.
   */
  private suspend fun TestScope.cancelWhileRunning(
      task: StorageTask<*>,
      call: suspend () -> Unit,
  ): Throwable? {
    var thrown: Throwable? = null
    val job = launch {
      try {
        call()
      } catch (e: Throwable) {
        thrown = e
        throw e
      }
    }
    runCurrent() // waiting for the transfer
    verify(task, never()).cancel()
    job.cancel()
    job.join()
    return thrown
  }

  // ---------------------------------------------------------------------------------------------
  // All operations

  @Test
  fun byDefaultTheBucketComesFromCircleStorageProvider() = runTest {
    // the app's instance: no mock. CircleStorageProvider refuses a circle whose bucket isn't
    // assigned before touching Firebase, so this needs neither Firebase nor the network
    val fileStorage = MediaFileStorageFirebase()
    val pending = CareCircle(id = "pending")
    val message = "Storage is not ready for care circle pending"
    val pendingPicture = picture.copy(storagePath = "careCircles/pending/media/m1.jpg")

    val downloadError = errorOf { fileStorage.download(pending, picture.storagePath, destination) }
    val uploadError = errorOf { fileStorage.upload(pending, pendingPicture, upload) }
    val deleteError = errorOf { fileStorage.delete(pending, picture.storagePath) }

    assertTrue("download", downloadError is IllegalArgumentException)
    assertEquals("download", message, downloadError?.message)
    assertTrue("upload", uploadError is IllegalArgumentException)
    assertEquals("upload", message, uploadError?.message)
    assertTrue("delete", deleteError is IllegalArgumentException)
    assertEquals("delete", message, deleteError?.message)
  }

  @Test
  fun uploadChecksTheItemAndFileBeforeTheBucket() = runTest {
    val fileStorage = MediaFileStorageFirebase()
    val pending = CareCircle(id = "c1")
    val text = MediaItem.Text(id = "t1")

    val textError = errorOf { fileStorage.upload(pending, text, upload) }
    val emptyError = errorOf { fileStorage.upload(pending, picture, tmp.newFile("empty.jpg")) }

    // a bug in the item or file is reported as such, not as a bucket that isn't assigned yet
    assertEquals("Media t1 is a text: it has no file to upload", textError?.message)
    assertEquals("Media m1's file is empty or over 25 MiB", emptyError?.message)
  }

  @Test
  fun aTransferCancelledByFirebaseThrowsItsError() = runTest {
    // what Firebase really does when it cancels a transfer: the task fails with ERROR_CANCELED
    val cancelled = StorageException.fromErrorStatus(Status.RESULT_CANCELED)
    val firebase =
        fakeFirebase(upload = mock<UploadTask>().inState(failure = cancelled, cancelled = true))

    val error = errorOf {
      MediaFileStorageFirebase { firebase.storage }.upload(circle, picture, upload)
    }

    // passed on as is, not a CancellationException that would silently stop the caller
    assertSame(cancelled, error)
    assertEquals(StorageException.ERROR_CANCELED, cancelled.errorCode)
  }

  @Test
  fun aTaskCancelledWithoutAnErrorIsAFailure() = runTest {
    // defensive: Firebase's own transfers always carry an error when cancelled
    val firebase = fakeFirebase(download = mock<FileDownloadTask>().inState(cancelled = true))

    val error = errorOf {
      MediaFileStorageFirebase { firebase.storage }.download(circle, "a/m1.jpg", destination)
    }

    // an IOException, not a CancellationException that would silently stop the caller
    assertTrue(error is IOException)
    assertEquals("Download of a/m1.jpg was cancelled", error?.message)
  }

  // ---------------------------------------------------------------------------------------------
  // Download

  @Test
  fun downloadsTheFileAtStoragePathIntoDestination() = runTest {
    val firebase = fakeFirebase()

    MediaFileStorageFirebase { firebase.storage }
        .download(circle, "careCircles/c1/media/m1.jpg", destination)

    verify(firebase.root).child("careCircles/c1/media/m1.jpg")
    verify(firebase.file).getFile(destination)
  }

  @Test
  fun downloadsFromTheBucketOfTheGivenCircle() = runTest {
    val firebase = fakeFirebase()
    val other = fakeFirebase()
    val buckets = mapOf("c1" to firebase.storage, "c2" to other.storage)
    val fileStorage = MediaFileStorageFirebase { buckets.getValue(it.id) }

    fileStorage.download(CareCircle(id = "c2"), "careCircles/c2/media/m1.jpg", destination)

    verify(other.file).getFile(destination)
    verify(firebase.root, never()).child(any())
  }

  @Test
  fun downloadsStopRetryingAfterFiveSeconds() = runTest {
    val firebase = fakeFirebase()

    MediaFileStorageFirebase { firebase.storage }.download(circle, "a/m1.jpg", destination)

    // before getFile: the download task reads it when it is created
    inOrder(firebase.storage, firebase.file) {
      verify(firebase.storage).maxDownloadRetryTimeMillis = 5_000L
      verify(firebase.file).getFile(destination)
    }
  }

  @Test
  fun failedDownloadThrowsItsError() = runTest {
    val notFound = storageError(404)
    val firebase = fakeFirebase(download = mock<FileDownloadTask>().inState(failure = notFound))

    val error = errorOf {
      MediaFileStorageFirebase { firebase.storage }.download(circle, "a/m1.jpg", destination)
    }

    assertSame(notFound, error)
  }

  @Test
  fun cancellingTheCallCancelsTheDownloadAndRethrowsTheCancellation() = runTest {
    val task = mock<FileDownloadTask>().inState(complete = false)
    val fileStorage = MediaFileStorageFirebase { fakeFirebase(download = task).storage }

    val thrown = cancelWhileRunning(task) { fileStorage.download(circle, "a/m1.jpg", destination) }

    verify(task).cancel() // the download stops instead of writing into a file nobody wants
    // a cancellation, not an IOException: a ViewModel whose screen closed must not see an error
    assertTrue(thrown is CancellationException)
  }

  // ---------------------------------------------------------------------------------------------
  // Upload

  @Test
  fun uploadsTheFileToItsStoragePathInTheCirclesBucket() = runTest {
    val firebase = fakeFirebase()
    val other = fakeFirebase()
    val buckets = mapOf("c1" to firebase.storage, "c2" to other.storage)

    MediaFileStorageFirebase { buckets.getValue(it.id) }.upload(circle, picture, upload)

    verify(firebase.root).child("careCircles/c1/media/m1.jpg")
    verify(firebase.file).putFile(eq(Uri.fromFile(upload)), any())
    verify(other.root, never()).child(any())
  }

  @Test
  fun uploadsCarryTheContentTypeAndAuthorTheSecurityRulesCheck() = runTest {
    val audio =
        MediaItem.Audio(id = "m2", authorId = "u1", storagePath = "careCircles/c1/media/m2.m4a")
    val items =
        mapOf(
            picture to "image/jpeg",
            picture.copy(storagePath = "careCircles/c1/media/m1.png") to "image/png",
            audio to "audio/mp4",
        )

    for ((item, contentType) in items) {
      val firebase = fakeFirebase()

      MediaFileStorageFirebase { firebase.storage }.upload(circle, item, upload)

      val metadata = argumentCaptor<StorageMetadata>()
      verify(firebase.file).putFile(any(), metadata.capture())
      assertEquals(item.storagePath, contentType, metadata.firstValue.contentType)
      // the rules accept exactly one custom field, the uploader's uid
      assertEquals(item.storagePath, setOf("authorId"), metadata.firstValue.customMetadataKeys)
      assertEquals(item.storagePath, "u1", metadata.firstValue.getCustomMetadata("authorId"))
    }
  }

  @Test
  fun uploadRefusesAMediaWithoutAnAcceptedFile() = runTest {
    val firebase = fakeFirebase()
    val fileStorage = MediaFileStorageFirebase { firebase.storage }
    val gif = picture.copy(storagePath = "careCircles/c1/media/m1.gif")

    val textError = errorOf { fileStorage.upload(circle, MediaItem.Text(id = "t1"), upload) }
    val gifError = errorOf { fileStorage.upload(circle, gif, upload) }

    assertTrue("text", textError is IllegalArgumentException)
    assertEquals("text", "Media t1 is a text: it has no file to upload", textError?.message)
    // the rules would refuse it: a bug, reported before anything is sent
    assertTrue("gif", gifError is IllegalArgumentException)
    verify(firebase.root, never()).child(any())
  }

  @Test
  fun uploadRefusesAMediaOfAnotherCircle() = runTest {
    val firebase = fakeFirebase()
    val otherCircle = picture.copy(storagePath = "careCircles/c2/media/m1.jpg")

    val error = errorOf {
      MediaFileStorageFirebase { firebase.storage }.upload(circle, otherCircle, upload)
    }

    // the rules would answer "permission denied", which a send could retry forever
    assertTrue(error is IllegalArgumentException)
    assertEquals(
        "Media m1 (careCircles/c2/media/m1.jpg) isn't a media of care circle c1",
        error?.message,
    )
    verify(firebase.root, never()).child(any())
  }

  @Test
  fun uploadRefusesAnEmptyFileOrOneOver25MiB() = runTest {
    val firebase = fakeFirebase()
    val fileStorage = MediaFileStorageFirebase { firebase.storage }
    val maxBytes = 25L * 1024 * 1024

    val emptyError = errorOf { fileStorage.upload(circle, picture, fileOfLength(0)) }
    val largeError = errorOf { fileStorage.upload(circle, picture, fileOfLength(maxBytes + 1)) }

    assertTrue("empty", emptyError is IllegalArgumentException)
    assertTrue("large", largeError is IllegalArgumentException)
    verify(firebase.root, never()).child(any())
  }

  @Test
  fun uploadAcceptsAFileOfExactly25MiB() = runTest {
    val firebase = fakeFirebase()

    MediaFileStorageFirebase { firebase.storage }
        .upload(circle, picture, fileOfLength(25L * 1024 * 1024))

    // the rules' limit is inclusive
    verify(firebase.file).putFile(any(), any())
  }

  @Test
  fun uploadsStopRetryingAfterOneMinute() = runTest {
    val firebase = fakeFirebase()

    MediaFileStorageFirebase { firebase.storage }.upload(circle, picture, upload)

    // before putFile: the upload task reads it when it is created
    inOrder(firebase.storage, firebase.file) {
      verify(firebase.storage).maxUploadRetryTimeMillis = 60_000L
      verify(firebase.file).putFile(any(), any())
    }
  }

  @Test
  fun failedUploadThrowsItsError() = runTest {
    val denied = storageError(403)
    val firebase = fakeFirebase(upload = mock<UploadTask>().inState(failure = denied))

    val error = errorOf {
      MediaFileStorageFirebase { firebase.storage }.upload(circle, picture, upload)
    }

    // passed on as is, so the send can tell a lasting error from one worth retrying
    assertSame(denied, error)
  }

  @Test
  fun cancellingTheCallCancelsTheUploadAndRethrowsTheCancellation() = runTest {
    val task = mock<UploadTask>().inState(complete = false)
    val fileStorage = MediaFileStorageFirebase { fakeFirebase(upload = task).storage }

    val thrown = cancelWhileRunning(task) { fileStorage.upload(circle, picture, upload) }

    verify(task).cancel() // a cancelled send doesn't keep uploading in the background
    assertTrue(thrown is CancellationException)
  }

  // ---------------------------------------------------------------------------------------------
  // Delete

  @Test
  fun deletesTheFileAtStoragePath() = runTest {
    val firebase = fakeFirebase()

    MediaFileStorageFirebase { firebase.storage }.delete(circle, "careCircles/c1/media/m1.jpg")

    verify(firebase.root).child("careCircles/c1/media/m1.jpg")
    verify(firebase.file).delete()
  }

  @Test
  fun deletionsStopRetryingAfterFiveSeconds() = runTest {
    val firebase = fakeFirebase()

    MediaFileStorageFirebase { firebase.storage }.delete(circle, "a/m1.jpg")

    // Firebase limits a deletion with the download setting, read when the task is created
    inOrder(firebase.storage, firebase.file) {
      verify(firebase.storage).maxDownloadRetryTimeMillis = 5_000L
      verify(firebase.file).delete()
    }
  }

  @Test
  fun deletingAMissingFileSucceeds() = runTest {
    val firebase = fakeFirebase(deletion = mock<Task<Void>>().inState(failure = storageError(404)))

    // returns normally: deleting twice, or a file never uploaded, isn't an error
    MediaFileStorageFirebase { firebase.storage }.delete(circle, "a/m1.jpg")

    verify(firebase.file).delete()
  }

  @Test
  fun otherDeleteErrorsAreThrown() = runTest {
    val denied = storageError(403)
    val firebase = fakeFirebase(deletion = mock<Task<Void>>().inState(failure = denied))

    val error = errorOf { MediaFileStorageFirebase { firebase.storage }.delete(circle, "a/m1.jpg") }

    assertSame(denied, error)
  }
}
