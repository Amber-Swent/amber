// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.request.Options
import com.github.se.amber.data.media.image.MediaPictureFetcher
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaPicture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Checks that the app's image loader shows media pictures through the repository. */
@RunWith(RobolectricTestRunner::class)
class AmberApplicationTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun theManifestRunsTheApp() {
    // Without android:name in the manifest, Android would run a plain Application instead.
    assertTrue(context is AmberApplication)
  }

  @Test
  fun registersTheMediaPictureKeyerAndFetcher() {
    val imageLoader = AmberApplication().newImageLoader(context)
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
  fun keepsNoCopyOfTheMediaInCoilsDiskCache() {
    // private photos are kept on disk only by the media repository's cache
    assertNull(AmberApplication().newImageLoader(context).diskCache)
  }
}
