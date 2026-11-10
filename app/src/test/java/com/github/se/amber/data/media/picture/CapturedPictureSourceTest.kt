// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.media.picture

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.File
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Tests [CapturedPictureSourceAndroid] in `check`. The FileProvider is replaced by one with the
 * same URI shape, as Robolectric's temporary folders hide its root behind a symlink on macOS;
 * `CapturedPictureSourceAndroidTest` covers the real one on a device.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CapturedPictureSourceTest {
  private companion object {
    const val WIDTH = 40
    const val HEIGHT = 30
    const val JPEG_QUALITY = 90
  }

  private lateinit var context: Context
  private lateinit var captureDir: File
  private lateinit var source: CapturedPictureSourceAndroid

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    captureDir = File(context.cacheDir, CAPTURED_PICTURES_DIR)
    mockkStatic(FileProvider::class)
    every { FileProvider.getUriForFile(any(), any(), any()) } answers
        {
          Uri.parse(
              "content://${secondArg<String>()}/$CAPTURED_PICTURES_DIR/${thirdArg<File>().name}"
          )
        }
    source = CapturedPictureSourceAndroid(context, UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() {
    unmockkStatic(FileProvider::class)
    captureDir.deleteRecursively()
  }

  private fun captureFile(uri: String) = File(captureDir, Uri.parse(uri).lastPathSegment!!)

  /** Writes a JPEG to the capture, the way the camera app does through the URI. */
  private fun writeJpeg(uri: String) {
    captureFile(uri).outputStream().use {
      assertTrue(
          Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
              .compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
      )
    }
  }

  private suspend fun readFailure(uri: String) = runCatching { source.read(uri) }.exceptionOrNull()

  @Test
  fun aNewCaptureIsAnEmptyJpegSharedThroughTheAppAuthority() = runTest {
    val uri = Uri.parse(source.newCaptureUri())

    assertEquals("content", uri.scheme)
    assertEquals("${context.packageName}.fileprovider", uri.authority)
    assertTrue(uri.lastPathSegment!!.endsWith(".jpg"))
    assertEquals(0L, captureFile(uri.toString()).length())
  }

  @Test
  fun aCaptureTheCameraWroteIsReadWithItsDimensions() = runTest {
    val uri = source.newCaptureUri()
    writeJpeg(uri)

    val picture = source.read(uri)

    assertEquals(captureFile(uri), picture.file)
    assertEquals(WIDTH, picture.width)
    assertEquals(HEIGHT, picture.height)
  }

  @Test
  fun aCaptureTheCameraNeverWroteCannotBeRead() = runTest {
    assertTrue(readFailure(source.newCaptureUri()) is IllegalArgumentException)
  }

  @Test
  fun aNewCaptureDeletesLeftoversButNotThePictureStillInUse() = runTest {
    val leftover = source.newCaptureUri()
    val inUse = source.newCaptureUri()
    assertFalse(captureFile(leftover).exists())

    source.newCaptureUri(keeping = inUse)

    assertTrue(captureFile(inUse).exists())
    assertEquals(2, captureDir.list()!!.size)
  }

  @Test
  fun discardDeletesOnlyThatCapture() = runTest {
    val discarded = source.newCaptureUri()
    val kept = source.newCaptureUri(keeping = discarded)

    source.discard(discarded)

    assertFalse(captureFile(discarded).exists())
    assertTrue(captureFile(kept).exists())
  }

  @Test
  fun aUriFromAnotherProviderIsRejected() = runTest {
    val name = Uri.parse(source.newCaptureUri()).lastPathSegment

    assertTrue(
        readFailure("content://com.example.other/captured_pictures/$name")
            is IllegalArgumentException
    )
    assertTrue(readFailure(File(captureDir, name!!).toUri()) is IllegalArgumentException)
  }

  @Test
  fun aUriLeadingOutOfTheCaptureFolderIsRejectedAndNothingIsDeleted() = runTest {
    val outside = File(context.cacheDir, "outside.txt").apply { writeText("keep me") }
    val escaping =
        "content://${context.packageName}.fileprovider/captured_pictures/..%2Foutside.txt"

    val failure = runCatching { source.discard(escaping) }.exceptionOrNull()

    assertTrue(failure is IllegalArgumentException)
    assertTrue(outside.exists())
    outside.delete()
  }

  private fun File.toUri() = Uri.fromFile(this).toString()
}
