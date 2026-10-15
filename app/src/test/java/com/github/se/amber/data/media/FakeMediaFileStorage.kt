// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import java.io.File

/**
 * A [MediaFileStorage] without network, for the repository tests. Records each call that succeeds
 * in [calls], as "download {circleId}:{storagePath}", "upload {bucket}:{storagePath}" or "delete
 * {circleId}:{storagePath}"; pass a list shared with other fakes to check the order of everything
 * that happened. A download writes "content of {storagePath}"; an upload runs [onUpload] first (to
 * fail, or to hang), and a deletion throws [deleteError] instead, when it is set.
 */
class FakeMediaFileStorage(val calls: MutableList<String> = mutableListOf()) : MediaFileStorage {

  /** Thrown by every [delete] while set. */
  var deleteError: Exception? = null

  /** Runs at the start of every [upload]; what it throws, [upload] throws. */
  var onUpload: suspend (File) -> Unit = {}

  /** The content of the last file uploaded, read during its upload. */
  var lastUploadedContent: String? = null

  override suspend fun download(circle: CareCircle, storagePath: String, destination: File) {
    calls += "download ${circle.id}:$storagePath"
    destination.writeText("content of $storagePath")
  }

  override suspend fun upload(circle: CareCircle, item: MediaItem, file: File) {
    onUpload(file)
    lastUploadedContent = file.readText()
    calls += "upload ${circle.storageBucket}:${item.storagePath}"
  }

  override suspend fun delete(circle: CareCircle, storagePath: String) {
    deleteError?.let { throw it }
    calls += "delete ${circle.id}:$storagePath"
  }
}
