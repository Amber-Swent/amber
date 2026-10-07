// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import java.io.File

/**
 * Access to the media files in remote storage (the bytes behind [MediaItem.storagePath]). Each care
 * circle keeps its files in its own bucket, so every call names the circle the file belongs to.
 * Being an interface, it can be replaced by a fake in tests.
 *
 * Used by the media repository only, never by ViewModels; [MediaFileCache] fills itself through it.
 * Only downloads exist for now; uploads and deletions will be added with the media repository.
 */
interface MediaFileStorage {
  /**
   * Downloads the file at [storagePath] in [circle]'s bucket into [destination], overwriting its
   * content.
   *
   * @throws IllegalArgumentException if [circle]'s bucket isn't assigned yet.
   * @throws Exception if the download fails (e.g. offline, or no file at [storagePath]);
   *   [destination] may then hold a partial file.
   */
  suspend fun downloadToFile(circle: CareCircle, storagePath: String, destination: File)
}
