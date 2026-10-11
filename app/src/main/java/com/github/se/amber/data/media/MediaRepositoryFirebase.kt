// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * [MediaRepository] backed by Firebase: metadata in Firestore, files in each circle's Storage
 * bucket through [fileStorage], with local copies kept in [cache].
 *
 * Only the files are implemented so far ([getFile] and the cache clears). The metadata, upload and
 * deletion methods throw [NotImplementedError] until the Firestore part is added.
 */
class MediaRepositoryFirebase(
    private val fileStorage: MediaFileStorage,
    private val cache: MediaFileCache,
) : MediaRepository {

  override fun newMediaId(circleId: String): String = TODO(FIRESTORE_TODO)

  override fun observeApprovedMedia(circleId: String): Flow<List<MediaItem>> = TODO(FIRESTORE_TODO)

  override fun observeMediaShowing(circleId: String, shownId: String): Flow<List<MediaItem>> =
      TODO(FIRESTORE_TODO)

  override suspend fun getMedia(circleId: String, mediaId: String): MediaItem? =
      TODO(FIRESTORE_TODO)

  override suspend fun addMedia(circle: CareCircle, item: MediaItem, file: File): Unit =
      TODO(FIRESTORE_TODO)

  override suspend fun updateMedia(circleId: String, item: MediaItem): Unit = TODO(FIRESTORE_TODO)

  override suspend fun deleteMedia(circle: CareCircle, item: MediaItem): Unit = TODO(FIRESTORE_TODO)

  override suspend fun getFile(circle: CareCircle, item: MediaItem): File {
    // a file of another circle would be downloaded from the wrong bucket (and refused by its
    // rules), and clearCachedMedia(circle.id) would then miss its cached copy
    require(item.storagePath.startsWith("careCircles/${circle.id}/")) {
      "Media ${item.id} (${item.storagePath}) isn't a media of care circle ${circle.id}"
    }
    return cache.getFile(item.storagePath) {
      fileStorage.downloadToFile(circle, item.storagePath, it)
    }
  }

  override suspend fun clearCachedMedia(circleId: String) = cache.clearCircle(circleId)

  override suspend fun clearCachedMedia() = cache.clear()

  private companion object {
    const val FIRESTORE_TODO = "Media metadata in Firestore is not implemented yet"
  }
}
