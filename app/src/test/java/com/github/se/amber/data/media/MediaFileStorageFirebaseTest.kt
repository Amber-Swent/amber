// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import com.github.se.amber.model.circle.CareCircle
import com.google.firebase.storage.FileDownloadTask
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

/** Checks [MediaFileStorageFirebase] against a mocked Firebase Storage: no network. */
@OptIn(ExperimentalCoroutinesApi::class) // runCurrent
class MediaFileStorageFirebaseTest {

  private val circle = CareCircle(id = "c1", storageBucket = "bucket-c1")

  private val destination = File("unused.jpg") // never written: the download is mocked

  /**
   * A download task in the state that `Task.await()` reads: finished with [failure] (null for
   * success), cancelled by Firebase if [cancelled], or still running if [complete] is false.
   *
   * Mocks the getters kotlinx-coroutines' `await()` reads today; a coroutines upgrade that reads
   * others could break these tests.
   */
  private fun downloadTask(
      complete: Boolean = true,
      failure: Exception? = null,
      cancelled: Boolean = false,
  ) =
      mock<FileDownloadTask> {
        on { isComplete } doReturn complete
        on { exception } doReturn failure
        on { isCanceled } doReturn cancelled
        on { result } doReturn mock<FileDownloadTask.TaskSnapshot>()
      }

  /** A mocked Firebase Storage whose file references all download through [task]. */
  private class FakeFirebase(task: FileDownloadTask) {
    val file = mock<StorageReference> { on { getFile(any<File>()) } doReturn task }
    val root = mock<StorageReference> { on { child(any()) } doReturn file }
    val storage = mock<FirebaseStorage> { on { reference } doReturn root }
  }

  @Test
  fun downloadsTheFileAtStoragePathIntoDestination() = runTest {
    val firebase = FakeFirebase(downloadTask())

    MediaFileStorageFirebase { firebase.storage }
        .downloadToFile(circle, "careCircles/c1/media/m1.jpg", destination)

    verify(firebase.root).child("careCircles/c1/media/m1.jpg")
    verify(firebase.file).getFile(destination)
  }

  @Test
  fun downloadsFromTheBucketOfTheGivenCircle() = runTest {
    val firebase = FakeFirebase(downloadTask())
    val other = FakeFirebase(downloadTask())
    val buckets = mapOf("c1" to firebase.storage, "c2" to other.storage)
    val fileStorage = MediaFileStorageFirebase { buckets.getValue(it.id) }

    fileStorage.downloadToFile(CareCircle(id = "c2"), "careCircles/c2/media/m1.jpg", destination)

    verify(other.file).getFile(destination)
    verify(firebase.root, never()).child(any())
  }

  @Test
  fun downloadsStopRetryingAfterFiveSeconds() = runTest {
    val firebase = FakeFirebase(downloadTask())

    MediaFileStorageFirebase { firebase.storage }.downloadToFile(circle, "a/m1.jpg", destination)

    verify(firebase.storage).maxDownloadRetryTimeMillis = 5_000L
  }

  @Test
  fun byDefaultTheBucketComesFromCircleStorageProvider() = runTest {
    // the app's instance: no mock. CircleStorageProvider refuses a circle whose bucket isn't
    // assigned before touching Firebase, so this needs neither Firebase nor the network
    val error = runCatching {
      MediaFileStorageFirebase()
          .downloadToFile(
              CareCircle(id = "pending"),
              "careCircles/pending/media/m1.jpg",
              destination,
          )
    }
        .exceptionOrNull()

    assertTrue(error is IllegalArgumentException)
    assertEquals("Storage is not ready for care circle pending", error?.message)
  }

  @Test
  fun failedDownloadThrowsItsError() = runTest {
    val firebase = FakeFirebase(downloadTask(failure = IOException("no network")))

    val error = runCatching {
      MediaFileStorageFirebase { firebase.storage }.downloadToFile(circle, "a/m1.jpg", destination)
    }
        .exceptionOrNull()

    assertTrue(error is IOException)
    assertEquals("no network", error?.message)
  }

  @Test
  fun downloadCancelledByFirebaseIsAFailure() = runTest {
    val firebase = FakeFirebase(downloadTask(cancelled = true))

    val error = runCatching {
      MediaFileStorageFirebase { firebase.storage }.downloadToFile(circle, "a/m1.jpg", destination)
    }
        .exceptionOrNull()

    // an IOException, not a CancellationException that would silently stop the caller
    assertTrue(error is IOException)
  }

  @Test
  fun cancellingTheCallCancelsTheDownloadAndRethrowsTheCancellation() = runTest {
    val task = downloadTask(complete = false)
    val firebase = FakeFirebase(task)
    val fileStorage = MediaFileStorageFirebase { firebase.storage }
    var thrown: Throwable? = null

    val call = launch {
      try {
        fileStorage.downloadToFile(circle, "a/m1.jpg", destination)
      } catch (e: Throwable) {
        thrown = e
        throw e
      }
    }
    runCurrent() // waiting for the download
    verify(task, never()).cancel()
    call.cancel()
    call.join()

    verify(task).cancel() // the download stops instead of writing into a file nobody wants
    // a cancellation, not an IOException: a ViewModel whose screen closed must not see an error
    assertTrue(thrown is CancellationException)
  }
}
