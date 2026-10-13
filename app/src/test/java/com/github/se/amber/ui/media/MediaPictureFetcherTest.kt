// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.media

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
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

  private fun logs() = ShadowLog.getLogsForTag(MEDIA_IMAGE_TAG)

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

  @Test
  fun aPictureOfAnotherCircleIsLoggedAsAnError() = runTest {
    val wrongCircle = IllegalArgumentException("m1 isn't a media of c1")
    repository.onGetFile = { throw wrongCircle }

    val error = fetchError()

    assertSame(wrongCircle, error.cause)
    assertEquals(Log.ERROR, logs().single().type)
  }

  @Test
  fun aCircleWithoutBucketYetIsLoggedAsAWarning() = runTest {
    // What getFile throws when the file must be downloaded while the circle is being set up.
    val noBucket = IllegalArgumentException("c1 has no bucket yet")
    repository.onGetFile = { throw noBucket }

    val error =
        runCatching {
          MediaPictureFetcher.Factory { repository }
              .create(
                  MediaPicture(circle.copy(storageBucket = ""), picture),
                  Options(context),
                  ImageLoader(context),
              )
              .fetch()
        }
            .exceptionOrNull()!!

    assertSame(noBucket, error.cause)
    val log = logs().single()
    assertEquals(Log.WARN, log.type)
    assertTrue(log.msg.contains("no bucket"))
  }

  @Test
  fun aTimeoutInGetFileIsAFailureNotACancellation() = runTest {
    repository.onGetFile = { withTimeout(1) { awaitCancellation() } }

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
