/* Written by Neha Chakraborty, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload.picture

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.firebase.Firebase
import com.google.firebase.app
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Provides the temporary files the camera app saves captured pictures to. */
interface CapturedPictureSource {
  /**
   * Creates an empty temporary picture for the camera app to write to.
   *
   * @return The string representation of the picture's content URI.
   */
  suspend fun newCaptureUri(): String

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
  // Must match the path in res/xml/file_paths.xml
  private val captureDir = File(appContext.cacheDir, "captured_pictures")

  override suspend fun newCaptureUri(): String =
      withContext(ioDispatcher) {
        captureDir.mkdirs()
        val file = File.createTempFile("captured_", ".jpg", captureDir)
        FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
            .toString()
      }

  override suspend fun read(uri: String): PickedImage =
      withContext(ioDispatcher) { decodePickedImage(fileOf(uri)) }

  override suspend fun discard(uri: String) {
    withContext(ioDispatcher) { fileOf(uri).delete() }
  }

  /** The file behind a URI from [newCaptureUri]: it is named after the URI's last segment. */
  private fun fileOf(uri: String): File =
      File(captureDir, requireNotNull(Uri.parse(uri).lastPathSegment) { "Invalid picture URI" })
}

/** Lazily initialized provider for the Android captured-picture source. */
val capturedPictures: CapturedPictureSource by lazy {
  CapturedPictureSourceAndroid(Firebase.app.applicationContext)
}
