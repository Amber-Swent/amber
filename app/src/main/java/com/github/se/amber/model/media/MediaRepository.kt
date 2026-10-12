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
   * Adds [item] to [circle], with [file] as its bytes. Returns as soon as both are saved in the
   * outbox on the phone, even offline: this doesn't mean they are in Firebase yet. They are sent
   * later in the background, the file first, then the metadata, so a [MediaItem] never points at a
   * missing file. Until then, the media appears in [observeOutbox], not in [observeApprovedMedia].
   *
   * [item]'s id comes from [newMediaId]. [file] is moved into the outbox: pass a file the app owns,
   * and don't use it afterwards.
   *
   * @throws IllegalArgumentException if [circle]'s bucket isn't assigned yet, [item] isn't a media
   *   of [circle], or [file] doesn't exist.
   * @throws java.io.IOException if the media can't be saved on the phone (e.g. disk full).
   */
  suspend fun addMedia(circle: CareCircle, item: MediaItem, file: File)

  /**
   * The media of [circleId] added on this phone and not sent yet, oldest first. Emits again
   * whenever one is added, changes [SendState] or leaves the outbox (once sent or discarded).
   */
  fun observeOutbox(circleId: String): Flow<List<OutboxEntry>>

  /**
   * Sends again the media [mediaId] of [circleId] whose send failed ([SendState.FAILED]). No effect
   * if it isn't in the outbox or hasn't failed.
   */
  suspend fun retrySend(circleId: String, mediaId: String)

  /**
   * Removes the unsent media [mediaId] of [circleId] from the outbox, with its file, and cancels
   * its send. It is then lost, so ask the user first. No effect if it isn't in the outbox.
   *
   * @throws java.io.IOException if its file can't be deleted.
   */
  suspend fun discardUnsent(circleId: String, mediaId: String)

  /** Saves [item]'s new metadata in [circleId]; its file doesn't change. Only its author may. */
  suspend fun updateMedia(circleId: String, item: MediaItem)

  /**
   * Deletes [item] from [circle]: its metadata, its file and its cached copy. A media not sent yet
   * is just removed from the outbox, like [discardUnsent].
   */
  suspend fun deleteMedia(circle: CareCircle, item: MediaItem)

  /**
   * The local copy of [item]'s file: the outbox's one if it isn't sent yet, else the cached one,
   * downloaded from [circle]'s bucket only if it isn't cached, so media already loaded also opens
   * offline. Open it right away rather than keeping the [File]: it may be evicted later to make
   * room.
   *
   * @throws IllegalArgumentException if [item] isn't a media of [circle].
   * @throws Exception if the file isn't cached and the download fails (e.g. offline).
   */
  suspend fun getFile(circle: CareCircle, item: MediaItem): File

  /**
   * Deletes the media of [circleId] kept on the phone: its cached files and its unsent media, whose
   * sends are cancelled. Call it when the user leaves that circle. Unsent media is lost, so warn
   * the user first if [observeOutbox] isn't empty.
   *
   * @throws java.io.IOException if some files can't be deleted: that circle's private media may
   *   then still be on the device, so report it rather than ignore it.
   */
  suspend fun clearLocalMedia(circleId: String)

  /**
   * Deletes the media of every circle kept on the phone, like the other [clearLocalMedia]. Call it
   * on sign-out.
   *
   * @throws java.io.IOException if some files can't be deleted, like the other [clearLocalMedia].
   */
  suspend fun clearLocalMedia()
}
