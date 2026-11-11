// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.media.picture

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Tests the rotation and location handling of [decodePickedImage] on real JPEG files. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DecodePickedImageTest {
  private companion object {
    const val WIDTH = 40
    const val HEIGHT = 30
    const val JPEG_QUALITY = 90
    const val LAUSANNE_LAT = 46.52
    const val LAUSANNE_LNG = 6.63
    const val DEST_LAT = "46/1,31/1,0/1"
    const val DEST_LNG = "6/1,38/1,0/1"
    const val AREA = "Lausanne"
    const val ROTATION_DEGREES = 90
  }

  @get:Rule val tempFolder = TemporaryFolder()

  private fun jpeg(orientation: Int? = null, edit: ExifInterface.() -> Unit = {}): File {
    val file = File.createTempFile("picture", ".jpg", tempFolder.root)
    file.outputStream().use {
      assertTrue(
          Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
              .compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
      )
    }
    ExifInterface(file).apply {
      orientation?.let { setAttribute(ExifInterface.TAG_ORIENTATION, it.toString()) }
      edit()
      saveAttributes()
    }
    return file
  }

  @Test
  fun anUprightPictureKeepsItsDimensions() {
    val picture = decodePickedImage(jpeg())

    assertEquals(WIDTH, picture.width)
    assertEquals(HEIGHT, picture.height)
  }

  @Test
  fun aPictureTurnedAQuarterIsReadWithSwappedDimensions() {
    listOf(
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE,
        )
        .forEach { orientation ->
          val picture = decodePickedImage(jpeg(orientation))

          assertEquals("orientation $orientation", HEIGHT, picture.width)
          assertEquals("orientation $orientation", WIDTH, picture.height)
          picture.file.delete()
        }
  }

  @Test
  fun anUpsideDownPictureKeepsItsDimensions() {
    val picture = decodePickedImage(jpeg(ExifInterface.ORIENTATION_ROTATE_180))

    assertEquals(WIDTH, picture.width)
    assertEquals(HEIGHT, picture.height)
  }

  @Test
  fun everyGpsTagIsRemovedButTheRotationIsKept() {
    val file =
        jpeg(ExifInterface.ORIENTATION_ROTATE_90) {
          setLatLong(LAUSANNE_LAT, LAUSANNE_LNG)
          setAttribute(ExifInterface.TAG_GPS_DEST_LATITUDE, DEST_LAT)
          setAttribute(ExifInterface.TAG_GPS_DEST_LATITUDE_REF, "N")
          setAttribute(ExifInterface.TAG_GPS_DEST_LONGITUDE, DEST_LNG)
          setAttribute(ExifInterface.TAG_GPS_DEST_LONGITUDE_REF, "E")
          setAttribute(ExifInterface.TAG_GPS_AREA_INFORMATION, AREA)
        }

    decodePickedImage(file)

    val saved = ExifInterface(file)
    assertNull(saved.latLong)
    assertNull(saved.getAttribute(ExifInterface.TAG_GPS_DEST_LATITUDE))
    assertNull(saved.getAttribute(ExifInterface.TAG_GPS_DEST_LONGITUDE))
    assertNull(saved.getAttribute(ExifInterface.TAG_GPS_AREA_INFORMATION))
    assertEquals(ROTATION_DEGREES, saved.rotationDegrees)
  }

  @Test
  fun aPictureWithoutLocationIsNotRewritten() {
    val file = jpeg()
    val before = file.readBytes()

    decodePickedImage(file)

    assertArrayEquals(before, file.readBytes())
  }

  @Test
  fun aFileThatIsNotAPictureIsRejected() {
    val file = tempFolder.newFile("notes.jpg").apply { writeText("not a picture") }

    assertThrows(IllegalArgumentException::class.java) { decodePickedImage(file) }
  }
}
