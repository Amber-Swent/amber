/* Written by Neha Chakraborty, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload.pictures

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import com.github.se.amber.ui.caregiver.upload.picture.CapturedPictureSourceAndroid
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests [CapturedPictureSourceAndroid] against the app's real FileProvider, as a camera uses it.
 */
class CapturedPictureSourceAndroidTest {
  private val width = 40
  private val height = 30

  private lateinit var context: Context
  private lateinit var source: CapturedPictureSourceAndroid

  @Before
  fun setUp() {
    context = InstrumentationRegistry.getInstrumentation().targetContext
    source = CapturedPictureSourceAndroid(context)
  }

  /** Writes a JPEG through [uri], the way the camera app does. */
  private fun writeJpeg(uri: String) {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    context.contentResolver.openOutputStream(Uri.parse(uri)).use {
      assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, requireNotNull(it)))
    }
  }

  private fun captureFile(uri: String) =
      File(File(context.cacheDir, "captured_pictures"), Uri.parse(uri).lastPathSegment!!)

  @Test
  fun aPictureWrittenThroughTheNewUriIsReadBackWithItsDimensions() = runBlocking {
    val uri = source.newCaptureUri()
    assertEquals("content", Uri.parse(uri).scheme)
    assertEquals("${context.packageName}.fileprovider", Uri.parse(uri).authority)
    assertTrue(uri.endsWith(".jpg"))

    writeJpeg(uri)
    val picture = source.read(uri)

    assertEquals(captureFile(uri), picture.file)
    assertEquals(width, picture.width)
    assertEquals(height, picture.height)
  }

  @Test
  fun aPictureSavedSidewaysIsReadWithTheDimensionsItIsDisplayedWith() = runBlocking {
    val uri = source.newCaptureUri()
    writeJpeg(uri)
    ExifInterface(captureFile(uri)).apply {
      setAttribute(
          ExifInterface.TAG_ORIENTATION,
          ExifInterface.ORIENTATION_ROTATE_90.toString(),
      )
      saveAttributes()
    }

    val picture = source.read(uri)

    assertEquals(height, picture.width)
    assertEquals(width, picture.height)
  }

  @Test
  fun readingACaptureTheCameraNeverWroteFails() = runBlocking {
    val uri = source.newCaptureUri()

    assertThrows(IllegalArgumentException::class.java) { runBlocking { source.read(uri) } }
    Unit
  }

  @Test
  fun discardDeletesOnlyThatCapture() = runBlocking {
    val discarded = source.newCaptureUri()
    val kept = source.newCaptureUri()

    source.discard(discarded)

    assertFalse(captureFile(discarded).exists())
    assertTrue(captureFile(kept).exists())
    source.discard(kept)
  }
}
