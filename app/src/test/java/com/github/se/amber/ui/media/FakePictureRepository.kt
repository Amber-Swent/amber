// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.ui.media

import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.CircleMedia
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.OutboxEntry
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.Flow

/**
 * A [MediaRepository] for the media picture tests: [getFile] runs [onGetFile] and records the id of
 * each media asked for in [requests]; the other methods aren't used by pictures and fail.
 */
class FakePictureRepository(
    @Volatile var onGetFile: suspend (MediaItem) -> File = { error("no file") }
) : MediaRepository {

  // Written on Coil's threads, read by the test thread.
  val requests: MutableList<String> = CopyOnWriteArrayList()

  override suspend fun getFile(circle: CareCircle, item: MediaItem): File {
    requests += item.id
    return onGetFile(item)
  }

  override fun newMediaId(circleId: String): String = unused()

  override fun observeApprovedMedia(circleId: String): Flow<List<MediaItem>> = unused()

  override fun observeMediaShowing(circleId: String, shownId: String): Flow<List<MediaItem>> =
      unused()

  override fun observeCircleMedia(circleId: String): Flow<List<CircleMedia>> = unused()

  override suspend fun getMedia(circleId: String, mediaId: String): MediaItem? = unused()

  override suspend fun addMedia(circle: CareCircle, item: MediaItem, file: File?) = unused()

  override fun observeOutbox(circleId: String): Flow<List<OutboxEntry>> = unused()

  override suspend fun retrySend(circleId: String, mediaId: String) = unused()

  override suspend fun discardUnsent(circleId: String, mediaId: String) = unused()

  override suspend fun updateMedia(circleId: String, item: MediaItem) = unused()

  override suspend fun deleteMedia(circle: CareCircle, item: MediaItem) = unused()

  override suspend fun clearLocalMedia(circleId: String) = unused()

  override suspend fun clearLocalMedia() = unused()

  private fun unused(): Nothing = error("Not used by media pictures")
}
