// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.github.se.amber.data.media.MAX_FILE_BYTES
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Reads the audio files the user picks, through Android's content resolver. Each one is copied into
 * an `audio/picked` folder of the app's cache, and its duration is read with
 * [MediaMetadataRetriever].
 *
 * The files left in that folder by a previous run of the app are deleted on the first [read]. It
 * uses its own folder, so this can't delete a recording waiting to be uploaded.
 *
 * @param context any context; only its application context is kept.
 * @param ioDispatcher runs the copy and the metadata reading.
 * @param maxBytes the biggest file it accepts. Only tests change it, to avoid copying 25 MiB.
 */
class AndroidSelectedAudioReader(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxBytes: Long = MAX_FILE_BYTES,
) : SelectedAudioReader {

  private val appContext = context.applicationContext
  private val directory = File(appContext.cacheDir, PICKED_DIR)

  /** Guards [leftoversDeleted], as two reads may run at once on [ioDispatcher]'s threads. */
  private val lock = Any()
  private var leftoversDeleted = false

  /**
   * Reads the audio on [ioDispatcher] (see [readNow]). If the caller is cancelled once the copy is
   * done, `withContext` throws instead of returning it, so nobody would ever take the file: it is
   * deleted then.
   */
  override suspend fun read(uri: String): LocalAudio {
    var audio: LocalAudio? = null
    try {
      return withContext(ioDispatcher) { readNow(uri.toUri()).also { audio = it } }
    } catch (e: CancellationException) {
      audio?.file?.delete()
      throw e
    }
  }

  /**
   * Checks the type, copies the audio into a new file of the folder and reads its duration. On any
   * failure, the file is deleted and the error becomes an [IllegalArgumentException], as the
   * contract promises (a cancellation stays one). This includes the content resolver's own errors,
   * e.g. a [SecurityException] when the app may no longer read the file.
   */
  private suspend fun readNow(uri: Uri): LocalAudio {
    var file: File? = null
    try {
      val resolver = appContext.contentResolver
      require(isM4a(resolver, uri)) { "The picked audio isn't an .m4a file" }
      file = File.createTempFile("picked_", ".m4a", prepareDirectory())
      copy(resolver, uri, file)
      return LocalAudio(file, readDurationMs(file))
    } catch (e: Exception) {
      file?.delete()
      throw when (e) {
        is CancellationException,
        is IllegalArgumentException -> e
        else -> IllegalArgumentException("Couldn't read the picked audio", e)
      }
    }
  }

  /**
   * Whether the audio is an .m4a: by its MIME type, or by its name only when the app that provides
   * it gives no real type. A known type wins, so an MP3 named .m4a is refused.
   */
  private fun isM4a(resolver: ContentResolver, uri: Uri): Boolean {
    val type = resolver.getType(uri)
    if (type != null && type != UNKNOWN_TYPE) return type in M4A_TYPES
    return displayName(resolver, uri).orEmpty().endsWith(".m4a", ignoreCase = true)
  }

  /** The file name the providing app shows for the audio, or null if it gives none. */
  private fun displayName(resolver: ContentResolver, uri: Uri): String? =
      resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
      }

  /**
   * Copies the audio into [file], giving up as soon as it is bigger than [maxBytes], or when the
   * caller is cancelled, as a big file can take a while.
   */
  private suspend fun copy(resolver: ContentResolver, uri: Uri, file: File) {
    val input = requireNotNull(resolver.openInputStream(uri)) { "The picked audio can't be opened" }
    input.use { source ->
      file.outputStream().use { destination ->
        val buffer = ByteArray(BUFFER_BYTES)
        var copied = 0L
        while (true) {
          val read = source.read(buffer)
          if (read < 0) break
          currentCoroutineContext().ensureActive()
          copied += read
          require(copied <= maxBytes) { "The picked audio is bigger than $maxBytes bytes" }
          destination.write(buffer, 0, read)
        }
      }
    }
  }

  /**
   * Reads how long [file] lasts from its metadata.
   *
   * @throws IllegalArgumentException if it has no duration, or a zero one: it isn't playable audio.
   */
  private fun readDurationMs(file: File): Long {
    val retriever = MediaMetadataRetriever()
    try {
      retriever.setDataSource(file.path)
      val durationMs =
          retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
      require(durationMs != null && durationMs > 0) { "The picked audio has no duration" }
      return durationMs
    } finally {
      retriever.release()
    }
  }

  /**
   * Empties the folder on the first call only, to delete the files of a previous run of the app,
   * and creates it. It takes the lock, so two first reads at once don't both empty it.
   */
  private fun prepareDirectory(): File =
      synchronized(lock) {
        if (!leftoversDeleted) {
          directory.listFiles()?.forEach { it.delete() }
          leftoversDeleted = true
        }
        directory.apply { mkdirs() }
      }

  private companion object {
    const val PICKED_DIR = "audio/picked"
    const val BUFFER_BYTES = 64 * 1024

    /** The type an app gives when it doesn't know the file's type. */
    const val UNKNOWN_TYPE = "application/octet-stream"

    /** The MIME types .m4a files are given, depending on the app that provides them. */
    val M4A_TYPES = setOf("audio/mp4", "audio/x-m4a", "audio/m4a")
  }
}
