// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
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
 * Whether it is an .m4a: a known type can refuse early, and the content is then checked in every
 * case. The type the providing app gives is checked before the copy, so a file it says isn't an
 * .m4a (e.g. an MP3) isn't copied for nothing. Then [MediaMetadataRetriever] reads the type from
 * the file's content, which doesn't depend on its name or on the app, so an MP3 renamed `.m4a` is
 * refused even when the app gives no type.
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
   * Reads the audio on [ioDispatcher] (see [readNow]).
   *
   * If the caller is cancelled once the copy is done, `withContext` throws instead of returning it,
   * and nobody would ever take the file. So the file is deleted here.
   */
  override suspend fun read(uri: String): LocalAudio {
    var audio: LocalAudio? = null
    try {
      return withContext(ioDispatcher) { readNow(uri.toUri()).also { audio = it } }
    } catch (e: CancellationException) {
      audio?.file?.let(::deleteOrWarn)
      throw e
    }
  }

  /**
   * Checks the type the providing app gives, copies the audio into a new file of the folder, then
   * checks its content and reads its duration (see the class documentation). On any failure, the
   * file is deleted and the error becomes an [IllegalArgumentException], as the contract promises
   * (a cancellation stays one). This includes the content resolver's own errors, e.g. a
   * [SecurityException] when the app may no longer read the file.
   */
  private suspend fun readNow(uri: Uri): LocalAudio {
    var file: File? = null
    try {
      val resolver = appContext.contentResolver
      val type = resolver.getType(uri)
      // null or UNKNOWN_TYPE means the app doesn't know the type, so only the content decides
      require(type == null || type == UNKNOWN_TYPE || type in M4A_TYPES) { NOT_M4A }
      file = File.createTempFile("picked_", ".m4a", prepareDirectory())
      copy(resolver, uri, file)
      return LocalAudio(file, readM4aDurationMs(file))
    } catch (e: Exception) {
      file?.let(::deleteOrWarn)
      throw when (e) {
        is CancellationException,
        is IllegalArgumentException -> e
        else -> IllegalArgumentException("Couldn't read the picked audio", e)
      }
    }
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
   * Checks from its content that [file] is an .m4a, and reads how long it lasts from its metadata.
   *
   * @throws IllegalArgumentException if its content isn't an .m4a, or it has no duration or a zero
   *   one: it isn't playable audio.
   */
  private fun readM4aDurationMs(file: File): Long {
    val retriever = MediaMetadataRetriever()
    try {
      retriever.setDataSource(file.path)
      val contentType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
      require(contentType in M4A_TYPES) { NOT_M4A }
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
          directory.listFiles()?.forEach(::deleteOrWarn)
          leftoversDeleted = true
        }
        directory.apply { mkdirs() }
      }

  /**
   * Deletes [file], and logs a warning if it is still there. A file left behind only wastes space,
   * and the first [read] after the next launch of the app deletes it.
   */
  private fun deleteOrWarn(file: File) {
    if (!file.delete() && file.exists()) {
      Log.w(TAG, "Couldn't delete ${file.name}; it is deleted at the next launch")
    }
  }

  internal companion object {
    const val TAG = "AndroidSelectedAudioReader"
    private const val PICKED_DIR = "audio/picked"
    private const val BUFFER_BYTES = 64 * 1024

    private const val NOT_M4A = "The picked audio isn't an .m4a file"

    /** The type an app gives when it doesn't know the file's type. */
    private const val UNKNOWN_TYPE = "application/octet-stream"
  }
}
