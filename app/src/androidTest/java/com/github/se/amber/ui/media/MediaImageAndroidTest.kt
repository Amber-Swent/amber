// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.github.se.amber.data.media.image.MediaPictureFetcher
import com.github.se.amber.data.media.image.MediaPictureKeyer
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.CircleMedia
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaPicture
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.OutboxEntry
import java.io.File
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks [MediaImage] and [MediaPictureFetcher] with Coil's real decoding of files written on the
 * device.
 */
@OptIn(DelicateCoilApi::class)
@RunWith(AndroidJUnit4::class)
class MediaImageInstrumentedTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()
  private lateinit var dir: File

  /** The file [repository] gives for every picture. */
  private lateinit var file: File
  /** Whether getting a file fails as it does offline, when it isn't on the phone. */
  @Volatile private var offline = false
  private val repository = FileRepository {
    if (offline) throw UnknownHostException("firebasestorage.googleapis.com") else file
  }

  private val circle = CareCircle(id = "c1", storageBucket = "bucket-c1")
  private val picture =
      MediaItem.Picture(
          id = "m1",
          storagePath = "careCircles/c1/media/m1.jpg",
          description = DESCRIPTION,
      )

  @Before
  fun setUp() {
    dir = File(context.cacheDir, "media-image-test").apply { mkdirs() }
    // The app's loader would get files from the real repository: use this test's instead.
    SingletonImageLoader.setUnsafe(
        ImageLoader.Builder(context)
            .components {
              add(MediaPictureKeyer())
              add(MediaPictureFetcher.Factory { repository })
            }
            .build()
    )
  }

  @After
  fun tearDown() {
    dir.deleteRecursively()
    SingletonImageLoader.reset()
  }

  /** Stands in for `ConnectivityMonitor.backOnline`: the test emits when the connection is back. */
  private val backOnline = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  /**
   * Shows [picture] with [backOnline] as its retry signal, as `MainActivity` provides it in the
   * app, and a [RETRY] text calling its retry action when tapped.
   */
  private fun show() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalMediaRetrySignal provides backOnline) {
        MediaImage(
            circle = circle,
            picture = picture,
            modifier = Modifier.size(100.dp),
            loading = { Text(LOADING) },
            retryButton = { retry -> Text(RETRY, Modifier.clickable(onClick = retry)) },
        )
      }
    }
  }

  private fun writePng(name: String): File {
    val png = File(dir, name)
    val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
    png.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return png
  }

  /** Waits until [condition] holds, failing the test after [TIMEOUT]. */
  private fun waitUntil(condition: () -> Boolean) =
      composeTestRule.waitUntil(TIMEOUT.inWholeMilliseconds, condition)

  // Waits for the final state itself: the absence of the loading slot alone doesn't prove the
  // picture is shown.
  private fun waitUntilShown() {
    waitUntil {
      composeTestRule
          .onAllNodesWithContentDescription(DESCRIPTION)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
  }

  @Test
  fun decodesAndShowsARealPng() {
    file = writePng("photo.png")
    show()

    waitUntilShown()
    composeTestRule.onNodeWithContentDescription(DESCRIPTION).assertIsDisplayed()
    composeTestRule.onNodeWithText(LOADING).assertDoesNotExist()
    composeTestRule.onNodeWithText(RETRY).assertDoesNotExist()
  }

  @Test
  fun showsTheRetryButtonForAFileThatIsNotAnImage() {
    file = File(dir, "photo.jpg").apply { writeText("not an image") }
    show()

    waitUntil { composeTestRule.onAllNodesWithText(RETRY).fetchSemanticsNodes().isNotEmpty() }
    composeTestRule.onNodeWithText(RETRY).assertIsDisplayed()
    composeTestRule.onNodeWithText(LOADING).assertDoesNotExist()
  }

  @Test
  fun aPictureThatFailedOfflineIsShownWhenTheConnectionComesBack() {
    file = writePng("photo.png")
    offline = true
    show()
    waitUntil { composeTestRule.onAllNodesWithText(RETRY).fetchSemanticsNodes().isNotEmpty() }

    offline = false
    // tryEmit drops the value if nobody listens yet: wait for the image's collector first
    waitUntil { backOnline.subscriptionCount.value > 0 }
    backOnline.tryEmit(Unit)

    waitUntilShown()
    assertEquals(2, repository.calls.get())
  }

  @Test
  fun theDefaultRetryButtonShowsAFileFixedSinceTheFailure() {
    file = File(dir, "photo.jpg").apply { writeText("not an image") }
    // no retryButton: the one every screen gets
    composeTestRule.setContent {
      MediaImage(circle = circle, picture = picture, modifier = Modifier.size(100.dp))
    }
    waitUntil {
      composeTestRule
          .onAllNodesWithTag(MediaImageTestTags.RETRY_BUTTON)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

    // e.g. downloaded again in full
    file = writePng("photo.png")
    composeTestRule.onNodeWithTag(MediaImageTestTags.RETRY_BUTTON).performClick()

    waitUntilShown()
    composeTestRule.onNodeWithTag(MediaImageTestTags.RETRY_BUTTON).assertDoesNotExist()
  }

  @Test
  fun aFileDeletedAfterTheFetchIsStillDecoded() = runBlocking {
    // What a send (moving the file) or an eviction (deleting it) does while Coil decodes.
    file = writePng("photo.png")
    val result =
        MediaPictureFetcher.Factory { repository }
            .create(MediaPicture(circle, picture), Options(context), ImageLoader(context))
            .fetch() as SourceFetchResult

    assertTrue(file.delete())

    val bitmap = result.source.use { BitmapFactory.decodeStream(it.source().inputStream()) }
    assertNotNull(bitmap)
    assertEquals(Color.RED, bitmap.getPixel(4, 4))
  }

  @Test
  fun aPictureInCoilsMemoryCacheIsVisibleInTheFirstFrameWithoutGettingItsFile() {
    file = writePng("photo.png")
    var shown by mutableStateOf(true)
    composeTestRule.setContent {
      if (shown) {
        MediaImage(
            circle = circle,
            picture = picture,
            modifier = Modifier.size(100.dp).testTag(CONTAINER),
            // A solid slot over the whole image, so a pixel tells which one is drawn on top.
            loading = { Box(Modifier.fillMaxSize().background(ComposeColor.Blue)) },
        )
      }
    }
    // First display: Coil decodes the file and keeps the picture in its memory cache.
    waitUntilShown()

    // Leave and come back, like scrolling an item out of a list and back. The clock is paused so
    // that the test runs a single frame.
    shown = false
    composeTestRule.waitForIdle()
    composeTestRule.mainClock.autoAdvance = false
    shown = true
    // A state written outside composition is only seen by Compose once published.
    Snapshot.sendApplyNotifications()
    composeTestRule.mainClock.advanceTimeByFrame()

    // The first frame back: the cached picture must be drawn over the loading slot (blue), and
    // the repository wasn't asked again.
    val bitmap = composeTestRule.onNodeWithTag(CONTAINER).captureToImage().asAndroidBitmap()
    assertEquals(Color.RED, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
    assertEquals(1, repository.calls.get())
  }

  /** Gives [file] for every picture and counts the calls; the other methods aren't used. */
  private class FileRepository(private val file: () -> File) : MediaRepository {
    // Called from Coil's threads, read by the test thread.
    val calls = AtomicInteger()

    override suspend fun getFile(circle: CareCircle, item: MediaItem): File {
      calls.incrementAndGet()
      return file()
    }

    override fun newMediaId(circleId: String): String = unused()

    override fun observeApprovedMedia(circleId: String): Flow<List<MediaItem>> = unused()

    override fun observeMediaShowing(circleId: String, shownId: String): Flow<List<MediaItem>> =
        unused()

    override fun observeCircleMedia(circleId: String): Flow<List<CircleMedia>> = unused()

    override suspend fun getMedia(circleId: String, mediaId: String): MediaItem? = unused()

    override suspend fun addMedia(circle: CareCircle, item: MediaItem, file: File?) = unused()

    override fun observeOutbox(circleId: String): Flow<List<OutboxEntry>> = unused()

    override suspend fun retrySend(circleId: String, mediaId: String) = unused()

    override suspend fun discardUnsent(circleId: String, mediaId: String) = unused()

    override suspend fun updateMedia(circleId: String, item: MediaItem) = unused()

    override suspend fun deleteMedia(circle: CareCircle, item: MediaItem) = unused()

    override suspend fun clearLocalMedia(circleId: String) = unused()

    override suspend fun clearLocalMedia() = unused()

    private fun unused(): Nothing = error("Not used by media pictures")
  }

  private companion object {
    val TIMEOUT = 10_000.milliseconds
    const val DESCRIPTION = "A red square"
    const val LOADING = "Loading"
    const val RETRY = "Try again"
    const val CONTAINER = "media_image_container"
  }
}
