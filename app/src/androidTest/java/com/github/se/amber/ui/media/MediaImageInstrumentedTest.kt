// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
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

  // Waits for the final state itself: the absence of the loading slot isn't enough, since nothing
  // is drawn for a frame between loading the file and Coil starting to decode it.
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
  fun aPictureInCoilsMemoryCacheIsShownWithoutTheLoadingSlotOnceItsFileIsLoaded() {
    val png = writePng("photo.png")
    var shown by mutableStateOf(true)
    // Set by loadFile: any loading slot composed after it comes from Coil's step, not the file's.
    var fileLoaded = false
    var loadingAfterFile = false
    composeTestRule.setContent {
      if (shown) {
        MediaImage(
            mediaId = "m1",
            loadFile = {
              fileLoaded = true
              png
            },
            contentDescription = DESCRIPTION,
            modifier = Modifier.size(100.dp),
            loading = {
              if (fileLoaded) loadingAfterFile = true
              Text(LOADING)
            },
        )
      }
    }
    // First display: Coil really decodes the file, which shows the loading slot. This also proves
    // the probe detects it. The decoded picture is now in Coil's memory cache.
    waitUntilShown()
    assertTrue(loadingAfterFile)

    // Leave and come back, like scrolling an item out of a list and back.
    shown = false
    composeTestRule.waitForIdle()
    fileLoaded = false
    loadingAfterFile = false
    shown = true
    composeTestRule.waitForIdle()
    waitUntilShown()

    assertFalse(loadingAfterFile)
  }

  private companion object {
    const val DESCRIPTION = "A red square"
    const val LOADING = "Loading"
    const val FAILED = "Not available offline"
    const val TIMEOUT_MS = 10_000L
  }
}
