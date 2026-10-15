// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import android.net.Uri
import com.github.se.amber.data.storage.CircleStorageProvider
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageTask
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await

/**
 * [MediaFileStorage] backed by Firebase Storage, where a media file lives at its
 * [MediaItem.storagePath] in its circle's bucket. Cancelling the calling coroutine also cancels a
 * download or an upload. A deletion can't be cancelled once requested: cancelling stops the wait,
 * but the file may still be deleted.
 *
 * @param storageFor the Firebase Storage instance of a circle's bucket; the app uses
 *   [CircleStorageProvider.forCircle], tests pass a mock.
 */
class MediaFileStorageFirebase(
    private val storageFor: (CareCircle) -> FirebaseStorage = CircleStorageProvider::forCircle,
) : MediaFileStorage {

  override suspend fun download(circle: CareCircle, storagePath: String, destination: File) {
    val storage = storageFor(circle).apply { maxDownloadRetryTimeMillis = MAX_DOWNLOAD_RETRY_MS }
    storage.reference.child(storagePath).getFile(destination).awaitOrCancel("Download", storagePath)
  }

  override suspend fun upload(circle: CareCircle, item: MediaItem, file: File) {
    // the rules would refuse it with "permission denied", which a send could retry forever
    val contentType = requireUploadable(circle, item, file)
    val metadata =
        StorageMetadata.Builder()
            .setContentType(contentType)
            .setCustomMetadata(AUTHOR_ID_KEY, item.authorId)
            .build()
    // set before putFile: the upload task reads it when it is created
    val storage = storageFor(circle).apply { maxUploadRetryTimeMillis = MAX_UPLOAD_RETRY_MS }
    storage.reference
        .child(item.storagePath)
        .putFile(Uri.fromFile(file), metadata)
        .awaitOrCancel("Upload", item.storagePath)
  }

  override suspend fun delete(circle: CareCircle, storagePath: String) {
    // Firebase limits a deletion's retries with the download setting, not an upload or operation
    // one; set here too, or an offline deletion would wait for Firebase's default 10 minutes
    val storage = storageFor(circle).apply { maxDownloadRetryTimeMillis = MAX_DOWNLOAD_RETRY_MS }
    try {
      storage.reference.child(storagePath).delete().await()
    } catch (e: StorageException) {
      // already gone (deleted earlier, or never uploaded): what the caller wanted
      if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
    }
  }

  /**
   * Waits for this transfer; if the caller is cancelled, also cancels the transfer, so it doesn't
   * keep writing a file (or sending bytes) nobody wants. [action] and [storagePath] name it in the
   * error thrown in the defensive case below.
   */
  private suspend fun StorageTask<*>.awaitOrCancel(action: String, storagePath: String) {
    try {
      await()
    } catch (e: CancellationException) {
      // await() only stops waiting; also stop the transfer
      cancel()
      // our caller was cancelled: rethrows its cancellation
      currentCoroutineContext().ensureActive()
      // defensive: a task cancelled without an exception. Firebase's own transfers don't do that:
      // when Firebase cancels one, await() throws a StorageException with ERROR_CANCELED instead
      throw IOException("$action of $storagePath was cancelled", e)
    }
  }

  internal companion object {
    /** Custom metadata key holding the uploader's uid, checked by the Storage security rules. */
    const val AUTHOR_ID_KEY = "authorId"

    /**
     * Maximum time Firebase retries a download after a failure (Firebase's default: 10 minutes).
     * Offline, a file that isn't cached then fails after roughly this long instead of leaving the
     * UI waiting, at the cost of failing on short network drops. It limits retries, not the length
     * of a download. Firebase also uses it for deletions, which wait the same way offline.
     *
     * Firebase keeps one Storage instance per bucket, so setting it before each download also
     * applies it to any other code using that circle's instance.
     */
    const val MAX_DOWNLOAD_RETRY_MS = 5_000L

    /**
     * Maximum time Firebase retries an upload after a failure (Firebase's default: 10 minutes).
     * When the connection drops mid-upload, Firebase retries by itself inside the same call, so a
     * short drop goes unnoticed; past this time it gives up and [upload] throws.
     *
     * Uploads run in a background job, which WorkManager stops after 10 minutes. With Firebase's
     * default, a long outage would get the job stopped while still waiting; with one minute, the
     * upload fails first, the send reports a retry, and WorkManager runs it again once back online.
     *
     * Like [MAX_DOWNLOAD_RETRY_MS], it limits retries, not the length of an upload (a slow upload
     * that keeps progressing isn't cut off), and applies to the circle's whole Storage instance.
     */
    const val MAX_UPLOAD_RETRY_MS = 60_000L
  }
}
