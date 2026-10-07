// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

import java.io.File

/**
 * Access to the media files in remote storage (the bytes behind [MediaItem.storagePath]). Being an
 * interface, it can be replaced by a fake in tests.
 *
 * Get the app's instance from [MediaStorageRepositoryProvider]. Only downloads exist for now;
 * uploads and deletions will be added with the media repository.
 */
interface MediaStorageRepository {
  /**
   * Downloads the remote file at [storagePath] into [destination], overwriting its content.
   *
   * @throws Exception if the download fails (e.g. offline, or no file at [storagePath]);
   *   [destination] may then hold a partial file.
   */
  suspend fun downloadToFile(storagePath: String, destination: File)
}
