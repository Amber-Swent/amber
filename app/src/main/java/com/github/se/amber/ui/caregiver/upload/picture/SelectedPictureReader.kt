/* Written by Lou-Anne Maier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload.picture

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
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

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }

        BitmapFactory.decodeFile(
            file.absolutePath,
            options,
        )

        PickedImage(
            file = file,
            width = options.outWidth,
            height = options.outHeight,
        )
      }
}

/** Lazily initialized provider for the Android selected-picture reader. */
val reader: SelectedPictureReader by lazy {
  SelectedPictureReaderAndroid(Firebase.app.applicationContext)
}
