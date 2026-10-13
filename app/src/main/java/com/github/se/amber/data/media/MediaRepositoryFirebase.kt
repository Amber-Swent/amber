// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.OutboxEntry
import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * [MediaRepository] backed by Firebase: metadata in Firestore, files in each circle's Storage
 * bucket through [fileStorage], with local copies kept in [cache]. Also the [MediaSender] the
 * background send job calls.
 *
 * Only the cache is implemented so far: [getFile] (without the outbox yet) and the clears. The
 * metadata, outbox and send methods throw [NotImplementedError] until the outbox and the Firestore
 * part are added.
 */
class MediaRepositoryFirebase(
    private val fileStorage: MediaFileStorage,
    private val cache: MediaFileCache,
) : MediaRepository, MediaSender {

  override fun newMediaId(circleId: String): String = TODO(FIRESTORE_TODO)

  override fun observeApprovedMedia(circleId: String): Flow<List<MediaItem>> = TODO(FIRESTORE_TODO)

  override fun observeMediaShowing(circleId: String, shownId: String): Flow<List<MediaItem>> =
      TODO(FIRESTORE_TODO)

  override suspend fun getMedia(circleId: String, mediaId: String): MediaItem? =
      TODO(FIRESTORE_TODO)

  override suspend fun addMedia(circle: CareCircle, item: MediaItem, file: File): Unit =
      TODO(FIRESTORE_TODO)

  override fun observeOutbox(circleId: String): Flow<List<OutboxEntry>> = TODO(OUTBOX_TODO)

  override suspend fun retrySend(circleId: String, mediaId: String): Unit = TODO(OUTBOX_TODO)

  override suspend fun discardUnsent(circleId: String, mediaId: String): Unit = TODO(OUTBOX_TODO)

  override suspend fun send(circleId: String, mediaId: String): SendResult = TODO(OUTBOX_TODO)

  override suspend fun updateMedia(circleId: String, item: MediaItem): Unit = TODO(FIRESTORE_TODO)

  override suspend fun deleteMedia(circle: CareCircle, item: MediaItem): Unit = TODO(FIRESTORE_TODO)

  override suspend fun getFile(circle: CareCircle, item: MediaItem): File {
    // a file of another circle would be downloaded from the wrong bucket (and refused by its
    // rules), and clearLocalMedia(circle.id) would then miss its cached copy
    require(item.storagePath.startsWith("careCircles/${circle.id}/")) {
      "Media ${item.id} (${item.storagePath}) isn't a media of care circle ${circle.id}"
    }
    return cache.getFile(item.storagePath) {
      fileStorage.downloadToFile(circle, item.storagePath, it)
    }
  }

  // only the cache exists so far: the outbox and its sends will be cleared here too
  override suspend fun clearLocalMedia(circleId: String) = cache.clearCircle(circleId)

  override suspend fun clearLocalMedia() = cache.clear()

  private companion object {
    const val FIRESTORE_TODO = "Media metadata in Firestore is not implemented yet"
    const val OUTBOX_TODO = "The media outbox is not implemented yet"
  }
}
