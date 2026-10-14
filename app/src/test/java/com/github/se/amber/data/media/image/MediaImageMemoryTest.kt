// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.image

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.asImage
import coil3.memory.MemoryCache
import coil3.request.Options
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaPicture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Checks the memory cache keys of media pictures and how [MediaImageMemory] clears them. */
@RunWith(RobolectricTestRunner::class)
class MediaImageMemoryTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val imageLoader = ImageLoader(context)
  private val memoryCache = imageLoader.memoryCache!!
  private val memory = MediaImageMemory(imageLoader)

  private fun key(circleId: String, mediaId: String) =
      MediaPictureKeyer()
          .key(
              MediaPicture(CareCircle(id = circleId), MediaItem.Picture(id = mediaId)),
              Options(context),
          )

  /**
   * Puts a decoded picture of [mediaId] in [circleId] in the memory cache, as Coil would: under the
   * keyer's key, by default with no extras, since MediaImage applies no transformations.
   */
  private fun putPicture(circleId: String, mediaId: String, extras: Map<String, String> = mapOf()) {
    val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
    memoryCache[MemoryCache.Key(key(circleId, mediaId), extras)] =
        MemoryCache.Value(bitmap.asImage())
  }

  private fun cachedKeys() = memoryCache.keys.map { it.key }.sorted()

  @Test
  fun aPictureIsKeyedByItsCircleAndMediaIdOnly() {
    val picture = MediaItem.Picture(id = "m1", storagePath = "careCircles/c1/media/m1.jpg")
    val options = Options(context)
    val keyer = MediaPictureKeyer()

    val key = keyer.key(MediaPicture(CareCircle(id = "c1"), picture), options)

    assertEquals("media/c1/m1", key)
    // Same media after an edit of its description or of its circle: same decoded picture.
    assertEquals(
        key,
        keyer.key(
            MediaPicture(
                CareCircle(id = "c1", name = "Renamed"),
                picture.copy(description = "new"),
            ),
            options,
        ),
    )
    assertNotEquals(key, keyer.key(MediaPicture(CareCircle(id = "c2"), picture), options))
  }

  @Test
  fun clearCircleRemovesOnlyThatCirclesPictures() {
    putPicture("c1", "m1")
    // a request with transformations would also add its size to the key
    putPicture("c1", "m1", extras = mapOf("coil#size" to "100x100"))
    putPicture("c1", "m2")
    // c10's keys start like c1's: still another circle
    putPicture("c10", "m1")
    putPicture("c2", "m1")

    memory.clearCircle("c1")

    assertEquals(listOf("media/c10/m1", "media/c2/m1"), cachedKeys())
  }

  @Test
  fun clearAllRemovesEveryPicture() {
    putPicture("c1", "m1")
    putPicture("c2", "m1")

    memory.clearAll()

    assertEquals(emptyList<String>(), cachedKeys())
  }

  @Test
  fun clearCircleRejectsAnIdThatCouldMatchOtherCircles() {
    putPicture("c1", "m1")

    for (circleId in listOf("", " ", "c1/m1")) {
      assertThrows(IllegalArgumentException::class.java) { memory.clearCircle(circleId) }
    }
    assertEquals(listOf("media/c1/m1"), cachedKeys())
  }

  @Test
  fun clearingALoaderWithoutMemoryCacheDoesNothing() {
    val noMemory = MediaImageMemory(ImageLoader.Builder(context).memoryCache(null).build())

    noMemory.clearCircle("c1")
    noMemory.clearAll()
  }
}
