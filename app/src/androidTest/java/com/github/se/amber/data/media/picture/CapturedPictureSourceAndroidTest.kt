// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.media.picture

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests [CapturedPictureSourceAndroid] against the app's real FileProvider, as a camera uses it.
 */
class CapturedPictureSourceAndroidTest {
  private companion object {
    const val WIDTH = 40
    const val HEIGHT = 30
    const val JPEG_QUALITY = 90
    // PNG is lossless, so the quality is ignored but must be given
    const val PNG_QUALITY = 100
    const val ROTATION_DEGREES = 90
    const val LAUSANNE_LAT = 46.52
    const val LAUSANNE_LNG = 6.63
  }

  private lateinit var context: Context
  private lateinit var source: CapturedPictureSourceAndroid

  @Before
  fun setUp() {
    context = InstrumentationRegistry.getInstrumentation().targetContext
    source = CapturedPictureSourceAndroid(context)
  }

  /** Writes a JPEG through [uri], the way the camera app does. */
  private fun writeJpeg(uri: String) {
    val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    context.contentResolver.openOutputStream(Uri.parse(uri)).use {
      assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, requireNotNull(it)))
    }
  }

  private fun captureFile(uri: String) =
      File(File(context.cacheDir, CAPTURED_PICTURES_DIR), Uri.parse(uri).lastPathSegment!!)

  @Test
  fun aPictureWrittenThroughTheNewUriIsReadBackWithItsDimensions() = runBlocking {
    val uri = source.newCaptureUri()
    assertEquals("content", Uri.parse(uri).scheme)
    assertEquals("${context.packageName}.fileprovider", Uri.parse(uri).authority)
    assertTrue(uri.endsWith(".jpg"))

    writeJpeg(uri)
    val picture = source.read(uri)

    assertEquals(captureFile(uri), picture.file)
    assertEquals(WIDTH, picture.width)
    assertEquals(HEIGHT, picture.height)
  }

  @Test
  fun aSidewaysPictureKeepsItsDisplayedDimensionsAndLosesItsLocation() = runBlocking {
    val uri = source.newCaptureUri()
    writeJpeg(uri)
    ExifInterface(captureFile(uri)).apply {
      setAttribute(
          ExifInterface.TAG_ORIENTATION,
          ExifInterface.ORIENTATION_ROTATE_90.toString(),
      )
      setLatLong(LAUSANNE_LAT, LAUSANNE_LNG)
      saveAttributes()
    }

    val picture = source.read(uri)

    assertEquals(HEIGHT, picture.width)
    assertEquals(WIDTH, picture.height)
    val saved = ExifInterface(captureFile(uri))
    assertNull(saved.latLong)
    assertEquals(ROTATION_DEGREES, saved.rotationDegrees)
  }

  @Test
  fun aGalleryPictureWithoutMetadataIsStillRead() = runBlocking {
    val png = File(context.cacheDir, "gallery.png")
    png.outputStream().use {
      assertTrue(
          Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
              .compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)
      )
    }

    val picture = SelectedPictureReaderAndroid(context).read(Uri.fromFile(png).toString())

    assertEquals(WIDTH, picture.width)
    assertEquals(HEIGHT, picture.height)
    png.delete()
    picture.file.delete()
    Unit
  }

  @Test
  fun readingACaptureTheCameraNeverWroteFails() = runBlocking {
    val uri = source.newCaptureUri()

    assertThrows(IllegalArgumentException::class.java) { runBlocking { source.read(uri) } }
    source.discard(uri)
  }

  @Test
  fun aNewCaptureDeletesLeftoversButNotThePictureStillInUse() = runBlocking {
    val leftover = source.newCaptureUri()
    val inUse = source.newCaptureUri()
    // deleted by the previous call, as nothing kept it
    assertFalse(captureFile(leftover).exists())

    val next = source.newCaptureUri(keeping = inUse)

    assertTrue(captureFile(inUse).exists())
    source.discard(inUse)
    source.discard(next)
  }

  @Test
  fun discardDeletesOnlyThatCapture() = runBlocking {
    val discarded = source.newCaptureUri()
    val kept = source.newCaptureUri(keeping = discarded)

    source.discard(discarded)

    assertFalse(captureFile(discarded).exists())
    assertTrue(captureFile(kept).exists())
    source.discard(kept)
  }
}
