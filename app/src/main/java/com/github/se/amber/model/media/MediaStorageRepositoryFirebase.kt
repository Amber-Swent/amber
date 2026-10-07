// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await

/**
 * [MediaStorageRepository] backed by Firebase Storage, where a media file lives at its
 * [MediaItem.storagePath]. Cancelling the calling coroutine also cancels the download.
 *
 * @param storage the Firebase Storage instance; [MediaStorageRepositoryProvider] passes the app's,
 *   tests pass a mock.
 */
class MediaStorageRepositoryFirebase(private val storage: FirebaseStorage) :
    MediaStorageRepository {

  override suspend fun downloadToFile(storagePath: String, destination: File) {
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
}
