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
 * Every call needs the circle's bucket and throws [IllegalArgumentException] while it isn't
 * assigned yet: [MediaRepository] passes that error on as its own, so a fake should throw it too.
 *
 * Used by [MediaRepositoryFirebase] only, never by ViewModels: it passes its downloads to
 * [MediaFileCache.getFile], deletes the file of a deleted media, and will upload a media's file
 * when sending it.
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
  suspend fun download(circle: CareCircle, storagePath: String, destination: File)

  /**
   * Uploads [file], the file of [item], to its [MediaItem.storagePath] in [circle]'s bucket,
   * replacing any file already there. The upload carries what the Storage security rules check: the
   * file's type, taken from the path's extension, and the item's [MediaItem.authorId], which must
   * be the signed-in user.
   *
   * Uploading again to the same path only replaces the file, so a caller unsure whether an earlier
   * upload finished (e.g. a send retried after the app was killed) can just upload again.
   *
   * @throws IllegalArgumentException before anything is sent, if [item] has no file (a
   *   [MediaItem.Text]), has a path whose extension isn't .jpg, .png or .m4a, or isn't a media of
   *   [circle]; if [file] is empty or over 25 MiB; or if [circle]'s bucket isn't assigned yet. The
   *   Storage security rules would refuse such an upload, and retrying it would never help.
   * @throws Exception if the upload fails (e.g. offline, or permission denied); the remote file may
   *   then be unchanged, missing, or already replaced by [file]: uploading again is safe.
   */
  suspend fun upload(circle: CareCircle, item: MediaItem, file: File)

  /**
   * Deletes the file at [storagePath] in [circle]'s bucket. Returns normally if there is no file
   * there, so deleting twice, or deleting a file whose upload never happened, isn't an error: the
   * Storage security rules let any member of [circle] delete a missing file, and Firebase then
   * answers "not found", which is ignored.
   *
   * @throws IllegalArgumentException if [circle]'s bucket isn't assigned yet.
   * @throws Exception if the deletion fails for another reason (e.g. offline, or permission
   *   denied).
   */
  suspend fun delete(circle: CareCircle, storagePath: String)
}
