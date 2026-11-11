// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Alaabrine, with assistance from Cursor.

package com.github.se.amber.data.account

import com.github.se.amber.data.media.image.MediaImageMemory
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.media.MediaRepository
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
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
 * Clears the phone through [DeviceLocalPhone], then signs out, which also clears the credential
 * provider state: the local Firebase session would otherwise outlive the deleted account.
 *
 * Firestore's copy of the user's documents goes last, once everything else worked: clearing it
 * needs a stopped Firestore, which the repositories can't use any more, so the process ends. It
 * only reopens on the first screen once the device is clear and [marker] removed: it would
 * otherwise make every launch fail and relaunch in a loop, and instead the next launch the user
 * makes tries again.
 *
 * @param mediaRepository Holds the media kept on the phone and their pending sends.
 * @param authRepository Ends the session.
 * @param imageMemory Holds the decoded pictures; clearLocalMedia doesn't clear these.
 * @param cacheDir The application cache, where the upload screens leave their temporary files.
 * @param firestore Keeps the documents the user read in its local cache.
 * @param marker Removed once everything is cleared, before the app restarts.
 * @param restartApp Restarts the app; doesn't return in the real app.
 * @param stopApp Ends the process without reopening the app; doesn't return in the real app.
 * @param ioDispatcher The dispatcher used for file operations.
 */
class DeviceLocalAccountData(
    private val mediaRepository: MediaRepository,
    private val authRepository: AuthRepository,
    private val imageMemory: MediaImageMemory,
    private val cacheDir: File,
    private val firestore: FirebaseFirestore,
    private val marker: DeletionMarker,
    private val restartApp: () -> Unit,
    private val stopApp: () -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LocalAccountData {

  private val phone =
      DeviceLocalPhone(
          mediaRepository = mediaRepository,
          imageMemory = imageMemory,
          cacheDir = cacheDir,
          ioDispatcher = ioDispatcher,
      )

  override suspend fun clear(): Unit =
      withContext(NonCancellable) {
        val failures =
            listOf<suspend () -> Unit>(
                    { phone.clear() },
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
          marker.remove()
        } catch (exception: Exception) {
          stopApp()
          throw exception
        }
        restartApp()
      }

  private suspend fun failureOf(step: suspend () -> Unit): Exception? =
      try {
        step()
        null
      } catch (exception: Exception) {
        exception
      }
}
