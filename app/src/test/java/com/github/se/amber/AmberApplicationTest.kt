// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkerParameters
import coil3.request.Options
import com.github.se.amber.data.media.MediaSendWorker
import com.github.se.amber.data.media.image.MediaPictureFetcher
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaPicture
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Checks that the app sets up its two libraries: the image loader showing media pictures through
 * the repository, and WorkManager creating the media send worker.
 */
@RunWith(RobolectricTestRunner::class)
class AmberApplicationTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  /** The instance Android runs, built from the manifest: closer to the app than a new one. */
  private val app: AmberApplication
    get() = context as AmberApplication

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
}
