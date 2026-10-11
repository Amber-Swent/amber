// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.media

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.request.SuccessResult
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
class MediaImageTest {
  @get:Rule val composeTestRule = createComposeRule()
  @get:Rule val tempFolder = TemporaryFolder()

  private val context: Context = ApplicationProvider.getApplicationContext()

  /** Stands in for Coil's decoding, so the tests need neither real images nor native graphics. */
  private val decoder = FakeDecoder()

  private lateinit var photo: File
  private lateinit var otherPhoto: File

  @Before
  fun setUp() {
    photo = tempFolder.newFile("photo.jpg")
    otherPhoto = tempFolder.newFile("other.jpg")
    SingletonImageLoader.setUnsafe(ImageLoader.Builder(context).components { add(decoder) }.build())
  }

  @After
  fun tearDown() {
    SingletonImageLoader.reset()
  }

  private fun setImage(mediaId: String = "m1", loadFile: suspend () -> File) {
    composeTestRule.setContent {
      MediaImage(
          mediaId = mediaId,
          loadFile = loadFile,
          contentDescription = DESCRIPTION,
          modifier = Modifier.size(100.dp),
          loading = { Text(LOADING) },
          failed = { Text(FAILED) },
      )
    }
  }

  private fun waitForText(text: String) {
    composeTestRule.waitUntil(TIMEOUT_MS) {
      composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun waitForImage() {
    composeTestRule.waitUntil(TIMEOUT_MS) { decoder.decoded.isNotEmpty() }
    composeTestRule.waitForIdle()
  }

  @Test
  fun showsLoadingWhileTheFileIsLoaded() {
    val never = CompletableDeferred<File>()
    setImage { never.await() }

    composeTestRule.onNodeWithText(LOADING).assertIsDisplayed()
    composeTestRule.onNodeWithText(FAILED).assertDoesNotExist()
    composeTestRule.onNodeWithTag(MediaImageTestTags.IMAGE).assertDoesNotExist()
  }

  @Test
  fun showsFailedWhenLoadingTheFileThrows() {
    setImage { throw IOException("offline and not cached") }

    waitForText(FAILED)
    composeTestRule.onNodeWithText(FAILED).assertIsDisplayed()
    composeTestRule.onNodeWithText(LOADING).assertDoesNotExist()
    composeTestRule.onNodeWithTag(MediaImageTestTags.IMAGE).assertDoesNotExist()
  }

  @Test
  fun showsTheDecodedFileWithItsDescription() {
    setImage { photo }

    waitForImage()
    assertEquals(listOf(photo), decoder.decoded)
    composeTestRule.onNodeWithContentDescription(DESCRIPTION).assertIsDisplayed()
    composeTestRule.onNodeWithText(LOADING).assertDoesNotExist()
    composeTestRule.onNodeWithText(FAILED).assertDoesNotExist()
  }

  @Test
  fun showsLoadingWhileCoilDecodes() {
    decoder.gate = CompletableDeferred()
    setImage { photo }

    composeTestRule.waitUntil(TIMEOUT_MS) { decoder.requested.isNotEmpty() }
    composeTestRule.onNodeWithText(LOADING).assertIsDisplayed()

    decoder.gate!!.complete(Unit)
    waitForImage()
    composeTestRule.onNodeWithText(LOADING).assertDoesNotExist()
  }

  @Test
  fun showsFailedWhenTheFileCannotBeDecoded() {
    decoder.broken += photo
    setImage { photo }

    waitForText(FAILED)
    composeTestRule.onNodeWithText(FAILED).assertIsDisplayed()
    composeTestRule.onNodeWithText(LOADING).assertDoesNotExist()
  }

  @Test
  fun doesNotReloadTheFileOnRecomposition() {
    var calls = 0
    var description by mutableStateOf("first")
    composeTestRule.setContent {
      MediaImage(
          mediaId = "m1",
          loadFile = {
            calls++
            photo
          },
          contentDescription = description,
          modifier = Modifier.size(100.dp),
      )
    }
    waitForImage()

    description = "second"
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithContentDescription("second").assertIsDisplayed()
    assertEquals(1, calls)
  }

  @Test
  fun reloadsWithTheLatestLoaderWhenTheMediaIdChanges() {
    var mediaId by mutableStateOf("m1")
    var calls = 0
    composeTestRule.setContent {
      val file = if (mediaId == "m1") photo else otherPhoto
      MediaImage(
          mediaId = mediaId,
          loadFile = {
            calls++
            file
          },
          contentDescription = DESCRIPTION,
          modifier = Modifier.size(100.dp),
      )
    }
    waitForImage()

    mediaId = "m2"
    composeTestRule.waitForIdle()
    composeTestRule.mainClock.advanceTimeBy(FRAMES_MS)
    composeTestRule.waitUntil(TIMEOUT_MS) { decoder.decoded.size == 2 }

    assertEquals(2, calls)
    assertEquals(listOf(photo, otherPhoto), decoder.decoded)
  }

  @Test
  fun showsLoadingAgainInsteadOfThePreviousMediaWhenTheMediaIdChanges() {
    var mediaId by mutableStateOf("m1")
    val second = CompletableDeferred<File>()
    composeTestRule.setContent {
      MediaImage(
          mediaId = mediaId,
          loadFile = { if (mediaId == "m1") photo else second.await() },
          contentDescription = DESCRIPTION,
          modifier = Modifier.size(100.dp),
          loading = { Text(LOADING) },
      )
    }
    waitForImage()

    mediaId = "m2"
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText(LOADING).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MediaImageTestTags.IMAGE).assertDoesNotExist()
  }

  @Test
  fun clearMediaImageMemoryCacheEmptiesCoilsMemoryCache() {
    val memoryCache = SingletonImageLoader.get(context).memoryCache!!
    val key = MemoryCache.Key("private photo")
    memoryCache[key] = MemoryCache.Value(smallBitmap().asImage())

    clearMediaImageMemoryCache(context)

    assertNull(memoryCache[key])
  }

  /**
   * Replaces Coil's fetching and decoding: answers with a small bitmap, or an error for the files
   * in [broken], after [gate] completes if one is set.
   */
  private class FakeDecoder : Interceptor {
    val requested = mutableListOf<Any>()
    val decoded = mutableListOf<Any>()
    val broken = mutableSetOf<File>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
      val data = chain.request.data
      requested += data
      gate?.await()
      if (data in broken) {
        return ErrorResult(
            image = null,
            request = chain.request,
            throwable = IOException("not an image"),
        )
      }
      decoded += data
      return SuccessResult(
          image = smallBitmap().asImage(),
          request = chain.request,
          dataSource = DataSource.DISK,
      )
    }
  }

  private companion object {
    const val DESCRIPTION = "A photo of the garden"
    const val LOADING = "Loading"
    const val FAILED = "Not available offline"
    const val TIMEOUT_MS = 5_000L

    /**
     * Time to advance, after recomposing, for Coil to start the request of a new model; waitUntil
     * alone doesn't advance the test clock enough.
     */
    const val FRAMES_MS = 1_000L

    fun smallBitmap(): Bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
  }
}
