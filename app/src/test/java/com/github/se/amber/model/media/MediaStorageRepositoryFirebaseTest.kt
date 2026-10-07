// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

import com.google.firebase.storage.FileDownloadTask
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.io.File
import java.io.IOException
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

/** Checks [MediaStorageRepositoryFirebase] against a mocked Firebase Storage: no network. */
@OptIn(ExperimentalCoroutinesApi::class) // runCurrent
class MediaStorageRepositoryFirebaseTest {

  private val destination = File("unused.jpg") // never written: the download is mocked

  /**
   * A download task in the state that `Task.await()` reads: finished with [failure] (null for
   * success), cancelled by Firebase if [cancelled], or still running if [complete] is false.
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

    MediaStorageRepositoryFirebase(firebase.storage)
        .downloadToFile("careCircles/c1/media/m1.jpg", destination)

    verify(firebase.root).child("careCircles/c1/media/m1.jpg")
    verify(firebase.file).getFile(destination)
  }

  @Test
  fun failedDownloadThrowsItsError() = runTest {
    val firebase = FakeFirebase(downloadTask(failure = IOException("no network")))

    val error = runCatching {
      MediaStorageRepositoryFirebase(firebase.storage).downloadToFile("a/m1.jpg", destination)
    }
        .exceptionOrNull()

    assertTrue(error is IOException)
    assertEquals("no network", error?.message)
  }

  @Test
  fun downloadCancelledByFirebaseIsAFailure() = runTest {
    val firebase = FakeFirebase(downloadTask(cancelled = true))

    val error = runCatching {
      MediaStorageRepositoryFirebase(firebase.storage).downloadToFile("a/m1.jpg", destination)
    }
        .exceptionOrNull()

    // an IOException, not a CancellationException that would silently stop the caller
    assertTrue(error is IOException)
  }

  @Test
  fun cancellingTheCallCancelsTheDownload() = runTest {
    val task = downloadTask(complete = false)
    val repository = MediaStorageRepositoryFirebase(FakeFirebase(task).storage)

    val call = launch { repository.downloadToFile("a/m1.jpg", destination) }
    runCurrent() // waiting for the download
    verify(task, never()).cancel()
    call.cancel()
    call.join()

    verify(task).cancel() // the download stops instead of writing into a file nobody wants
  }
}
