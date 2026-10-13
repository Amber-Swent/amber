// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

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
    // Empty files are enough: the fake decoder never reads them, it only compares them.
    photo = tempFolder.newFile("photo.jpg")
    otherPhoto = tempFolder.newFile("other.jpg")
    // MediaImage uses Coil's app-wide image loader, so the fake is installed there. setUnsafe
    // replaces it even if Coil already created one in an earlier test of the same process.
    SingletonImageLoader.setUnsafe(ImageLoader.Builder(context).components { add(decoder) }.build())
  }

  @After
  fun tearDown() {
    // The loader is global: without a reset, the next test class would get this fake.
    SingletonImageLoader.reset()
  }

  private fun setImage(
      mediaId: String = "m1",
      description: String? = DESCRIPTION,
      loadFile: suspend () -> File,
  ) {
    composeTestRule.setContent {
      MediaImage(
          mediaId = mediaId,
          loadFile = loadFile,
          contentDescription = description,
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

  /** Waits until Coil has decoded a file and the UI shows it. */
  private fun waitForImage() {
    composeTestRule.waitUntil(TIMEOUT_MS) { decoder.decoded.isNotEmpty() }
    // The decoder answers before Compose draws the result: let that recomposition happen too.
    composeTestRule.waitForIdle()
  }

  @Test
  fun showsLoadingWhileTheFileIsLoaded() {
    // A deferred that is never completed keeps loadFile suspended, like a slow download.
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
  fun showsFailedWhenLoadFileGivesUpWithACancellation() {
    // withTimeout throws a TimeoutCancellationException, which is a CancellationException, but the
    // image itself was not cancelled. Rethrowing it blindly would leave the image loading forever,
    // so it must show the failed slot.
    setImage { withTimeout(1) { awaitCancellation() } }

    waitForText(FAILED)
    composeTestRule.onNodeWithText(FAILED).assertIsDisplayed()
    composeTestRule.onNodeWithText(LOADING).assertDoesNotExist()
  }

  @Test
  fun cancelsTheLoadWhenTheImageLeavesTheScreen() {
    var shown by mutableStateOf(true)
    val started = CompletableDeferred<Unit>()
    var cancelled = false
    composeTestRule.setContent {
      if (shown) {
        MediaImage(
            mediaId = "m1",
            loadFile = {
              started.complete(Unit)
              // Suspends until cancelled; the finally block records that the cancellation came.
              try {
                awaitCancellation()
              } finally {
                cancelled = true
              }
            },
            contentDescription = DESCRIPTION,
        )
      }
    }
    // Remove the image only once its load is running, otherwise there is nothing to cancel.
    composeTestRule.waitUntil(TIMEOUT_MS) { started.isCompleted }

    shown = false
    composeTestRule.waitForIdle()

    assertTrue(cancelled)
    // Leaving the screen is not a failure: nothing to log.
    assertTrue(ShadowLog.getLogsForTag(LOG_TAG).isEmpty())
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
  fun aBlankDescriptionIsReadAsTheFallback() {
    // MediaItem.description defaults to "", which TalkBack would read as nothing.
    setImage(description = " ") { photo }

    waitForImage()
    composeTestRule
        .onNodeWithTag(MediaImageTestTags.IMAGE)
        .assertContentDescriptionEquals(MEDIA_IMAGE_FALLBACK_TALKBACK)
  }

  @Test
  fun aNullDescriptionShowsADecorativeImage() {
    setImage(description = null) { photo }

    waitForImage()
    composeTestRule
        .onNodeWithTag(MediaImageTestTags.IMAGE)
        .assertIsDisplayed()
        .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
  }

  @Test
  fun showsLoadingWhileCoilDecodes() {
    // The gate holds the fake decoder: the file is loaded, but Coil hasn't finished decoding it.
    decoder.gate = CompletableDeferred()
    setImage { photo }

    composeTestRule.waitUntil(TIMEOUT_MS) { decoder.requested.isNotEmpty() }
    composeTestRule.onNodeWithText(LOADING).assertIsDisplayed()
    // TalkBack must not describe a picture that isn't shown yet.
    composeTestRule.onNodeWithContentDescription(DESCRIPTION).assertDoesNotExist()

    decoder.gate!!.complete(Unit)
    waitForImage()
    composeTestRule.onNodeWithText(LOADING).assertDoesNotExist()
    composeTestRule.onNodeWithContentDescription(DESCRIPTION).assertIsDisplayed()
  }

  @Test
  fun keepsTheSameLoadingSlotFromLoadingTheFileToDecodingIt() {
    // Counts how often the loading slot enters the composition: a second entry would restart an
    // animated spinner, or mean a frame without any slot in between.
    var loadingEntries = 0
    val file = CompletableDeferred<File>()
    decoder.gate = CompletableDeferred()
    composeTestRule.setContent {
      MediaImage(
          mediaId = "m1",
          loadFile = { file.await() },
          contentDescription = DESCRIPTION,
          modifier = Modifier.size(100.dp),
          loading = {
            DisposableEffect(Unit) {
              loadingEntries++
              onDispose {}
            }
            Text(LOADING)
          },
      )
    }
    composeTestRule.onNodeWithText(LOADING).assertIsDisplayed()

    // The file is loaded, and Coil is held while decoding it.
    file.complete(photo)
    composeTestRule.waitUntil(TIMEOUT_MS) { decoder.requested.isNotEmpty() }
    composeTestRule.onNodeWithText(LOADING).assertIsDisplayed()

    decoder.gate!!.complete(Unit)
    waitForImage()

    composeTestRule.onNodeWithText(LOADING).assertDoesNotExist()
    assertEquals(1, loadingEntries)
  }

  @Test
  fun showsFailedAndHidesTheImageWhenTheFileCannotBeDecoded() {
    decoder.broken += photo
    setImage { photo }

    waitForText(FAILED)
    composeTestRule.onNodeWithText(FAILED).assertIsDisplayed()
    composeTestRule.onNodeWithText(LOADING).assertDoesNotExist()
    composeTestRule.onNodeWithContentDescription(DESCRIPTION).assertDoesNotExist()
    composeTestRule.onNodeWithTag(MediaImageTestTags.IMAGE).assertDoesNotExist()
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

    // A new parameter recomposes MediaImage (with a new loadFile lambda too) but keeps mediaId.
    description = "second"
    composeTestRule.waitForIdle()

    // The new description proves the recomposition happened; the file was still loaded once.
    composeTestRule.onNodeWithContentDescription("second").assertIsDisplayed()
    assertEquals(1, calls)
  }

  @Test
  fun reloadsWithTheLatestLoaderWhenTheMediaIdChanges() {
    var mediaId by mutableStateOf("m1")
    var calls = 0
    composeTestRule.setContent {
      // Each composition passes a lambda returning the file of the current id, so decoding
      // otherPhoto shows that the new id's loader ran.
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
    // Recompose with "m2" first, then move the test clock on so that Coil starts the new request
    // (see FRAMES_MS). In the other order, the request didn't start.
    composeTestRule.waitForIdle()
    composeTestRule.mainClock.advanceTimeBy(FRAMES_MS)
    composeTestRule.waitUntil(TIMEOUT_MS) { decoder.decoded.size == 2 }

    assertEquals(2, calls)
    assertEquals(listOf(photo, otherPhoto), decoder.decoded)
  }

  @Test
  fun showsLoadingInsteadOfThePreviousMediaFromTheFirstFrameAfterAMediaIdChange() {
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
    // The bug lasted a single frame, and waitForIdle would run many frames and hide it. With the
    // clock paused, the test decides exactly which frames run.
    composeTestRule.mainClock.autoAdvance = false

    mediaId = "m2"
    // A state written outside composition is only seen by Compose once published; without this,
    // the frame below would still compose "m1" and the check would prove nothing.
    Snapshot.sendApplyNotifications()
    composeTestRule.mainClock.advanceTimeByFrame()

    // First frame with "m2": the m1 picture must already be gone.
    composeTestRule.onNodeWithTag(MediaImageTestTags.IMAGE).assertDoesNotExist()
    composeTestRule.onNodeWithText(LOADING).assertIsDisplayed()

    // And it stays so while the m2 file is loading (second is never completed).
    composeTestRule.mainClock.autoAdvance = true
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(MediaImageTestTags.IMAGE).assertDoesNotExist()
    composeTestRule.onNodeWithText(LOADING).assertIsDisplayed()
  }

  @Test
  fun aCancelledLoadThatFailsLateDoesNotShowFailedForTheNewMedia() {
    // Changing mediaId cancels the m1 load. Here the m1 loader turns that cancellation into an
    // IOException, thrown only once the m2 load has started; it must not mark m2 as failed.
    var mediaId by mutableStateOf("m1")
    val firstStarted = CompletableDeferred<Unit>()
    val secondStarted = CompletableDeferred<Unit>()
    var firstFailed = false
    val second = CompletableDeferred<File>()
    composeTestRule.setContent {
      MediaImage(
          mediaId = mediaId,
          // mediaId is read when the loader runs, so one lambda serves as both loaders.
          loadFile = {
            if (mediaId == "m1") {
              firstStarted.complete(Unit)
              // A loader that turns its own cancellation into another exception.
              try {
                awaitCancellation()
              } catch (e: CancellationException) {
                // A cancelled coroutine can still wait, but only in NonCancellable. Waiting for
                // the m2 load to start forces the order in which the bug appeared.
                withContext(NonCancellable) { secondStarted.await() }
                firstFailed = true
                throw IOException("download stopped", e)
              }
            } else {
              secondStarted.complete(Unit)
              second.await()
            }
          },
          contentDescription = DESCRIPTION,
          modifier = Modifier.size(100.dp),
          loading = { Text(LOADING) },
          failed = { Text(FAILED) },
      )
    }
    composeTestRule.waitUntil(TIMEOUT_MS) { firstStarted.isCompleted }

    mediaId = "m2"
    // waitForIdle recomposes with "m2" (waitUntil alone didn't). Then wait until the late
    // IOException was really thrown, and let its result reach the UI.
    composeTestRule.waitForIdle()
    composeTestRule.waitUntil(TIMEOUT_MS) { firstFailed }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText(LOADING).assertIsDisplayed()
    composeTestRule.onNodeWithText(FAILED).assertDoesNotExist()
  }

  /** Shows an image whose failed slot is a [RETRY] button, with the given [retryKey]. */
  private fun setRetryableImage(retryKey: () -> Any? = { null }, loadFile: suspend () -> File) {
    composeTestRule.setContent {
      MediaImage(
          mediaId = "m1",
          loadFile = loadFile,
          contentDescription = DESCRIPTION,
          modifier = Modifier.size(100.dp),
          retryKey = retryKey(),
          loading = { Text(LOADING) },
          failed = { retry -> Text(RETRY, Modifier.clickable(onClick = retry)) },
      )
    }
  }

  @Test
  fun retryingAfterALoadFailureShowsTheImage() {
    var calls = 0
    setRetryableImage {
      calls++
      if (calls == 1) throw IOException("offline and not cached") else photo
    }
    waitForText(RETRY)

    composeTestRule.onNodeWithText(RETRY).performClick()

    waitForImage()
    assertEquals(2, calls)
    composeTestRule.onNodeWithContentDescription(DESCRIPTION).assertIsDisplayed()
    composeTestRule.onNodeWithText(RETRY).assertDoesNotExist()
  }

  @Test
  fun retryingAfterADecodeFailureDecodesTheFileAgain() {
    decoder.broken += photo
    setRetryableImage { photo }
    waitForText(RETRY)

    // The file is fine now, for example it was downloaded again.
    decoder.broken -= photo
    composeTestRule.onNodeWithText(RETRY).performClick()

    waitForImage()
    composeTestRule.onNodeWithContentDescription(DESCRIPTION).assertIsDisplayed()
  }

  @Test
  fun aRetryCalledTwiceStartsASingleNewLoad() {
    var calls = 0
    lateinit var retry: () -> Unit
    composeTestRule.setContent {
      MediaImage(
          mediaId = "m1",
          loadFile = {
            calls++
            if (calls == 1) throw IOException("offline") else awaitCancellation()
          },
          contentDescription = DESCRIPTION,
          loading = { Text(LOADING) },
          failed = {
            retry = it
            Text(FAILED)
          },
      )
    }
    waitForText(FAILED)

    // A double tap: the second call comes while the retried load is still running.
    composeTestRule.runOnIdle {
      retry()
      retry()
    }
    composeTestRule.waitForIdle()

    assertEquals(2, calls)
    composeTestRule.onNodeWithText(LOADING).assertIsDisplayed()
  }

  @Test
  fun aNewRetryKeyRetriesAFailedImage() {
    var retryKey by mutableStateOf(0)
    var calls = 0
    setRetryableImage(retryKey = { retryKey }) {
      calls++
      if (calls == 1) throw IOException("offline and not cached") else photo
    }
    waitForText(RETRY)

    // What a ViewModel does when the connection comes back. waitForIdle recomposes with the new
    // key (waitUntil alone doesn't).
    retryKey++
    composeTestRule.waitForIdle()

    waitForImage()
    assertEquals(2, calls)
    composeTestRule.onNodeWithContentDescription(DESCRIPTION).assertIsDisplayed()
  }

  @Test
  fun aNewRetryKeyDoesNotReloadAShownImage() {
    var retryKey by mutableStateOf(0)
    var calls = 0
    setRetryableImage(retryKey = { retryKey }) {
      calls++
      photo
    }
    waitForImage()

    retryKey++
    composeTestRule.waitForIdle()

    assertEquals(1, calls)
    assertEquals(listOf(photo), decoder.decoded)
    composeTestRule.onNodeWithContentDescription(DESCRIPTION).assertIsDisplayed()
  }

  @Test
  fun aNewRetryKeyDoesNotStartASecondLoadWhileLoading() {
    var retryKey by mutableStateOf(0)
    var calls = 0
    setRetryableImage(retryKey = { retryKey }) {
      calls++
      awaitCancellation()
    }
    composeTestRule.waitUntil(TIMEOUT_MS) { calls == 1 }

    retryKey++
    composeTestRule.waitForIdle()

    assertEquals(1, calls)
    composeTestRule.onNodeWithText(LOADING).assertIsDisplayed()
  }

  /** Waits for the first log of MediaImage and returns it. */
  private fun waitForLog(): ShadowLog.LogItem {
    composeTestRule.waitUntil(TIMEOUT_MS) { ShadowLog.getLogsForTag(LOG_TAG).isNotEmpty() }
    return ShadowLog.getLogsForTag(LOG_TAG).single()
  }

  @Test
  fun aLoadFailureIsLoggedAsAWarningWithItsCause() {
    val offline = IOException("offline and not cached")
    setImage { throw offline }

    val log = waitForLog()
    assertEquals(Log.WARN, log.type)
    assertSame(offline, log.throwable)
    assertTrue(log.msg.contains("m1"))
  }

  @Test
  fun aMediaNotInTheCircleIsLoggedAsAnError() {
    // What getFile throws when the ViewModel passed the wrong circle: a bug, not being offline.
    val wrongCircle = IllegalArgumentException("media m1 is not in circle c1")
    setImage { throw wrongCircle }

    val log = waitForLog()
    assertEquals(Log.ERROR, log.type)
    assertSame(wrongCircle, log.throwable)
  }

  @Test
  fun aDecodeFailureIsLoggedWithCoilsCause() {
    decoder.broken += photo
    setImage { photo }

    val log = waitForLog()
    assertEquals(Log.WARN, log.type)
    assertEquals("not an image", log.throwable?.message)
    assertTrue(log.msg.contains("m1"))
  }

  @Test
  fun clearMediaImageMemoryCacheEmptiesCoilsMemoryCache() {
    // An entry put directly in the cache stands in for a decoded private photo.
    val memoryCache = SingletonImageLoader.get(context).memoryCache!!
    val key = MemoryCache.Key("private photo")
    memoryCache[key] = MemoryCache.Value(smallBitmap().asImage())

    clearMediaImageMemoryCache(context)

    assertNull(memoryCache[key])
  }

  /**
   * Replaces Coil's fetching and decoding: answers with a small bitmap, or an error for the files
   * in [broken], after [gate] completes if one is set.
   *
   * Coil runs its interceptors before fetching and decoding, and an interceptor that returns a
   * result without calling `chain.proceed` skips them. So files are never read and Robolectric
   * needs no real image decoding.
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
    const val RETRY = "Try again"
    const val LOG_TAG = "MediaImage"
    const val TIMEOUT_MS = 5_000L

    /**
     * Time to advance, after recomposing, for Coil to start the request of a new model; waitUntil
     * alone doesn't advance the test clock enough.
     */
    const val FRAMES_MS = 1_000L

    fun smallBitmap(): Bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
  }
}
