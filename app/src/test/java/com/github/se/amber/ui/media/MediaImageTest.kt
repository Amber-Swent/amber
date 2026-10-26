// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.size.Size
import com.github.se.amber.data.connectivity.FakeConnectivityMonitor
import com.github.se.amber.data.media.image.FakePictureRepository
import com.github.se.amber.data.media.image.MEDIA_PICTURE_FETCHER_TAG
import com.github.se.amber.data.media.image.MediaPictureFetcher
import com.github.se.amber.data.media.image.MediaPictureKeyer
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import java.io.File
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

/**
 * Checks [MediaImage] with the app's real keyer and fetcher, in front of a fake repository and a
 * fake decoder.
 */
@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
class MediaImageTest {
  @get:Rule val composeTestRule = createComposeRule()
  @get:Rule val tempFolder = TemporaryFolder()

  private val context: Context = ApplicationProvider.getApplicationContext()

  private val repository = FakePictureRepository()

  /** Stands in for Coil's decoding, so the tests need neither real images nor native graphics. */
  private val decoder = FakeDecoder()

  /** Every request MediaImage makes, recorded before Coil handles it. */
  private val requests: MutableList<ImageRequest> = CopyOnWriteArrayList()
  private val requestRecorder = Interceptor { chain ->
    requests += chain.request
    chain.proceed()
  }

  private val circle = CareCircle(id = "c1", name = "Family", storageBucket = "bucket-c1")
  private val m1 = picture("m1")
  private val m2 = picture("m2")

  private lateinit var photo: File
  private lateinit var otherPhoto: File

  @Before
  fun setUp() {
    // The fake decoder tells files apart by their content.
    photo = tempFolder.newFile("photo.jpg").apply { writeText(PHOTO) }
    otherPhoto = tempFolder.newFile("other.jpg").apply { writeText(OTHER_PHOTO) }
    repository.onGetFile = { if (it.id == "m1") photo else otherPhoto }
    installImageLoader()
  }

  /**
   * MediaImage uses Coil's app-wide image loader, so the test one is installed there. setUnsafe
   * replaces it even if Coil already created one earlier in the same process. Without
   * [memoryCache], every request gets the file again, so a test can see each new request.
   */
  private fun installImageLoader(memoryCache: Boolean = true) {
    SingletonImageLoader.setUnsafe(
        ImageLoader.Builder(context)
            .apply { if (!memoryCache) memoryCache(null) }
            .components {
              add(requestRecorder)
              add(MediaPictureKeyer())
              add(MediaPictureFetcher.Factory { repository })
              add(decoder)
            }
            .build()
    )
  }

  @After
  fun tearDown() {
    // The loader is global: without a reset, the next test class would get this one.
    SingletonImageLoader.reset()
  }

  private fun picture(id: String, description: String = DESCRIPTION) =
      MediaItem.Picture(
          id = id,
          storagePath = "careCircles/c1/media/$id.jpg",
          description = description,
      )

  /** The phone's connection as the images of [setImages] see it. Starts offline. */
  private val connection = FakeConnectivityMonitor(online = false)

  /**
   * Shows [pictures] one under the other, with [connection]'s comebacks as their retry signal, as
   * `MainActivity` provides it in the app. Each shows [LOADING] while loading and, if it fails, a
   * [RETRY] text calling its retry action when tapped.
   */
  private fun setImages(vararg pictures: MediaItem.Picture) {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalMediaRetrySignal provides connection.backOnline) {
        Column {
          pictures.forEach {
            MediaImage(
                circle = circle,
                picture = it,
                modifier = Modifier.size(100.dp),
                loading = { Text(LOADING) },
                retryButton = { retry -> Text(RETRY, Modifier.clickable(onClick = retry)) },
            )
          }
        }
      }
    }
  }

  /** Brings [connection] back online, after dropping it if it was online. */
  private fun comeBackOnline() {
    connection.isOnline.value = false
    // A StateFlow keeps only its latest value: set to true right away, the images would never see
    // it false, and nothing would come back.
    composeTestRule.waitForIdle()
    connection.isOnline.value = true
    composeTestRule.waitForIdle()
  }

  /** Waits until [condition] holds, failing the test after [TIMEOUT]. */
  private fun waitUntil(condition: () -> Boolean) =
      composeTestRule.waitUntil(TIMEOUT.inWholeMilliseconds, condition)

  private fun waitForText(text: String = RETRY) {
    waitUntil { composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
  }

  /** Waits until Coil has decoded [count] files and the UI shows the last one. */
  private fun waitForImage(count: Int = 1) {
    waitUntil { decoder.decoded.size >= count }
    // The decoder answers before Coil hands the result to Compose: wait for the picture too.
    waitUntil {
      composeTestRule
          .onAllNodesWithTag(MediaImageTestTags.IMAGE, useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
  }

  @Test
  fun showsLoadingWhileTheFileIsGot() {
    // A deferred that is never completed keeps getFile suspended, like a slow download.
    val never = CompletableDeferred<File>()
    repository.onGetFile = { never.await() }
    setImages(m1)

    waitUntil { repository.requests.isNotEmpty() }
    composeTestRule.onNodeWithText(LOADING).assertIsDisplayed()
    composeTestRule.onNodeWithText(RETRY).assertDoesNotExist()
    composeTestRule.onNodeWithTag(MediaImageTestTags.IMAGE).assertDoesNotExist()
  }

  @Test
  fun showsTheRetryButtonWhenGetFileThrows() {
    repository.onGetFile = { throw IOException("offline and not cached") }
    setImages(m1)

    waitForText()
    composeTestRule.onNodeWithText(RETRY).assertIsDisplayed()
    composeTestRule.onNodeWithText(LOADING).assertDoesNotExist()
    composeTestRule.onNodeWithTag(MediaImageTestTags.IMAGE).assertDoesNotExist()
  }

  @Test
  fun showsTheRetryButtonWhenGetFileGivesUpWithACancellation() {
    // withTimeout throws a TimeoutCancellationException, which is a CancellationException, but the
    // image itself was not cancelled: it must show the retry button, not stay loading forever.
    repository.onGetFile = { withTimeout(1.milliseconds) { awaitCancellation() } }
    setImages(m1)

    waitForText()
    composeTestRule.onNodeWithText(RETRY).assertIsDisplayed()
    composeTestRule.onNodeWithText(LOADING).assertDoesNotExist()
  }

  @Test
  fun cancelsTheLoadWhenTheImageLeavesTheScreen() {
    var shown by mutableStateOf(true)
    val started = CompletableDeferred<Unit>()
    // Written on Coil's threads, read by the test thread.
    val cancelled = AtomicBoolean(false)
    repository.onGetFile = {
      started.complete(Unit)
      // Suspends until cancelled; the finally block records that the cancellation came.
      try {
        awaitCancellation()
      } finally {
        cancelled.set(true)
      }
    }
    composeTestRule.setContent { if (shown) MediaImage(circle = circle, picture = m1) }
    // Remove the image only once its load is running, otherwise there is nothing to cancel.
    waitUntil { started.isCompleted }

    shown = false
    // waitForIdle recomposes without the image (waitUntil alone doesn't).
    composeTestRule.waitForIdle()
    waitUntil { cancelled.get() }
    // That it isn't logged as a failure is checked by MediaPictureFetcherTest, without threads.
  }

  @Test
  fun showsTheDecodedFileWithThePicturesDescription() {
    setImages(m1)

    waitForImage()
    assertEquals(listOf(PHOTO), decoder.decoded)
    composeTestRule.onNodeWithContentDescription(DESCRIPTION).assertIsDisplayed()
    composeTestRule.onNodeWithText(LOADING).assertDoesNotExist()
    composeTestRule.onNodeWithText(RETRY).assertDoesNotExist()
  }

  @Test
  fun aBlankDescriptionIsReadAsTheFallback() {
    // MediaItem.description defaults to "", which TalkBack would read as nothing.
    setImages(picture("m1", description = " "))

    waitForImage()
    composeTestRule
        .onNodeWithTag(MediaImageTestTags.IMAGE)
        .assertContentDescriptionEquals(MEDIA_IMAGE_FALLBACK_TALKBACK)
  }

  @Test
  fun aNullDescriptionShowsADecorativeImage() {
    composeTestRule.setContent {
      MediaImage(
          circle = circle,
          picture = m1,
          contentDescription = null,
          modifier = Modifier.size(100.dp),
      )
    }

    waitForImage()
    composeTestRule
        .onNodeWithTag(MediaImageTestTags.IMAGE)
        .assertIsDisplayed()
        .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
  }

  @Test
  fun showsLoadingWhileCoilDecodes() {
    // The gate holds the fake decoder: the file is got, but Coil hasn't finished decoding it.
    decoder.gate = CompletableDeferred()
    setImages(m1)

    waitUntil { decoder.requested.isNotEmpty() }
    composeTestRule.onNodeWithText(LOADING).assertIsDisplayed()
    // TalkBack must not describe a picture that isn't shown yet.
    composeTestRule.onNodeWithContentDescription(DESCRIPTION).assertDoesNotExist()

    decoder.gate!!.complete(Unit)
    waitForImage()
    composeTestRule.onNodeWithText(LOADING).assertDoesNotExist()
    composeTestRule.onNodeWithContentDescription(DESCRIPTION).assertIsDisplayed()
  }

  @Test
  fun keepsTheSameLoadingSlotFromGettingTheFileToDecodingIt() {
    // Counts how often the loading slot enters the composition: a second entry (e.g. if Coil's
    // Empty state, before it starts, and its Loading state had their own call sites) would restart
    // an animated spinner, or mean a frame without any slot in between.
    var loadingEntries = 0
    val file = CompletableDeferred<File>()
    repository.onGetFile = { file.await() }
    decoder.gate = CompletableDeferred()
    composeTestRule.setContent {
      MediaImage(
          circle = circle,
          picture = m1,
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

    // The file is got, and Coil is held while decoding it.
    file.complete(photo)
    waitUntil { decoder.requested.isNotEmpty() }
    composeTestRule.onNodeWithText(LOADING).assertIsDisplayed()

    decoder.gate!!.complete(Unit)
    waitForImage()

    composeTestRule.onNodeWithText(LOADING).assertDoesNotExist()
    assertEquals(1, loadingEntries)
  }

  @Test
  fun showsTheRetryButtonAndHidesTheImageWhenTheFileCannotBeDecoded() {
    decoder.broken += PHOTO
    setImages(m1)

    waitForText()
    composeTestRule.onNodeWithText(RETRY).assertIsDisplayed()
    composeTestRule.onNodeWithText(LOADING).assertDoesNotExist()
    composeTestRule.onNodeWithContentDescription(DESCRIPTION).assertDoesNotExist()
    composeTestRule.onNodeWithTag(MediaImageTestTags.IMAGE).assertDoesNotExist()
  }

  @Test
  fun doesNotGetTheFileAgainWhenTheCircleOrTheDescriptionChanges() {
    // Without memory, a new request would get the file again instead of finding it in memory.
    installImageLoader(memoryCache = false)
    var shownCircle by mutableStateOf(circle)
    var shownPicture by mutableStateOf(m1)
    composeTestRule.setContent {
      MediaImage(circle = shownCircle, picture = shownPicture, modifier = Modifier.size(100.dp))
    }
    waitForImage()

    // What a live update of the circle or of the media's metadata gives the screen.
    shownCircle = circle.copy(name = "Renamed family")
    shownPicture = m1.copy(description = "second")
    composeTestRule.waitForIdle()

    // The new description proves the recomposition happened; the file was still got once.
    composeTestRule.onNodeWithContentDescription("second").assertIsDisplayed()
    assertEquals(listOf("m1"), repository.requests)
    assertEquals(listOf(PHOTO), decoder.decoded)
  }

  @Test
  fun loadsAgainWhenTheCircleGetsItsBucket() {
    // What getFile throws while the circle is still being set up and the file must be downloaded.
    repository.onGetFile = {
      if (repository.requests.size == 1) throw IllegalArgumentException("no bucket") else photo
    }
    var shownCircle by mutableStateOf(circle.copy(storageBucket = ""))
    composeTestRule.setContent {
      MediaImage(
          circle = shownCircle,
          picture = m1,
          modifier = Modifier.size(100.dp),
          retryButton = { Text(RETRY) },
      )
    }
    waitForText()

    shownCircle = circle // its bucket is assigned
    composeTestRule.waitForIdle()

    waitForImage()
    assertEquals(listOf("m1", "m1"), repository.requests)
  }

  @Test
  fun getsTheFileAgainWhenItsPathChanges() {
    installImageLoader(memoryCache = false)
    var shownPicture by mutableStateOf(m1)
    composeTestRule.setContent {
      MediaImage(circle = circle, picture = shownPicture, modifier = Modifier.size(100.dp))
    }
    waitForImage()

    shownPicture = m1.copy(storagePath = "careCircles/c1/media/m1-v2.jpg")
    composeTestRule.waitForIdle()

    waitUntil { repository.requests.size == 2 }
    waitForImage(count = 2)
  }

  @Test
  fun aBiggerRequestDrawsThePictureInMemoryMeanwhile() {
    setImages(m1)
    waitForImage()

    // Coil draws the entry under this key while it decodes the picture at a bigger size.
    assertEquals(MemoryCache.Key("media/c1/m1"), requests.single().placeholderMemoryCacheKey)
  }

  @Test
  fun getsTheFileOfANewPicture() {
    var shownPicture by mutableStateOf(m1)
    composeTestRule.setContent {
      MediaImage(circle = circle, picture = shownPicture, modifier = Modifier.size(100.dp))
    }
    waitForImage()

    shownPicture = m2
    // waitForIdle recomposes with m2 (waitUntil alone doesn't), which starts its request.
    composeTestRule.waitForIdle()
    waitForImage(count = 2)

    assertEquals(listOf("m1", "m2"), repository.requests)
    assertEquals(listOf(PHOTO, OTHER_PHOTO), decoder.decoded)
  }

  @Test
  fun showsLoadingInsteadOfThePreviousPictureFromTheFirstFrameAfterAChange() {
    var shownPicture by mutableStateOf(m1)
    val second = CompletableDeferred<File>()
    repository.onGetFile = { if (it.id == "m1") photo else second.await() }
    composeTestRule.setContent {
      MediaImage(
          circle = circle,
          picture = shownPicture,
          modifier = Modifier.size(100.dp),
          loading = { Text(LOADING) },
      )
    }
    waitForImage()
    // The bug would last a single frame, and waitForIdle would run many frames and hide it. With
    // the clock paused, the test decides exactly which frames run.
    composeTestRule.mainClock.autoAdvance = false

    shownPicture = m2
    // A state written outside composition is only seen by Compose once published; without this,
    // the frame below would still compose m1 and the check would prove nothing.
    Snapshot.sendApplyNotifications()
    composeTestRule.mainClock.advanceTimeByFrame()

    // First frame with m2: the m1 picture must already be gone.
    composeTestRule.onNodeWithTag(MediaImageTestTags.IMAGE).assertDoesNotExist()
    composeTestRule.onNodeWithText(LOADING).assertIsDisplayed()

    // And it stays so while the m2 file is got (second is never completed).
    composeTestRule.mainClock.autoAdvance = true
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(MediaImageTestTags.IMAGE).assertDoesNotExist()
    composeTestRule.onNodeWithText(LOADING).assertIsDisplayed()
  }

  @Test
  fun aCancelledLoadThatFailsLateDoesNotShowTheRetryButtonForTheNewPicture() {
    // Changing the picture cancels the m1 load. Here getFile turns that cancellation into an
    // IOException, thrown only once the m2 load has started; it must not show m2 as failed.
    var shownPicture by mutableStateOf(m1)
    val firstStarted = CompletableDeferred<Unit>()
    val secondStarted = CompletableDeferred<Unit>()
    val firstFailed = AtomicBoolean(false)
    val second = CompletableDeferred<File>()
    repository.onGetFile = {
      if (it.id == "m1") {
        firstStarted.complete(Unit)
        // A getFile that turns its own cancellation into another exception.
        try {
          awaitCancellation()
        } catch (e: CancellationException) {
          // A cancelled coroutine can still wait, but only in NonCancellable. Waiting for the m2
          // load to start forces the order in which the bug would appear.
          withContext(NonCancellable) { secondStarted.await() }
          firstFailed.set(true)
          throw IOException("download stopped", e)
        }
      } else {
        secondStarted.complete(Unit)
        second.await()
      }
    }
    composeTestRule.setContent {
      MediaImage(
          circle = circle,
          picture = shownPicture,
          modifier = Modifier.size(100.dp),
          loading = { Text(LOADING) },
          retryButton = { Text(RETRY) },
      )
    }
    waitUntil { firstStarted.isCompleted }

    shownPicture = m2
    // waitForIdle recomposes with m2 (waitUntil alone didn't). Then wait until the late
    // IOException was really thrown, and let its result reach the UI.
    composeTestRule.waitForIdle()
    waitUntil { firstFailed.get() }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText(LOADING).assertIsDisplayed()
    composeTestRule.onNodeWithText(RETRY).assertDoesNotExist()
  }

  @Test
  fun aPictureInMemoryIsShownAgainWithoutGettingItsFile() {
    var shown by mutableStateOf(true)
    composeTestRule.setContent {
      if (shown) MediaImage(circle = circle, picture = m1, modifier = Modifier.size(100.dp))
    }
    waitForImage()

    // Leave and come back, like scrolling an item out of a list and back.
    shown = false
    composeTestRule.waitForIdle()
    shown = true
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithContentDescription(DESCRIPTION).assertIsDisplayed()
    assertEquals(listOf("m1"), repository.requests)
    assertEquals(listOf(PHOTO), decoder.decoded)
  }

  @Test
  fun retryingAfterALoadFailureShowsTheImage() {
    repository.onGetFile = {
      if (repository.requests.size == 1) throw IOException("download failed") else photo
    }
    setImages(m1)
    waitForText()

    composeTestRule.onNodeWithText(RETRY).performClick()

    waitForImage()
    assertEquals(listOf("m1", "m1"), repository.requests)
    composeTestRule.onNodeWithContentDescription(DESCRIPTION).assertIsDisplayed()
    composeTestRule.onNodeWithText(RETRY).assertDoesNotExist()
  }

  @Test
  fun retryingAfterADecodeFailureDecodesTheFileAgain() {
    decoder.broken += PHOTO
    setImages(m1)
    waitForText()

    // The file is fine now, for example it was downloaded again.
    decoder.broken -= PHOTO
    composeTestRule.onNodeWithText(RETRY).performClick()

    waitForImage()
    composeTestRule.onNodeWithContentDescription(DESCRIPTION).assertIsDisplayed()
  }

  @Test
  fun theDefaultRetryButtonIsReadByTalkBackAndLoadsThePictureAgain() {
    repository.onGetFile = {
      if (repository.requests.size == 1) throw IOException("download failed") else photo
    }
    // no retryButton: the one every screen gets
    composeTestRule.setContent {
      MediaImage(circle = circle, picture = m1, modifier = Modifier.size(100.dp))
    }
    waitUntil {
      composeTestRule
          .onAllNodesWithTag(MediaImageTestTags.RETRY_BUTTON)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

    composeTestRule
        .onNodeWithContentDescription(MEDIA_IMAGE_RETRY_TALKBACK)
        .assertIsDisplayed()
        .performClick()

    waitForImage()
    assertEquals(listOf("m1", "m1"), repository.requests)
    composeTestRule.onNodeWithTag(MediaImageTestTags.RETRY_BUTTON).assertDoesNotExist()
  }

  /**
   * Shows m1 with the same retry signal and [LOADING] slot as [setImages], and a retry slot that
   * gives its `retry` to [onRetry] each time it is shown, so the test can call it when it wants.
   */
  private fun setImageKeepingRetry(onRetry: (() -> Unit) -> Unit) {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalMediaRetrySignal provides connection.backOnline) {
        MediaImage(
            circle = circle,
            picture = m1,
            loading = { Text(LOADING) },
            retryButton = {
              onRetry(it)
              Text(RETRY)
            },
        )
      }
    }
  }

  @Test
  fun aRetryCalledTwiceStartsASingleNewLoad() {
    val retriedLoadRunning = CompletableDeferred<Unit>()
    repository.onGetFile = {
      if (repository.requests.size == 1) throw IOException("download failed")
      retriedLoadRunning.complete(Unit)
      awaitCancellation()
    }
    lateinit var retry: () -> Unit
    setImageKeepingRetry { retry = it }
    waitForText()

    // A double tap, then a third call once the new load is running.
    composeTestRule.runOnIdle {
      retry()
      retry()
    }
    waitUntil { retriedLoadRunning.isCompleted }
    composeTestRule.runOnIdle { retry() }
    composeTestRule.waitForIdle()

    // A restart would have cancelled the running load and asked for the file again.
    assertEquals(listOf("m1", "m1"), repository.requests)
    composeTestRule.onNodeWithText(LOADING).assertIsDisplayed()
  }

  @Test
  fun aRetryAfterASecondFailureLoadsAgain() {
    repository.onGetFile = {
      if (repository.requests.size <= 2) throw IOException("download failed") else photo
    }
    lateinit var retry: () -> Unit
    setImageKeepingRetry { retry = it }

    for (attempt in 2..3) {
      waitForText()
      composeTestRule.runOnIdle { retry() }
      waitUntil { repository.requests.size == attempt }
    }

    waitForImage()
    assertEquals(listOf("m1", "m1", "m1"), repository.requests)
  }

  @Test
  fun aPictureThatFailedOfflineLoadsAgainWhenTheConnectionComesBack() {
    repository.onGetFile = {
      if (repository.requests.size == 1) throw UnknownHostException("offline") else photo
    }
    setImages(m1)
    waitForText()

    comeBackOnline()

    waitForImage()
    assertEquals(listOf("m1", "m1"), repository.requests)
    composeTestRule.onNodeWithText(RETRY).assertDoesNotExist()
  }

  @Test
  fun aPictureFailingOfflineAgainLoadsAgainAtTheNextComeback() {
    // The connection comes back, but drops again before the file is downloaded.
    repository.onGetFile = {
      if (repository.requests.size <= 2) throw UnknownHostException("offline") else photo
    }
    setImages(m1)

    for (attempt in 2..3) {
      waitForText()
      comeBackOnline()
      waitUntil { repository.requests.size == attempt }
    }

    waitForImage()
    assertEquals(listOf("m1", "m1", "m1"), repository.requests)
  }

  @Test
  fun onlyThePicturesThatFailedOfflineLoadAgainWhenTheConnectionComesBack() {
    val shown = picture("shown")
    val offline = picture("offline")
    val diskFull = picture("diskFull")
    val undecodable = picture("undecodable")
    val broken = tempFolder.newFile("broken.jpg").apply { writeText(BROKEN) }
    decoder.broken += BROKEN
    repository.onGetFile = {
      when (it.id) {
        "shown" -> photo
        "offline" ->
            if (repository.requests.count { id -> id == "offline" } == 1) {
              throw UnknownHostException("offline")
            } else {
              otherPhoto
            }
        "diskFull" -> throw IOException("no space left on device")
        else -> broken
      }
    }
    setImages(shown, offline, diskFull, undecodable)
    waitUntil { composeTestRule.onAllNodesWithText(RETRY).fetchSemanticsNodes().size == 3 }

    comeBackOnline()

    // The offline one loading again proves the signal reached the images.
    waitUntil { OTHER_PHOTO in decoder.decoded }
    composeTestRule.waitForIdle()
    assertEquals(1, repository.requests.count { it == "shown" })
    assertEquals(2, repository.requests.count { it == "offline" })
    assertEquals(1, repository.requests.count { it == "diskFull" })
    assertEquals(1, repository.requests.count { it == "undecodable" })
    assertEquals(2, composeTestRule.onAllNodesWithText(RETRY).fetchSemanticsNodes().size)
  }

  @Test
  fun aTapWhileTheConnectionComebackLoadsStartsNoSecondLoad() {
    val retriedLoadRunning = CompletableDeferred<Unit>()
    repository.onGetFile = {
      if (repository.requests.size == 1) throw UnknownHostException("offline")
      retriedLoadRunning.complete(Unit)
      awaitCancellation()
    }
    lateinit var retry: () -> Unit
    setImageKeepingRetry { retry = it }
    waitForText()

    comeBackOnline()
    waitUntil { retriedLoadRunning.isCompleted }
    composeTestRule.onNodeWithText(LOADING).assertIsDisplayed()
    // The user taps the retry button still shown a moment ago.
    composeTestRule.runOnIdle { retry() }
    composeTestRule.waitForIdle()

    // A restart would have cancelled the running load and asked for the file again.
    assertEquals(listOf("m1", "m1"), repository.requests)
  }

  /**
   * Waits until the image shows its failure, which comes after every log of its load, and returns
   * the single log of the media pictures, by MediaImage or by its fetcher: never both.
   */
  private fun waitForLog(): ShadowLog.LogItem {
    waitForText()
    composeTestRule.waitForIdle()
    return (ShadowLog.getLogsForTag(MEDIA_IMAGE_TAG) +
            ShadowLog.getLogsForTag(MEDIA_PICTURE_FETCHER_TAG))
        .single()
  }

  @Test
  fun aLoadFailureIsLoggedOnceAsAWarningWithItsCause() {
    val offline = IOException("offline and not cached")
    repository.onGetFile = { throw offline }
    setImages(m1)

    val log = waitForLog()
    assertEquals(MEDIA_PICTURE_FETCHER_TAG, log.tag)
    assertEquals(Log.WARN, log.type)
    assertSame(offline, log.throwable)
    assertTrue(log.msg.contains("m1"))
  }

  @Test
  fun aMediaNotInTheCircleIsLoggedAsAnError() {
    // What getFile throws when the screen passed the wrong circle: a bug, not being offline.
    val wrongCircle = IllegalArgumentException("media m1 is not in circle c1")
    repository.onGetFile = { throw wrongCircle }
    setImages(m1)

    val log = waitForLog()
    assertEquals(MEDIA_PICTURE_FETCHER_TAG, log.tag)
    assertEquals(Log.ERROR, log.type)
    assertSame(wrongCircle, log.throwable)
  }

  @Test
  fun aDecodeFailureIsLoggedWithCoilsCause() {
    decoder.broken += PHOTO
    setImages(m1)

    val log = waitForLog()
    assertEquals(MEDIA_IMAGE_TAG, log.tag)
    assertEquals(Log.WARN, log.type)
    assertEquals("not an image", log.throwable?.message)
    assertTrue(log.msg.contains("m1"))
  }

  @Test
  fun decodesThePictureAtItsLaidOutSizeNotItsFullResolution() {
    setImages(m1)

    waitForImage()
    val size = decoder.sizes.single()
    // 100.dp at the test's density: never Size.ORIGINAL, which would decode every pixel.
    val expected = with(composeTestRule.density) { 100.dp.roundToPx() }
    assertEquals(Size(expected, expected), size)
  }

  @Test
  fun aspectRatioOfReservesThePicturesShape() {
    val wide = MediaItem.Picture(id = "w", width = 400, height = 200)
    val unknown = MediaItem.Picture(id = "u")
    composeTestRule.setContent {
      Box(Modifier.width(100.dp).aspectRatioOf(wide).testTag("wide"))
      Box(Modifier.width(100.dp).aspectRatioOf(unknown).testTag("unknown"))
    }

    composeTestRule.onNodeWithTag("wide").assertHeightIsEqualTo(50.dp)
    composeTestRule.onNodeWithTag("unknown").assertHeightIsEqualTo(100.dp)
  }

  /**
   * Replaces Coil's decoding: reads the fetched file's text, then answers with a small bitmap, or
   * an error for the texts in [broken], after [gate] completes if one is set. So Robolectric needs
   * no real image decoding, while the real fetcher still gets and opens the files.
   */
  private class FakeDecoder : Decoder.Factory {
    // Written on Coil's threads, read and changed by the test thread.
    val requested: MutableList<String> = CopyOnWriteArrayList()
    val decoded: MutableList<String> = CopyOnWriteArrayList()
    val sizes: MutableList<Size> = CopyOnWriteArrayList()
    val broken: MutableSet<String> = ConcurrentHashMap.newKeySet()
    @Volatile var gate: CompletableDeferred<Unit>? = null

    override fun create(
        result: SourceFetchResult,
        options: Options,
        imageLoader: ImageLoader,
    ) = Decoder {
      val text = result.source.source().readUtf8()
      requested += text
      sizes += options.size
      gate?.await()
      if (text in broken) throw IOException("not an image")
      decoded += text
      DecodeResult(image = smallBitmap().asImage(), isSampled = false)
    }
  }

  private companion object {
    val TIMEOUT = 5_000.milliseconds
    const val DESCRIPTION = "A photo of the garden"
    const val PHOTO = "photo"
    const val OTHER_PHOTO = "other photo"
    const val LOADING = "Loading"
    const val RETRY = "Try again"
    const val BROKEN = "not an image"

    fun smallBitmap(): Bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
  }
}
