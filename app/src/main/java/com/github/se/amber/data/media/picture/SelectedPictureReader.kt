// Written by Lou-Anne Maier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.media.picture

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.Firebase
import com.google.firebase.app
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads a user-selected picture and exposes its local file and dimensions. */
interface SelectedPictureReader {
  /**
   * Reads the picture identified by the supplied URI.
   *
   * @param uri The string representation of the selected picture URI.
   * @return The locally readable picture and its pixel dimensions.
   */
  suspend fun read(uri: String): PickedImage
}

/**
 * Represents a picture copied to a local file, together with its pixel dimensions.
 *
 * @property file The local file containing the picture.
 * @property width The picture width in pixels.
 * @property height The picture height in pixels.
 */
data class PickedImage(
    val file: File,
    val width: Int,
    val height: Int,
)

/**
 * Reads a selected picture through Android's content resolver.
 *
 * @param context The context used to access the content resolver and application cache.
 * @param ioDispatcher The dispatcher used for file and content-resolver operations.
 */
class SelectedPictureReaderAndroid(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SelectedPictureReader {
  private val appContext = context.applicationContext

  /**
   * Copies the selected picture to a temporary local file and reads its dimensions.
   *
   * @param uri The string representation of the selected picture URI.
   * @return The temporary local picture file and its pixel dimensions.
   */
  override suspend fun read(uri: String): PickedImage =
      withContext(ioDispatcher) {
        val parsedUri = Uri.parse(uri)

        val file =
            File.createTempFile(
                "picked_",
                ".jpg",
                appContext.cacheDir,
            )

        val input =
            requireNotNull(appContext.contentResolver.openInputStream(parsedUri)) {
              "Could not open selected picture"
            }

        input.use { source ->
          file.outputStream().use { destination -> source.copyTo(destination) }
        }

        decodePickedImage(file)
      }
}

/**
 * Reads the dimensions of a local picture as it is displayed: cameras often save a portrait photo
 * as landscape pixels plus a rotation, which image loaders apply when they show it. Also removes
 * the GPS position the camera may have saved: where a picture was taken is what the user picks.
 *
 * @param file The local picture file.
 * @return The picture and its displayed pixel dimensions.
 * @throws IllegalArgumentException If the file isn't a picture.
 */
internal fun decodePickedImage(file: File): PickedImage {
  val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }

  BitmapFactory.decodeFile(
      file.absolutePath,
      options,
  )

  require(options.outWidth > 0 && options.outHeight > 0) { "Could not decode selected picture" }

  val isSideways = isSavedSideways(file)
  removeLocation(file)
  return PickedImage(
      file = file,
      width = if (isSideways) options.outHeight else options.outWidth,
      height = if (isSideways) options.outWidth else options.outHeight,
  )
}

/** A picture whose metadata can't be read still decoded fine, so it just counts as upright. */
private fun isSavedSideways(file: File): Boolean = runCatching {
  ExifInterface(file).rotationDegrees % 180 != 0
}
    .getOrDefault(false)

/** Some formats can't be edited, such as HEIC: those pictures are kept as they are. */
private fun removeLocation(file: File) {
  runCatching {
    val metadata = ExifInterface(file)
    if (LOCATION_TAGS.none(metadata::hasAttribute)) return
    LOCATION_TAGS.forEach { tag -> metadata.setAttribute(tag, null) }
    metadata.saveAttributes()
  }
}

/** Every GPS tag, as an app may save a place in any of them, such as the area's name. */
private val LOCATION_TAGS =
    listOf(
        ExifInterface.TAG_GPS_VERSION_ID,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_SATELLITES,
        ExifInterface.TAG_GPS_STATUS,
        ExifInterface.TAG_GPS_MEASURE_MODE,
        ExifInterface.TAG_GPS_DOP,
        ExifInterface.TAG_GPS_SPEED,
        ExifInterface.TAG_GPS_SPEED_REF,
        ExifInterface.TAG_GPS_TRACK,
        ExifInterface.TAG_GPS_TRACK_REF,
        ExifInterface.TAG_GPS_IMG_DIRECTION,
        ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
        ExifInterface.TAG_GPS_MAP_DATUM,
        ExifInterface.TAG_GPS_DEST_LATITUDE,
        ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
        ExifInterface.TAG_GPS_DEST_LONGITUDE,
        ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
        ExifInterface.TAG_GPS_DEST_BEARING,
        ExifInterface.TAG_GPS_DEST_BEARING_REF,
        ExifInterface.TAG_GPS_DEST_DISTANCE,
        ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_AREA_INFORMATION,
        ExifInterface.TAG_GPS_DIFFERENTIAL,
        ExifInterface.TAG_GPS_H_POSITIONING_ERROR,
    )

/** Lazily initialized provider for the Android selected-picture reader. */
val reader: SelectedPictureReader by lazy {
  SelectedPictureReaderAndroid(Firebase.app.applicationContext)
}
