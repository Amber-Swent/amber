// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import java.io.File

/**
 * A [MediaFileStorage] without network, for the repository tests. Records each call in [calls], as
 * "download {circleId}:{storagePath}" or "delete {circleId}:{storagePath}"; pass a list shared with
 * other fakes to check the order of everything that happened. A download writes "content of
 * {storagePath}"; a deletion throws [deleteError] instead, when it is set.
 */
class FakeMediaFileStorage(val calls: MutableList<String> = mutableListOf()) : MediaFileStorage {

  /** Thrown by every [delete] while set. */
  var deleteError: Exception? = null

  override suspend fun download(circle: CareCircle, storagePath: String, destination: File) {
    calls += "download ${circle.id}:$storagePath"
    destination.writeText("content of $storagePath")
  }

  override suspend fun upload(circle: CareCircle, item: MediaItem, file: File) =
      error("upload isn't used by the repository yet")

  override suspend fun delete(circle: CareCircle, storagePath: String) {
    deleteError?.let { throw it }
    calls += "delete ${circle.id}:$storagePath"
  }
}
