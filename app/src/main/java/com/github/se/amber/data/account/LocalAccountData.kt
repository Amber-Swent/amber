// Written by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.account

import com.github.se.amber.data.media.image.MediaImageMemory
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.media.MediaRepository
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Everything the app keeps on this device for the signed-in user. */
fun interface LocalAccountData {

  /**
   * Clears it all and ends the session on this device. Leaving the screen doesn't interrupt it: the
   * account is gone, so nothing private may stay behind.
   *
   * @throws Exception if something couldn't be cleared: it may still be on the device. Every step
   *   is still attempted, and clearing can run again.
   */
  suspend fun clear()
}

/**
 * Clears the media and its background sends, the decoded pictures in memory and the temporary
 * pictures and voice memos in [cacheDir], then signs out, which also clears the credential provider
 * state: the local Firebase session would otherwise outlive the deleted account.
 *
 * Firestore's copy of the user's documents goes last, once everything else worked: clearing it
 * needs a stopped Firestore, which the repositories can't use any more, so the app restarts.
 *
 * @param mediaRepository Holds the media kept on the phone and their pending sends.
 * @param authRepository Ends the session.
 * @param imageMemory Holds the decoded pictures; clearLocalMedia doesn't clear these.
 * @param cacheDir The application cache, where the upload screens leave their temporary files.
 * @param firestore Keeps the documents the user read in its local cache.
 * @param restartApp Restarts the app; doesn't return in the real app.
 * @param ioDispatcher The dispatcher used for file operations.
 */
class DeviceLocalAccountData(
    private val mediaRepository: MediaRepository,
    private val authRepository: AuthRepository,
    private val imageMemory: MediaImageMemory,
    private val cacheDir: File,
    private val firestore: FirebaseFirestore,
    private val restartApp: () -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LocalAccountData {

  override suspend fun clear(): Unit =
      withContext(NonCancellable) {
        val failures =
            listOf<suspend () -> Unit>(
                    { mediaRepository.clearLocalMedia() },
                    { imageMemory.clearAll() },
                    { deleteTemporaryFiles() },
                    { authRepository.signOut().getOrThrow() },
                )
                .mapNotNull { step -> failureOf(step) }
        failures.firstOrNull()?.let { first ->
          failures.drop(1).forEach(first::addSuppressed)
          throw first
        }
        try {
          firestore.terminate().await()
          firestore.clearPersistence().await()
          Unit
        } finally {
          restartApp()
        }
      }

  private suspend fun failureOf(step: suspend () -> Unit): Exception? =
      try {
        step()
        null
      } catch (exception: Exception) {
        exception
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

  private companion object {
    const val PICKED_PREFIX = "picked_"
    const val PICTURE_SUFFIX = ".jpg"
    const val CAPTURED_DIR = "captured_pictures"
    const val MEMO_EXTENSION = "m4a"
  }
}
