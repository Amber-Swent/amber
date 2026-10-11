// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
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

  private fun waitUntilGone(text: String) {
    composeTestRule.waitUntil(TIMEOUT_MS) {
      composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
    }
  }

  @Test
  fun decodesAndShowsARealPng() {
    val png = File(dir, "photo.png")
    val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
    png.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    show(png)

    waitUntilGone(LOADING)
    composeTestRule.onNodeWithContentDescription(DESCRIPTION).assertIsDisplayed()
    composeTestRule.onNodeWithText(FAILED).assertDoesNotExist()
  }

  @Test
  fun showsFailedForAFileThatIsNotAnImage() {
    val notImage = File(dir, "photo.jpg").apply { writeText("not an image") }

    show(notImage)

    waitUntilGone(LOADING)
    composeTestRule.onNodeWithText(FAILED).assertIsDisplayed()
  }

  private companion object {
    const val DESCRIPTION = "A red square"
    const val LOADING = "Loading"
    const val FAILED = "Not available offline"
    const val TIMEOUT_MS = 10_000L
  }
}
