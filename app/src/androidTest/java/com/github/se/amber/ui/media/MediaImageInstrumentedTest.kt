// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks [MediaImage] with Coil's real decoding of files written on the device. */
@RunWith(AndroidJUnit4::class)
class MediaImageInstrumentedTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()
  private lateinit var dir: File

  @Before
  fun setUp() {
    dir = File(context.cacheDir, "media-image-test").apply { mkdirs() }
    clearMediaImageMemoryCache(context)
  }

  @After
  fun tearDown() {
    dir.deleteRecursively()
    clearMediaImageMemoryCache(context)
  }

  private fun show(file: File) {
    composeTestRule.setContent {
      MediaImage(
          mediaId = file.name,
          loadFile = { file },
          contentDescription = DESCRIPTION,
          modifier = Modifier.size(100.dp),
          loading = { Text(LOADING) },
          failed = { Text(FAILED) },
      )
    }
  }

  private fun writePng(name: String): File {
    val png = File(dir, name)
    val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
    png.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return png
  }

  // Waits for the final state itself: the absence of the loading slot alone doesn't prove the
  // picture is shown.
  private fun waitUntilShown() {
    composeTestRule.waitUntil(TIMEOUT_MS) {
      composeTestRule
          .onAllNodesWithContentDescription(DESCRIPTION)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
  }

  @Test
  fun decodesAndShowsARealPng() {
    show(writePng("photo.png"))

    waitUntilShown()
    composeTestRule.onNodeWithContentDescription(DESCRIPTION).assertIsDisplayed()
    composeTestRule.onNodeWithText(LOADING).assertDoesNotExist()
    composeTestRule.onNodeWithText(FAILED).assertDoesNotExist()
  }

  @Test
  fun showsFailedForAFileThatIsNotAnImage() {
    val notImage = File(dir, "photo.jpg").apply { writeText("not an image") }

    show(notImage)

    composeTestRule.waitUntil(TIMEOUT_MS) {
      composeTestRule.onAllNodesWithText(FAILED).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText(FAILED).assertIsDisplayed()
    composeTestRule.onNodeWithText(LOADING).assertDoesNotExist()
  }

  @Test
  fun aPictureInCoilsMemoryCacheIsVisibleInTheFirstFrameAfterItsFileIsLoaded() {
    val png = writePng("photo.png")
    var shown by mutableStateOf(true)
    // Written by loadFile on the main thread, read by the test thread: atomic for visibility.
    val fileLoaded = AtomicBoolean(false)
    composeTestRule.setContent {
      if (shown) {
        MediaImage(
            mediaId = "m1",
            loadFile = {
              fileLoaded.set(true)
              png
            },
            contentDescription = DESCRIPTION,
            modifier = Modifier.size(100.dp).testTag(CONTAINER),
            // A solid slot over the whole image, so a pixel tells which one is drawn on top.
            loading = { Box(Modifier.fillMaxSize().background(ComposeColor.Blue)) },
        )
      }
    }
    // First display: Coil decodes the file and keeps the picture in its memory cache.
    waitUntilShown()

    // Leave and come back, like scrolling an item out of a list and back. The clock is paused so
    // that the test runs the frames one by one.
    shown = false
    composeTestRule.waitForIdle()
    fileLoaded.set(false)
    composeTestRule.mainClock.autoAdvance = false
    shown = true
    // A state written outside composition is only seen by Compose once published.
    Snapshot.sendApplyNotifications()
    var frames = 0
    while (!fileLoaded.get() && frames < MAX_FRAMES) {
      composeTestRule.mainClock.advanceTimeByFrame()
      frames++
    }
    assertTrue("loadFile didn't run within $MAX_FRAMES frames", fileLoaded.get())

    // The first frame composed with the file: the cached picture must be drawn, over the loading
    // slot, not the slot itself (blue) or nothing.
    composeTestRule.mainClock.advanceTimeByFrame()
    val bitmap = composeTestRule.onNodeWithTag(CONTAINER).captureToImage().asAndroidBitmap()
    assertEquals(Color.RED, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
  }

  private companion object {
    const val DESCRIPTION = "A red square"
    const val LOADING = "Loading"
    const val FAILED = "Not available offline"
    const val TIMEOUT_MS = 10_000L
    const val CONTAINER = "mediaImageContainer"
    const val MAX_FRAMES = 30
  }
}
