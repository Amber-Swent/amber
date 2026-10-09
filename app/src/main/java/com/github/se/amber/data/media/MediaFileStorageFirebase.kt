// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import com.github.se.amber.data.storage.CircleStorageProvider
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await

/**
 * [MediaFileStorage] backed by Firebase Storage, where a media file lives at its
 * [MediaItem.storagePath] in its circle's bucket. Cancelling the calling coroutine also cancels the
 * download.
 *
 * @param storageFor the Firebase Storage instance of a circle's bucket; the app uses
 *   [CircleStorageProvider.forCircle], tests pass a mock.
 */
class MediaFileStorageFirebase(
    private val storageFor: (CareCircle) -> FirebaseStorage = CircleStorageProvider::forCircle,
) : MediaFileStorage {

  override suspend fun downloadToFile(circle: CareCircle, storagePath: String, destination: File) {
    val storage = storageFor(circle).apply { maxDownloadRetryTimeMillis = MAX_DOWNLOAD_RETRY_MS }
    val task = storage.reference.child(storagePath).getFile(destination)
    try {
      task.await()
    } catch (e: CancellationException) {
      // await() only stops waiting; also stop the download so it doesn't keep writing the file
      task.cancel()
      // our caller was cancelled: rethrows its cancellation
      currentCoroutineContext().ensureActive()
      // otherwise Firebase cancelled the download itself, which is a failed download
      throw IOException("Download of $storagePath was cancelled", e)
    }
  }

  internal companion object {
    /**
     * Maximum time Firebase retries a download after a failure (Firebase's default: 10 minutes).
     * Offline, a file that isn't cached then fails after roughly this long instead of leaving the
     * UI waiting, at the cost of failing on short network drops. It limits retries, not the length
     * of a download.
     *
     * Firebase keeps one Storage instance per bucket, so setting it before each download also
     * applies it to any other code using that circle's instance.
     */
    const val MAX_DOWNLOAD_RETRY_MS = 5_000L
  }
}
