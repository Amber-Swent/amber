// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkerParameters
import coil3.request.Options
import com.github.se.amber.data.account.AccountDeletionRepository
import com.github.se.amber.data.account.AccountDeletionRepositoryProvider
import com.github.se.amber.data.account.DeletionMarker
import com.github.se.amber.data.media.MediaSendWorker
import com.github.se.amber.data.media.image.MediaPictureFetcher
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaPicture
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Checks that the app sets up its two libraries: the image loader showing media pictures through
 * the repository, and WorkManager creating the media send worker. Also checks that it finishes an
 * interrupted account deletion at startup.
 */
@RunWith(RobolectricTestRunner::class)
class AmberApplicationTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  /** The instance Android runs, built from the manifest: closer to the app than a new one. */
  private val app: AmberApplication
    get() = context as AmberApplication

  private val deletion: AccountDeletionRepository = mockk(relaxUnitFun = true)
  // a failure in a launched coroutine never reaches the test by itself: it is only logged
  private val uncaught = mutableListOf<Throwable>()
  private val rightHere =
      CoroutineScope(
          Dispatchers.Unconfined + CoroutineExceptionHandler { _, error -> uncaught += error }
      )

  private fun marker(set: Boolean) =
      object : DeletionMarker {
        override val isSet = set

        override fun set() = Unit

        override fun remove() = Unit
      }

  @Test
  fun theManifestRunsTheApp() {
    // Without android:name in the manifest, Android would run a plain Application instead.
    assertTrue(context is AmberApplication)
  }

  @Test
  fun registersTheMediaPictureKeyerAndFetcher() {
    val imageLoader = app.newImageLoader(context)
    val picture = MediaPicture(CareCircle(id = "c1"), MediaItem.Picture(id = "m1"))
    val options = Options(context)

    assertEquals("media/c1/m1", imageLoader.components.key(picture, options))
    // Not created here: that would build the real repository, which needs Firebase.
    assertTrue(
        imageLoader.components.fetcherFactories.any { (factory, type) ->
          factory is MediaPictureFetcher.Factory && type == MediaPicture::class
        }
    )
  }

  @Test
  fun givesWorkManagerTheMediaSendWorkerFactory() {
    val factory = app.workManagerConfiguration.workerFactory

    val worker =
        factory.createWorker(
            context,
            MediaSendWorker::class.java.name,
            mockk<WorkerParameters>(relaxed = true),
        )

    // created without its sender: the repository is built only when the worker runs
    assertEquals(MediaSendWorker::class, worker!!::class)
  }

  @Test
  fun keepsNoCopyOfTheMediaInCoilsDiskCache() {
    // private photos are kept on disk only by the media repository's cache
    assertNull(app.newImageLoader(context).diskCache)
  }

  @Test
  fun aLaunchWithoutAMarkerBuildsNoDeletionRepository() {
    var attempts = 0
    val provider = AccountDeletionRepositoryProvider {
      attempts++
      deletion
    }

    app.finishInterruptedDeletion(marker(set = false), provider, rightHere)

    assertEquals(0, attempts)
  }

  @Test
  fun aMarkerFinishesTheDeletionAndAFailureDoesNotStopTheAppFromOpening() {
    app.finishInterruptedDeletion(
        marker(set = true),
        AccountDeletionRepositoryProvider { deletion },
        rightHere,
    )
    coVerify(exactly = 1) { deletion.finishInterruptedDeletion() }

    coEvery { deletion.finishInterruptedDeletion() } throws IllegalStateException("Boom")
    app.finishInterruptedDeletion(
        marker(set = true),
        AccountDeletionRepositoryProvider { deletion },
        rightHere,
    )
    app.finishInterruptedDeletion(
        marker(set = true),
        AccountDeletionRepositoryProvider { throw IllegalStateException("Firebase not ready") },
        rightHere,
    )

    coVerify(exactly = 2) { deletion.finishInterruptedDeletion() }
    assertEquals(emptyList<Throwable>(), uncaught)
  }
}
