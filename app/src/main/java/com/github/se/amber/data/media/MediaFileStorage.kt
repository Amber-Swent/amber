// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Alaabrine, with assistance from Cursor Agent.

package com.github.se.amber.data.media

import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.isMediaOf
import java.io.File

/**
 * Access to the media files in remote storage (the bytes behind [MediaItem.storagePath]). Each care
 * circle keeps its files in its own bucket, so every call names the circle the file belongs to.
 * Being an interface, it can be replaced by a fake in tests.
 *
 * Every call needs the circle's bucket and throws [IllegalArgumentException] while it isn't
 * assigned yet: [MediaRepository] passes that error on as its own, so a fake should throw it too.
 *
 * Used by [MediaRepositoryFirebase] (and later person-photo storage) only, never by ViewModels: it
 * passes its downloads to [MediaFileCache.getFile], deletes the file of a deleted media, and
 * uploads a media's file when sending it.
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
   * Uploads [file] to [storagePath] in [circle]'s bucket. The upload carries what the Storage
   * security rules check: [contentType], and [authorId] as custom metadata, which must be the
   * signed-in user.
   *
   * Callers validate path, type and size for their kind of file (media or person photo) before
   * calling. For a media file, uploading again to the same path replaces it, so a send that is
   * unsure whether an earlier upload finished (for example after the app was killed) can upload
   * again. That does not hold for a person photo: the rules refuse every update under `people/`,
   * and in production a second write to a path that already has a file is an update. If a photo
   * upload lands and the reply is lost, every retry of that same path is permission denied.
   * PersonPhotoRepository should treat "permission denied, and a file is already at the path" as
   * done. The Storage emulator evaluates a second upload as a create, so a rules test cannot show
   * this.
   *
   * @throws IllegalArgumentException if [circle]'s bucket isn't assigned yet.
   * @throws Exception if the upload fails (for example offline, or permission denied). For media
   *   the remote file may then be unchanged, missing, or already replaced by [file], and uploading
   *   again is safe. For a person photo, a file already at the path is left as it is.
   */
  suspend fun upload(
      circle: CareCircle,
      storagePath: String,
      file: File,
      contentType: String,
      authorId: String,
  )

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

/** Content type of each file extension the Storage security rules accept for a media. */
private val CONTENT_TYPES = mapOf("jpg" to "image/jpeg", "png" to "image/png", "m4a" to "audio/mp4")

/** Largest media file the Storage security rules accept, in bytes (25 MiB). */
const val MAX_FILE_BYTES = 25L * 1024 * 1024

/** Largest person photo the Storage security rules accept, in bytes (2 MiB). */
const val MAX_PHOTO_BYTES = 2L * 1024 * 1024

/** Content type the Storage security rules accept for a person photo. */
const val PHOTO_CONTENT_TYPE = "image/jpeg"

/**
 * Checks what the Storage security rules check before accepting [file] as [item]'s file in [circle]
 * (except the uploader and the bucket): [item] has a file, its path is in [circle]'s media folder
 * with a .jpg, .png or .m4a extension, and [file] is between 1 byte and 25 MiB. Used by
 * [MediaRepositoryFirebase.addMedia] and its send path, so a media the rules would refuse never
 * waits in the outbox or is uploaded.
 *
 * @return the content type the rules expect for that extension.
 * @throws IllegalArgumentException if one of these doesn't hold.
 */
internal fun requireUploadable(circle: CareCircle, item: MediaItem, file: File): String {
  require(item !is MediaItem.Text) { "Media ${item.id} is a text: it has no file to upload" }
  val extension = item.storagePath.substringAfterLast('.', missingDelimiterValue = "")
  val contentType =
      requireNotNull(CONTENT_TYPES[extension]) {
        "Media ${item.id} (${item.storagePath}) has no .jpg, .png or .m4a extension"
      }
  require(item.isMediaOf(circle.id)) {
    "Media ${item.id} (${item.storagePath}) isn't a media of care circle ${circle.id}"
  }
  require(file.length() in 1..MAX_FILE_BYTES) { "Media ${item.id}'s file is empty or over 25 MiB" }
  return contentType
}

/**
 * Storage path of the person photo [photoId] for [personId] in [circleId]:
 * `careCircles/{circleId}/people/{personId}/{photoId}.jpg`.
 *
 * An empty [photoId] would be the file name `.jpg`. The rules refuse that (`[^_]+\\.jpg` needs at
 * least one character before the extension), so it is refused here too.
 *
 * @throws IllegalArgumentException if [circleId], [personId] or [photoId] is blank or contains `/`
 *   or `_`.
 */
internal fun personPhotoPath(circleId: String, personId: String, photoId: String): String {
  require(isStorageId(circleId)) {
    "Care circle $circleId must be non-blank and contain no '/' or '_'"
  }
  require(isStorageId(personId)) {
    "Person id $personId must be non-blank and contain no '/' or '_'"
  }
  require(isStorageId(photoId)) { "Photo id $photoId must be non-blank and contain no '/' or '_'" }
  return "careCircles/$circleId/people/$personId/$photoId.jpg"
}

private fun isStorageId(id: String) = id.isNotBlank() && '/' !in id && '_' !in id

/**
 * Checks what the Storage security rules check before accepting [file] as the person photo
 * [photoId] of [personId] in [circle] (except the uploader and the bucket): the ids contain no `_`,
 * and [file] is between 1 byte and 2 MiB. The content type to send with it is [PHOTO_CONTENT_TYPE].
 * For use by PersonPhotoRepository, which should build no path of its own. A retried upload of the
 * same path is not safe: see [MediaFileStorage.upload].
 *
 * @return the storage path the rules accept.
 * @throws IllegalArgumentException if one of these doesn't hold.
 */
internal fun requirePhotoUploadable(
    circle: CareCircle,
    personId: String,
    photoId: String,
    file: File,
): String {
  val path = personPhotoPath(circle.id, personId, photoId)
  require(file.length() in 1..MAX_PHOTO_BYTES) { "Photo at $path is empty or over 2 MiB" }
  return path
}
