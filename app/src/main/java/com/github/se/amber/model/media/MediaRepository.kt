// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

import com.github.se.amber.model.circle.CareCircle
import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * The media of the care circles: their metadata ([MediaItem]) and their files. The only media class
 * ViewModels use: they receive it through their constructor, and their factory takes the app's
 * instance from [MediaRepositoryProvider]. Being an interface, it can be replaced by a fake in
 * tests.
 *
 * Calls that touch a file take the whole [CareCircle], since its files live in its own Storage
 * bucket ([CareCircle.storageBucket]); they throw [IllegalArgumentException] while that bucket
 * isn't assigned yet.
 */
interface MediaRepository {

  /** A new mediaId for [circleId], generated on the device, so it also works offline. */
  fun newMediaId(circleId: String): String

  /**
   * The media of [circleId] the patient may see (status APPROVED), newest first. Emits again
   * whenever they change.
   */
  fun observeApprovedMedia(circleId: String): Flow<List<MediaItem>>

  /**
   * The media of [circleId] crediting [shownId] (a member uid or a personId of
   * [CareCircle.people]), newest first. Emits again whenever they change.
   */
  fun observeMediaShowing(circleId: String, shownId: String): Flow<List<MediaItem>>

  /** The media [mediaId] of [circleId], or null if it doesn't exist (e.g. it was deleted). */
  suspend fun getMedia(circleId: String, mediaId: String): MediaItem?

  /**
   * Adds [item] to [circle], with [file] as its bytes: the file is uploaded first, then the
   * metadata is written, so a [MediaItem] never points at a missing file. [item]'s id comes from
   * [newMediaId]. [file] is moved into the local cache, so it never needs to be downloaded.
   */
  suspend fun addMedia(circle: CareCircle, item: MediaItem, file: File)

  /** Saves [item]'s new metadata in [circleId]; its file doesn't change. Only its author may. */
  suspend fun updateMedia(circleId: String, item: MediaItem)

  /** Deletes [item] from [circle]: its metadata, its file and its cached copy. */
  suspend fun deleteMedia(circle: CareCircle, item: MediaItem)

  /**
   * The local copy of [item]'s file, downloaded from [circle]'s bucket only if it isn't cached, so
   * media already loaded also opens offline. Open it right away rather than keeping the [File]: it
   * may be evicted later to make room.
   *
   * @throws IllegalArgumentException if [item] isn't a media of [circle].
   * @throws Exception if the file isn't cached and the download fails (e.g. offline).
   */
  suspend fun getFile(circle: CareCircle, item: MediaItem): File

  /** Deletes the cached files of [circleId]. Call it when the user leaves that circle. */
  suspend fun clearCachedMedia(circleId: String)

  /** Deletes the cached files of every circle. Call it on sign-out. */
  suspend fun clearCachedMedia()
}
