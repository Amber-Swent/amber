// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

import com.github.se.amber.model.circle.CareCircle
import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * The media of the care circles: their metadata ([MediaItem]) and their files. The only media
 * service ViewModels call: they receive it through their constructor, and their factory takes the
 * app's instance from [MediaRepositoryProvider]. Being an interface, it can be replaced by a fake
 * in tests.
 *
 * A media added on this phone first waits in the outbox, pending its send to Firebase: until it is
 * sent, it is listed by [observeOutbox], not by [observeApprovedMedia] or [observeMediaShowing].
 * During its send, its Firestore document is written a moment before it leaves the outbox, so for
 * that moment it is in both. A screen showing both kinds uses [observeCircleMedia], which lists it
 * once.
 *
 * Calls that may touch a file take the whole [CareCircle], since its files live in its own Storage
 * bucket ([CareCircle.storageBucket]); those that need the bucket throw [IllegalArgumentException]
 * while it isn't assigned yet. A [MediaItem.Text] has no file: its content is its text.
 */
interface MediaRepository {

  /** A new mediaId for [circleId], generated on the device, so it also works offline. */
  fun newMediaId(circleId: String): String

  /**
   * The media of [circleId] the patient may see ([MediaStatus.APPROVED]), newest first. Emits again
   * whenever they change.
   */
  fun observeApprovedMedia(circleId: String): Flow<List<MediaItem>>

  /**
   * The media of [circleId] whose [MediaItem.shownIds] contain [shownId] (a member uid or a
   * personId of [CareCircle.people]), newest first. Emits again whenever they change.
   */
  fun observeMediaShowing(circleId: String, shownId: String): Flow<List<MediaItem>>

  /**
   * The media of [circleId] a gallery shows: first those added on this phone and not sent yet
   * ([observeOutbox]), then those in Firebase ([observeApprovedMedia]), each part newest first.
   * Emits again whenever either part changes.
   *
   * A media being sent is listed once, as in Firebase, as soon as its document is there, so a list
   * keyed by mediaId never sees the same key twice.
   */
  fun observeCircleMedia(circleId: String): Flow<List<CircleMedia>>

  /**
   * The media [mediaId] of [circleId], or null if it doesn't exist (e.g. it was deleted) or is
   * still in the outbox.
   */
  suspend fun getMedia(circleId: String, mediaId: String): MediaItem?

  /**
   * Adds [item] to [circle]. Returns as soon as it is saved in the outbox on the phone, even
   * offline: this doesn't mean it is in Firebase yet. It is sent later in the background, its file
   * first, then its metadata, so a Firestore document never points at a missing Storage file. If
   * sending keeps failing, it ends [SendState.FAILED] (see [observeOutbox]).
   *
   * [item]'s id comes from [newMediaId]. [file] holds its bytes:
   * - for a [MediaItem.Picture] or a [MediaItem.Audio], required, and [item]'s storagePath must be
   *   inside [circle];
   * - for a [MediaItem.Text], null: its content is its text, and its storagePath stays empty.
   *
   * [file] is moved into the outbox: pass a file the app owns, and don't use it afterwards.
   *
   * @throws IllegalArgumentException if [file] is null or doesn't exist for a media that needs one,
   *   or is given for a [MediaItem.Text]; if [item]'s storagePath doesn't follow the rule above; or
   *   if [item] has a file and [circle]'s bucket isn't assigned yet.
   * @throws java.io.IOException if the media can't be saved on the phone (e.g. disk full).
   */
  suspend fun addMedia(circle: CareCircle, item: MediaItem, file: File?)

  /**
   * The media of [circleId] added on this phone and not sent yet, oldest first. Emits again
   * whenever one is added, changes [SendState] or leaves the outbox (once sent or discarded).
   */
  fun observeOutbox(circleId: String): Flow<List<OutboxEntry>>

  /**
   * Puts the media [mediaId] of [circleId] whose send failed ([SendState.FAILED]) back to
   * [SendState.WAITING] and schedules its send again, with a new series of attempts; returns
   * without waiting for it. No effect if it isn't in the outbox or hasn't failed.
   */
  suspend fun retrySend(circleId: String, mediaId: String)

  /**
   * Removes the media [mediaId] of [circleId] from the outbox, with its file if it has one, and
   * cancels its send. No effect if it isn't in the outbox.
   *
   * The media is then lost, so ask the user first. Except if its send was already under way
   * ([SendState.SENDING]): cancelling may come too late, and the media may still reach Firebase. It
   * then appears in [observeApprovedMedia] and can be removed with [deleteMedia].
   *
   * @throws java.io.IOException if its file can't be deleted.
   */
  suspend fun discardUnsent(circleId: String, mediaId: String)

  /**
   * Saves [item]'s new metadata in [circleId]; its file doesn't change. Returns once Firestore
   * confirms the write, so it needs a connection. Not defined yet for a media still in the outbox.
   *
   * @throws Exception if Firebase refuses it (only [item]'s author may edit it) or can't be
   *   reached.
   */
  suspend fun updateMedia(circleId: String, item: MediaItem)

  /**
   * Deletes [item] from [circle]: its metadata first, then its file and its cached copy if it has a
   * file, so a Firestore document never points at a missing Storage file. Needs a connection. Not
   * defined yet for a media still in the outbox.
   *
   * @throws IllegalArgumentException if [item] has a file and either isn't a media of [circle] or
   *   [circle]'s bucket isn't assigned yet.
   * @throws Exception if Firebase refuses it (only [item]'s author may delete it) or can't be
   *   reached.
   */
  suspend fun deleteMedia(circle: CareCircle, item: MediaItem)

  /**
   * The local copy of [item]'s file: the outbox's one if it isn't sent yet, else the cached one,
   * downloaded from [circle]'s bucket only if it isn't cached, so media already loaded also opens
   * offline. Open it right away rather than keeping the [File]: it may be evicted later to make
   * room, and an unsent media's file moves from the outbox to the cache once it is sent. A file
   * already opened stays readable after either. If it is gone when opened (moved or evicted at that
   * moment), call getFile again: it returns where the file is now.
   *
   * @throws IllegalArgumentException if [item] is a [MediaItem.Text] (it has no file), isn't a
   *   media of [circle], or its file must be downloaded while [circle]'s bucket isn't assigned yet.
   * @throws Exception if its file must be downloaded and the download fails (e.g. offline).
   */
  suspend fun getFile(circle: CareCircle, item: MediaItem): File

  /**
   * Deletes the media of [circleId] kept on the phone: its cached files and its unsent media, whose
   * sends are cancelled. Nothing already in Firebase is deleted. Call it when the user leaves that
   * circle.
   *
   * Unsent media is lost (except a send already under way, as for [discardUnsent]), so warn the
   * user first if [observeOutbox] isn't empty.
   *
   * @throws IllegalArgumentException if [circleId] isn't a valid circle id (blank, or containing
   *   '/' or '_').
   * @throws java.io.IOException if some files can't be deleted: that circle's private media may
   *   then still be on the device, so report it rather than ignore it.
   */
  suspend fun clearLocalMedia(circleId: String)

  /**
   * Deletes all media kept on the phone, for every circle, like `clearLocalMedia(circleId)`. Call
   * it on sign-out.
   *
   * @throws java.io.IOException if some files can't be deleted: private media may then still be on
   *   the device, so report it rather than ignore it.
   */
  suspend fun clearLocalMedia()
}
