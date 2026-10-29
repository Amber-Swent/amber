// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.image

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaPicture
import com.google.firebase.storage.StorageException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

/** Checks how [MediaPictureFetcher] gets and opens a picture's file, and how it fails. */
@RunWith(RobolectricTestRunner::class)
class MediaPictureFetcherTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val repository = FakePictureRepository()
  private val circle = CareCircle(id = "c1", storageBucket = "bucket-c1")
  private val picture = MediaItem.Picture(id = "m1", storagePath = "careCircles/c1/media/m1.jpg")
  // c10 starts with "c1": the circle check must not take it for c1
  private val otherCirclePicture = picture.copy(storagePath = "careCircles/c10/media/m1.jpg")

  private fun fetcher() =
      MediaPictureFetcher.Factory { repository }
          .create(MediaPicture(circle, picture), Options(context), ImageLoader(context))

  private suspend fun fetchText(): String {
    val result = fetcher().fetch() as SourceFetchResult
    assertEquals(DataSource.DISK, result.dataSource)
    return result.source.use { it.source().readUtf8() }
  }

  private suspend fun fetchError(): Throwable =
      runCatching { fetcher().fetch() }.exceptionOrNull()!!

  private fun logs() = ShadowLog.getLogsForTag(MEDIA_PICTURE_FETCHER_TAG)

  @Test
  fun givesTheBytesOfTheFileFromGetFile() = runTest {
    val file = tempFolder.newFile().apply { writeText("picture bytes") }
    repository.onGetFile = { file }

    assertEquals("picture bytes", fetchText())
    assertEquals(listOf("m1"), repository.requests)
  }

  @Test
  fun opensTheFileBeforeReturningSoItCanBeMovedWhileDecoded() = runTest {
    val file = tempFolder.newFile().apply { writeText("picture bytes") }
    repository.onGetFile = { file }

    val result = fetcher().fetch() as SourceFetchResult
    // A send moving the file, or an eviction deleting it, before Coil decodes it.
    val deleted = file.delete()

    // On Windows, an open file can't be deleted: the failed delete itself proves it is open.
    if (!deleted) assertTrue(System.getProperty("os.name").orEmpty().startsWith("Windows"))
    assertEquals("picture bytes", result.source.use { it.source().readUtf8() })
  }

  @Test
  fun aFileGoneBeforeItIsOpenedIsAskedForAgain() = runTest {
    // The outbox file, moved to the cache by a send right after getFile returned it.
    val moved = File(tempFolder.root, "outbox-m1.jpg")
    val cached = tempFolder.newFile().apply { writeText("picture bytes") }
    repository.onGetFile = { if (repository.requests.size == 1) moved else cached }

    assertEquals("picture bytes", fetchText())
    assertEquals(listOf("m1", "m1"), repository.requests)
    assertTrue(logs().isEmpty())
  }

  @Test
  fun aFileStillMissingTheSecondTimeFails() = runTest {
    repository.onGetFile = { File(tempFolder.root, "missing.jpg") }

    val error = fetchError()

    assertTrue(error is MediaPictureUnavailableException)
    // the second failure, with the first one kept for the log
    val second = error.cause!!
    assertTrue(second is FileNotFoundException)
    assertTrue(second.suppressed.single() is FileNotFoundException)
    assertEquals(2, repository.requests.size)
    assertSame(second, logs().single().throwable)
  }

  @Test
  fun aGetFileFailureIsWrappedAndLoggedAsAWarning() = runTest {
    val offline = IOException("offline and not cached")
    repository.onGetFile = { throw offline }

    val error = fetchError()

    assertTrue(error is MediaPictureUnavailableException)
    assertSame(offline, error.cause)
    val log = logs().single()
    assertEquals(Log.WARN, log.type)
    assertSame(offline, log.throwable)
    assertTrue(log.msg.contains("m1"))
  }

  private suspend fun fetchError(circle: CareCircle, picture: MediaItem.Picture): Throwable =
      runCatching {
        MediaPictureFetcher.Factory { repository }
            .create(MediaPicture(circle, picture), Options(context), ImageLoader(context))
            .fetch()
      }
          .exceptionOrNull()!!

  @Test
  fun aPictureOfAnotherCircleIsLoggedAsAnError() = runTest {
    val wrongCircle = IllegalArgumentException("m1 isn't a media of c1")
    repository.onGetFile = { throw wrongCircle }

    val error = fetchError(circle, otherCirclePicture)

    assertSame(wrongCircle, error.cause)
    val log = logs().single()
    assertEquals(Log.ERROR, log.type)
    assertTrue(log.msg.contains("wrong circle"))
  }

  @Test
  fun aPictureOfAnotherCircleIsAnErrorEvenWhenTheCircleHasNoBucketYet() = runTest {
    val wrongCircle = IllegalArgumentException("m1 isn't a media of c1")
    repository.onGetFile = { throw wrongCircle }

    fetchError(circle.copy(storageBucket = ""), otherCirclePicture)

    // the missing bucket must not hide the bug
    val log = logs().single()
    assertEquals(Log.ERROR, log.type)
    assertTrue(log.msg.contains("wrong circle"))
  }

  @Test
  fun aCircleWithoutBucketYetIsLoggedAsAWarning() = runTest {
    // What getFile throws when the file must be downloaded while the circle is being set up.
    val noBucket = IllegalArgumentException("c1 has no bucket yet")
    repository.onGetFile = { throw noBucket }

    val error = fetchError(circle.copy(storageBucket = ""), picture)

    assertSame(noBucket, error.cause)
    val log = logs().single()
    assertEquals(Log.WARN, log.type)
    assertTrue(log.msg.contains("no bucket"))
  }

  @Test
  fun anotherInvalidArgumentOfAPictureOfTheCircleIsLoggedAsAnError() = runTest {
    // e.g. a storage path the cache refuses: not offline, a bug
    val invalid = IllegalArgumentException("invalid storage path")
    repository.onGetFile = { throw invalid }

    fetchError()

    val log = logs().single()
    assertEquals(Log.ERROR, log.type)
    assertTrue(log.msg.contains("invalid media"))
  }

  @Test
  fun aTimeoutInGetFileIsAFailureNotACancellation() = runTest {
    repository.onGetFile = { withTimeout(1.milliseconds) { awaitCancellation() } }

    val error = fetchError()

    // Coil would take a CancellationException for a cancelled load and never show the failure.
    assertTrue(error is MediaPictureUnavailableException)
    assertTrue(error.cause is CancellationException)
  }

  @Test
  fun aCancelledFetchStopsWithoutFailingOrLogging() = runTest {
    val started = CompletableDeferred<Unit>()
    repository.onGetFile = {
      started.complete(Unit)
      awaitCancellation()
    }
    val fetch = async { fetcher().fetch() }
    started.await()

    fetch.cancel()
    val error = runCatching { fetch.await() }.exceptionOrNull()

    assertTrue(error is CancellationException)
    assertTrue(logs().isEmpty())
  }

  @Test
  fun aCancelledFetchWhoseGetFileThrowsSomethingElseStopsWithoutLogging() = runTest {
    // A download that turns its cancellation into an IOException, e.g. "stream closed".
    val started = CompletableDeferred<Unit>()
    repository.onGetFile = {
      started.complete(Unit)
      try {
        awaitCancellation()
      } catch (e: CancellationException) {
        throw IOException("download stopped", e)
      }
    }
    val fetch = async { fetcher().fetch() }
    started.await()

    fetch.cancel()
    val error = runCatching { fetch.await() }.exceptionOrNull()

    assertTrue(error is CancellationException)
    assertTrue(logs().isEmpty())
  }

  /** The failure the fetcher gives when getFile throws [error]. */
  private suspend fun failureOf(error: Exception): MediaPictureUnavailableException {
    repository.onGetFile = { throw error }
    return fetchError() as MediaPictureUnavailableException
  }

  /** The error Firebase Storage gives for a request that failed with [cause] and [httpCode]. */
  private fun storageError(cause: Exception, httpCode: Int): StorageException =
      StorageException.fromExceptionAndHttpCode(cause, httpCode)!!

  @Test
  fun connectionErrorsAreConnectionFailures() = runTest {
    listOf(
            // What a Firebase Storage download ends with offline: Firebase builds it this way
            // when its request fails without an HTTP answer (code -2), once it gives up retrying.
            storageError(SocketException("Network subsystem is unavailable"), httpCode = -2),
            // also without an HTTP answer, with a cause that isn't a platform network error
            storageError(SSLHandshakeException("connection closed by peer"), httpCode = -2),
            // the connection cut while the file was being read: Firebase keeps the HTTP code
            storageError(SocketException("Connection reset"), httpCode = 200),
            UnknownHostException("firebasestorage.googleapis.com"),
            ConnectException("connection refused"),
            SocketTimeoutException("read timed out"),
            // wrapped by whoever downloaded the file
            IOException("download failed", UnknownHostException("firebasestorage.googleapis.com")),
        )
        .forEach { assertTrue("$it", failureOf(it).isConnectionFailure) }
  }

  @Test
  fun otherErrorsAreNotConnectionFailures() = runTest {
    // Coming back online wouldn't change them.
    listOf(
            storageError(IOException("object not found"), httpCode = 404),
            storageError(IOException("permission denied"), httpCode = 403),
            storageError(IOException("internal error"), httpCode = 500),
            // Firebase retries these, but gives up with their own code, not as a connection one
            storageError(IOException("service unavailable"), httpCode = 503),
            storageError(IOException("too many requests"), httpCode = 429),
            storageError(IOException("request timeout"), httpCode = 408),
            // writing the downloaded file failed
            storageError(IOException("no space left on device"), httpCode = 200),
            IOException("no space left on device"),
            IllegalArgumentException("c1 has no bucket yet"),
        )
        .forEach { assertFalse("$it", failureOf(it).isConnectionFailure) }
  }

  @Test
  fun aConnectionFailureIsLoggedAsAWarningWithItsReason() = runTest {
    val offline = UnknownHostException("firebasestorage.googleapis.com")

    failureOf(offline)

    val log = logs().single()
    assertEquals(Log.WARN, log.type)
    assertSame(offline, log.throwable)
    assertTrue(log.msg.contains("connection problem"))
  }

  @Test
  fun aTimeoutInGetFileIsNotAConnectionFailure() = runTest {
    // getFile giving up by itself says nothing about the connection
    repository.onGetFile = { withTimeout(1.milliseconds) { awaitCancellation() } }

    assertFalse((fetchError() as MediaPictureUnavailableException).isConnectionFailure)
  }

  @Test
  fun theFactoryGetsTheRepositoryOnlyWhenAPictureIsLoaded() {
    var built = 0
    val factory = MediaPictureFetcher.Factory {
      built++
      repository
    }
    assertEquals(0, built)

    factory.create(MediaPicture(circle, picture), Options(context), ImageLoader(context))

    assertEquals(1, built)
  }
}
