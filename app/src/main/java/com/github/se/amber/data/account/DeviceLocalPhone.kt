// Written by Alaabrine, with assistance from Cursor.

package com.github.se.amber.data.account

import com.github.se.amber.data.media.image.MediaImageMemory
import com.github.se.amber.model.media.MediaRepository
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Clears the media this device keeps for the signed-in user. Account deletion uses it, and sign-out
 * will use the same place, so the steps are not copied into each flow.
 *
 * @param mediaRepository Holds the media kept on the phone and their pending sends.
 * @param imageMemory Holds the decoded pictures; clearLocalMedia doesn't clear these.
 * @param cacheDir The application cache, where the upload screens leave their temporary files.
 * @param ioDispatcher The dispatcher used for file operations.
 */
class DeviceLocalPhone(
    private val mediaRepository: MediaRepository,
    private val imageMemory: MediaImageMemory,
    private val cacheDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

  /**
   * Cancels the send jobs and clears the outbox and media cache, then clears the decoded pictures,
   * then deletes the temporary upload files. Every step is still attempted when an earlier one
   * fails.
   *
   * @throws Exception if something couldn't be cleared: it may still be on the device. The first
   *   failure is thrown, and any later ones are added to it. Clearing can run again.
   */
  suspend fun clear() {
    val failures =
        listOf<suspend () -> Unit>(
                { mediaRepository.clearLocalMedia() },
                { imageMemory.clearAll() },
                { deleteTemporaryFiles() },
            )
            .mapNotNull { step -> failureOf(step) }
    failures.firstOrNull()?.let { first ->
      failures.drop(1).forEach(first::addSuppressed)
      throw first
    }
  }

  /**
   * Deletes the files SelectedPictureReaderAndroid copies picked pictures to, camera photos, and
   * voice memos that weren't uploaded: the recorder and the audio reader leave them as .m4a files
   * anywhere in the cache.
   */
  private suspend fun deleteTemporaryFiles() =
      withContext(ioDispatcher) {
        val picked = cacheDir.listFiles { file ->
          file.name.startsWith(PICKED_PREFIX) && file.name.endsWith(PICTURE_SUFFIX)
        }
        val memos = cacheDir.walkTopDown().filter { it.isFile && it.extension == MEMO_EXTENSION }
        val captured = File(cacheDir, CAPTURED_DIR)
        // map, not all: a file that can't be deleted must not keep the next ones
        val allDeleted =
            (picked.orEmpty().toList() + memos).map { it.delete() }.all { it } and
                captured.deleteRecursively()
        if (!allDeleted) throw IOException("Some temporary files could not be deleted")
      }

  private suspend fun failureOf(step: suspend () -> Unit): Exception? =
      try {
        step()
        null
      } catch (exception: Exception) {
        exception
      }

  private companion object {
    const val PICKED_PREFIX = "picked_"
    const val PICTURE_SUFFIX = ".jpg"
    const val CAPTURED_DIR = "captured_pictures"
    const val MEMO_EXTENSION = "m4a"
  }
}
