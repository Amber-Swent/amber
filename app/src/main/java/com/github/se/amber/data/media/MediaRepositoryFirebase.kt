// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import android.util.Log
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.circle.hasStorageBucket
import com.github.se.amber.model.media.CircleMedia
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MediaStatus
import com.github.se.amber.model.media.OutboxEntry
import com.github.se.amber.model.media.isMediaOf
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import java.io.File
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.tasks.await

/**
 * The app's [MediaRepository], keeping each media in three places:
 * - its details (a [MediaItem]: author, description, date, people shown...) in Firestore, the
 *   database, as one document at `careCircles/{circleId}/media/{mediaId}`;
 * - its file (photo or voice memo; a text has none) in Firebase Storage, in the circle's own
 *   bucket, through [fileStorage];
 * - a copy of each file already downloaded on this phone, in [cache], so it also opens offline.
 *
 * It is also the [MediaSender]: the background job calls it to send the media added on this phone.
 *
 * A media document holds the fields of [toMap] plus an `id` field, equal to the document's name:
 * Firebase's security rules refuse to create a document without it. Once created, only its
 * [EDITABLE_FIELDS] can change.
 *
 * Done so far: everything about media already in Firebase (new ids, lists, reading, editing,
 * deleting) and the downloaded copies ([getFile], the clears). Adding and sending media (the
 * outbox) comes next: those methods throw [NotImplementedError] until then.
 */
class MediaRepositoryFirebase(
    private val fileStorage: MediaFileStorage,
    private val cache: MediaFileCache,
    private val firestore: FirebaseFirestore,
) : MediaRepository, MediaSender {

  override fun newMediaId(circleId: String): String = mediaCollection(circleId).document().id

  override fun observeApprovedMedia(circleId: String): Flow<List<MediaItem>> =
      mediaCollection(circleId)
          .whereEqualTo(STATUS_FIELD, MediaStatus.APPROVED.name)
          .orderBy(CREATED_AT_FIELD, Query.Direction.DESCENDING)
          .observeItems()

  override fun observeMediaShowing(circleId: String, shownId: String): Flow<List<MediaItem>> =
      mediaCollection(circleId)
          .whereArrayContains(SHOWN_IDS_FIELD, shownId)
          .orderBy(CREATED_AT_FIELD, Query.Direction.DESCENDING)
          .observeItems()

  override suspend fun getMedia(circleId: String, mediaId: String): MediaItem? =
      mediaDocument(circleId, mediaId).get().await().toMediaItem()

  override suspend fun addMedia(circle: CareCircle, item: MediaItem, file: File?): Unit =
      TODO(OUTBOX_TODO)

  override fun observeOutbox(circleId: String): Flow<List<OutboxEntry>> = TODO(OUTBOX_TODO)

  override fun observeCircleMedia(circleId: String): Flow<List<CircleMedia>> = TODO(OUTBOX_TODO)

  override suspend fun retrySend(circleId: String, mediaId: String): Unit = TODO(OUTBOX_TODO)

  override suspend fun discardUnsent(circleId: String, mediaId: String): Unit = TODO(OUTBOX_TODO)

  override suspend fun send(circleId: String, mediaId: String): SendResult = TODO(OUTBOX_TODO)

  /**
   * Done in a Firestore transaction: a group of reads and writes that Firebase applies together or
   * not at all, and only with a connection. Here it checks that the document exists, then updates
   * it. Without the transaction, an update made offline would be kept on the phone and sent once
   * back online, and the call would wait until then, however long that takes; with it, the call
   * fails offline instead.
   *
   * A media that no longer exists (e.g. deleted from another phone) fails with NOT_FOUND: it is
   * never created again. Only the [EDITABLE_FIELDS] of [item] are written, over the existing ones
   * (an `update`, not a replacement): its author, file, type, status and creation time are left as
   * stored, even if [item] is an older copy, and the security rules refuse any change to them.
   */
  override suspend fun updateMedia(circleId: String, item: MediaItem) {
    val document = mediaDocument(circleId, item.id)
    firestore
        .runTransaction { transaction ->
          if (!transaction.get(document).exists()) {
            throw FirebaseFirestoreException(
                "Media ${item.id} of care circle $circleId doesn't exist",
                FirebaseFirestoreException.Code.NOT_FOUND,
            )
          }
          transaction.update(document, item.toDocument().filterKeys { it in EDITABLE_FIELDS })
        }
        .await()
  }

  /**
   * Three steps, in this order: the document, the file in Storage, then the copy on this phone.
   * With the document deleted first, Firebase never keeps a media document whose file is gone: if
   * the call stops halfway, what is left is a file no document points at, never the reverse.
   *
   * What could make a step refuse the call (a media of another circle, a circle without a bucket
   * yet) is checked before the first step, so a refused call deletes nothing.
   *
   * The document is deleted in a transaction, as in [updateMedia], so that offline the call fails.
   * A plain delete would remove the media from this phone's lists right away, but reach Firebase
   * only once back online, and the call would wait until then.
   *
   * If a step fails, calling it again finishes the job: a document already deleted is skipped
   * (Firebase would refuse to delete it: its rules check the document's author, and there is none),
   * and a file already deleted isn't an error ([MediaFileStorage.delete]).
   */
  override suspend fun deleteMedia(circle: CareCircle, item: MediaItem) {
    val hasFile = item !is MediaItem.Text
    if (hasFile) {
      requireMediaOf(circle, item)
      // fileStorage.delete checks it too, but only after the document is deleted: too late
      require(circle.hasStorageBucket) { "Storage is not ready for care circle ${circle.id}" }
    }
    val document = mediaDocument(circle.id, item.id)
    firestore
        .runTransaction { transaction ->
          // missing: an earlier call deleted it, and the file is deleted below. Not yet true for a
          // media still in the outbox (see MediaRepository.deleteMedia): its file may already be
          // uploaded while its document isn't written yet, so this would delete a send under way
          if (transaction.get(document).exists()) transaction.delete(document)
        }
        .await()
    if (hasFile) {
      fileStorage.delete(circle, item.storagePath)
      cache.evict(item.storagePath)
    }
  }

  override suspend fun getFile(circle: CareCircle, item: MediaItem): File {
    requireMediaOf(circle, item)
    return cache.getFile(item.storagePath) { fileStorage.download(circle, item.storagePath, it) }
  }

  // only the cache exists so far: the outbox and its sends will be cleared here too
  override suspend fun clearLocalMedia(circleId: String) = cache.clearCircle(circleId)

  override suspend fun clearLocalMedia() = cache.clear()

  private fun mediaCollection(circleId: String): CollectionReference =
      firestore.collection(CARE_CIRCLES_COLLECTION).document(circleId).collection(MEDIA_COLLECTION)

  private fun mediaDocument(circleId: String, mediaId: String): DocumentReference =
      mediaCollection(circleId).document(mediaId)

  /**
   * Watches the media this query finds: emits the list at once, then again each time one of them is
   * added, changed or removed, until the collector stops. A collector slower than the changes only
   * gets the latest list; older ones are dropped, being out of date anyway.
   *
   * If Firebase refuses to answer (e.g. permission denied after the user left the circle), the flow
   * ends with that error, so the collector can catch it. Going offline doesn't end it: the list
   * then comes from what this phone has already loaded. Firestore's ready-made `Query.snapshots()`
   * isn't used: it ends with a cancellation instead, which a ViewModel's coroutine treats as a
   * normal stop, so the error would go unnoticed.
   */
  private fun Query.observeItems(): Flow<List<MediaItem>> {
    val items = callbackFlow {
      val registration = addSnapshotListener { snapshot, error ->
        when {
          error != null -> close(error)
          snapshot != null -> trySend(snapshot.documents.mapNotNull { it.toMediaItem() })
        }
      }
      awaitClose { registration.remove() }
    }
    return items.conflate()
  }

  /**
   * The media stored in this document; null if there is no document, or if this app version can't
   * read it (e.g. a kind of media added by a newer version), which is logged.
   */
  private fun DocumentSnapshot.toMediaItem(): MediaItem? {
    val data = data ?: return null
    return mediaItemFromMap(id, data).also {
      if (it == null) Log.w(TAG, "Couldn't read media ${reference.path}: unknown type or bad field")
    }
  }

  /**
   * The fields written to this media's document: those of [toMap], plus the `id` the rules check.
   */
  private fun MediaItem.toDocument(): Map<String, Any?> = toMap() + (ID_FIELD to id)

  private fun requireMediaOf(circle: CareCircle, item: MediaItem) {
    // a file of another circle would be looked for in this circle's bucket, where its rules refuse
    // it, and its copy on the phone would be missed by clearLocalMedia(circle.id)
    require(item.isMediaOf(circle.id)) {
      "Media ${item.id} (${item.storagePath}) isn't a media of care circle ${circle.id}"
    }
  }

  private companion object {
    const val TAG = "MediaRepositoryFirebase"
    const val OUTBOX_TODO = "The media outbox is not implemented yet"
    const val CARE_CIRCLES_COLLECTION = "careCircles"
    const val MEDIA_COLLECTION = "media"

    // field names, as written by toMap()
    const val ID_FIELD = "id"
    const val STATUS_FIELD = "status"
    const val CREATED_AT_FIELD = "createdAt"
    const val SHOWN_IDS_FIELD = "shownIds"

    /**
     * The fields of a media document its author may change, the same as in the security rules
     * (`onlyEditableMediaFieldsChanged`). A type only has some of them: `text` for a text,
     * `transcript` for an audio.
     */
    val EDITABLE_FIELDS =
        setOf("description", "date", "location", "shownIds", "authorizedIds", "text", "transcript")
  }
}
