/* Written by Neha Chakraborty, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.data.media.picture

import android.content.Context
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.google.firebase.Firebase
import com.google.firebase.app
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The folder in the application cache that holds captured pictures. Must match the path in
 * res/xml/file_paths.xml; `CapturedPictureSourceAndroidTest` fails if they differ, as the
 * FileProvider then refuses to share a capture.
 */
const val CAPTURED_PICTURES_DIR = "captured_pictures"

/** Provides the temporary files the camera app saves captured pictures to. */
interface CapturedPictureSource {
  /**
   * Creates an empty temporary picture for the camera app to write to. Older captures are deleted:
   * private pictures can be left behind if the app dies before they are used.
   *
   * @param keeping A URI from an earlier call whose picture is still in use, if any.
   * @return The string representation of the picture's content URI.
   */
  suspend fun newCaptureUri(keeping: String? = null): String

  /**
   * Reads a picture the camera app finished writing.
   *
   * @param uri A URI returned by [newCaptureUri].
   * @return The local picture and its pixel dimensions.
   */
  suspend fun read(uri: String): PickedImage

  /**
   * Deletes a temporary picture that won't be used.
   *
   * @param uri A URI returned by [newCaptureUri].
   */
  suspend fun discard(uri: String)
}

/**
 * Keeps captured pictures in the application cache and shares them through the app's FileProvider.
 *
 * @param context The context used to access the application cache and the FileProvider.
 * @param ioDispatcher The dispatcher used for file operations.
 */
class CapturedPictureSourceAndroid(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CapturedPictureSource {
  private val appContext = context.applicationContext
  private val captureDir = File(appContext.cacheDir, CAPTURED_PICTURES_DIR)

  override suspend fun newCaptureUri(keeping: String?): String =
      withContext(ioDispatcher) {
        captureDir.mkdirs()
        val kept = keeping?.let(::fileOf)
        captureDir.listFiles()?.filter { it != kept }?.forEach(::delete)
        val file = File.createTempFile("captured_", ".jpg", captureDir)
        FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
            .toString()
      }

  override suspend fun read(uri: String): PickedImage =
      withContext(ioDispatcher) { decodePickedImage(fileOf(uri)) }

  override suspend fun discard(uri: String) {
    withContext(ioDispatcher) { delete(fileOf(uri)) }
  }

  /** A file that can't be deleted is deleted with the next capture's leftovers. */
  private fun delete(file: File) {
    if (!file.delete() && file.exists()) Log.w(TAG, "Couldn't delete the capture ${file.name}")
  }

  /** The file behind a URI from [newCaptureUri]: it is named after the URI's last segment. */
  private fun fileOf(uri: String): File =
      File(captureDir, requireNotNull(uri.toUri().lastPathSegment) { "Invalid picture URI" })
}

private const val TAG = "CapturedPictureSource"

/** Lazily initialized provider for the Android captured-picture source. */
val capturedPictures: CapturedPictureSource by lazy {
  CapturedPictureSourceAndroid(Firebase.app.applicationContext)
}
