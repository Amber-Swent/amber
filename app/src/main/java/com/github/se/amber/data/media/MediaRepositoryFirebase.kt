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
import com.github.se.amber.model.media.SendState
import com.github.se.amber.model.media.isMediaOf
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The app's [MediaRepository], keeping each media in up to four places:
 * - its details (a [MediaItem]: author, description, date, people shown...) in Firestore, the
 *   database, as one document at `careCircles/{circleId}/media/{mediaId}`;
 * - its file (photo or voice memo; a text has none) in Firebase Storage, in the circle's own
 *   bucket, through [fileStorage];
 * - a copy of each file already downloaded or sent from this phone, in [cache], so it also opens
 *   offline;
 * - each media added on this phone and not in Firebase yet, in [outbox], until its send succeeds.
 *
 * It is also the [MediaSender]: the background job, scheduled through [scheduler] for each media
 * added, calls [send] to move it from the outbox to Firebase. [scheduleWaitingSends] schedules
 * again those whose job was lost.
 *
 * A media document holds the fields of [toMap] plus an `id` field, equal to the document's name:
 * Firebase's security rules refuse to create or update a document without it.
 */
class MediaRepositoryFirebase(
    private val fileStorage: MediaFileStorage,
    private val cache: MediaFileCache,
    private val outbox: MediaOutbox,
    private val firestore: FirebaseFirestore,
    private val scheduler: MediaSendScheduler,
) : MediaRepository, MediaSender {

  /**
   * The media this process sent and the Firestore list ([observeApprovedMedia]) may not show yet,
   * by circleId then mediaId, for [observeCircleMedia]. The send creates the document in a
   * transaction, whose write reaches this phone's listeners only when the server sends it back,
   * while the outbox entry is removed at once: without this, the media would be missing from both
   * lists in between. Each media is forgotten once a list shows it in Firestore, when it is
   * deleted, or when its circle's media is cleared; after a restart, Firestore's list already has
   * it.
   */
  private val recentlySent = MutableStateFlow<Map<String, Map<String, MediaItem>>>(emptyMap())

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

  /**
   * A media is listed as sent from the moment its send created its document: from [recentlySent]
   * until the Firestore list shows it, then from that list. Its outbox entry, if still there, is
   * then left out. So during a send, the media goes from one part to the other without ever being
   * missing from both, or in both.
   */
  override fun observeCircleMedia(circleId: String): Flow<List<CircleMedia>> =
      combine(outbox.observe(circleId), recentlySent, observeApprovedMedia(circleId)) {
              entries,
              allRecent,
              inFirestore ->
            val firestoreIds = inFirestore.mapTo(HashSet()) { it.id }
            val recent = allRecent[circleId].orEmpty()
            val shownByFirestore = recent.keys.filter { it in firestoreIds }
            // Trims recentlySent.
            if (shownByFirestore.isNotEmpty()) forgetRecentlySent(circleId, shownByFirestore)
            val notInFirestoreYet = recent.values.filter { it.id !in firestoreIds }
            val sent =
                if (notInFirestoreYet.isEmpty()) inFirestore
                else (notInFirestoreYet + inFirestore).sortedByDescending { it.createdAt }
            val sentIds = firestoreIds + notInFirestoreYet.map { it.id }
            // the outbox lists oldest first
            val unsent =
                entries
                    .asReversed()
                    .filter { it.item.id !in sentIds }
                    .map { CircleMedia(it.item, it.state) }
            unsent + sent.map { CircleMedia(it, sendState = null) }
          }
          // forgetting a recently sent media emits the same list again
          .distinctUntilChanged()

  override suspend fun getMedia(circleId: String, mediaId: String): MediaItem? =
      mediaDocument(circleId, mediaId).get().await().toMediaItem()

  /**
   * What the security rules check is checked here first (as far as the phone can: not that
   * [MediaItem.authorId] is the signed-in user, nor that the user is a member of [circle]), so a
   * media they would refuse never waits in the outbox. [send] doesn't check again: it sees nothing
   * but outbox entries.
   *
   * If scheduling its send fails once the media is saved, the call still returns normally: the
   * media is saved, and [scheduleWaitingSends] schedules it at the next start of the app.
   */
  override suspend fun addMedia(circle: CareCircle, item: MediaItem, file: File?) {
    // when the media is sent, both the Storage and the Firestore rules refuse it unless its authorId
    // is the signed-in user's uid; a blank one could never be sent
    require(item.authorId.isNotBlank()) { "Media ${item.id} has no author" }
    if (item is MediaItem.Text) {
      require(file == null) { "Media ${item.id} is a text: it has no file" }
      require(item.storagePath.isEmpty()) {
        "Text ${item.id} has a storagePath: ${item.storagePath}"
      }
    } else {
      requireNotNull(file) { "Media ${item.id} needs its file" }
      require(file.isFile) { "No file to add at $file" }
      requireUploadable(circle, item, file)
      require(circle.hasStorageBucket) { "Storage is not ready for care circle ${circle.id}" }
    }
    outbox.add(circle, item, file)
    try {
      scheduler.schedule(circle.id, item.id)
    } catch (e: Exception) {
      // throwing would tell the caller nothing was saved, and adding it again would be refused
      Log.w(TAG, "Couldn't schedule the send of media ${item.id}; it waits for the next start", e)
    }
  }

  /**
   * Schedules the send of every [SendState.WAITING] media of the outbox, of every circle; no effect
   * on those already scheduled ([MediaSendScheduler.schedule]). Repairs entries left without a job:
   * the app killed between saving a media and scheduling its send, a job ended while its entry
   * could not be updated, or WorkManager's own data lost. [SendState.FAILED] media are left alone:
   * they are sent again only through [retrySend]. Called by
   * [com.github.se.amber.model.media.MediaRepositoryProvider] once, when it builds the repository.
   *
   * @throws IOException if the outbox can't be read.
   */
  suspend fun scheduleWaitingSends() {
    for ((circleId, mediaId) in outbox.waitingIds()) scheduler.schedule(circleId, mediaId)
  }

  override fun observeOutbox(circleId: String): Flow<List<OutboxEntry>> = outbox.observe(circleId)

  override suspend fun retrySend(circleId: String, mediaId: String) {
    // only the call that moved the entry back schedules: a second tap must not replace that send
    if (outbox.retry(circleId, mediaId)) scheduler.reschedule(circleId, mediaId)
  }

  override suspend fun discardUnsent(circleId: String, mediaId: String) {
    scheduler.cancel(circleId, mediaId)
    outbox.remove(circleId, mediaId)
  }

  /**
   * One attempt, in [MediaSender.ATTEMPT_TIMEOUT_MS] at most: the file's upload, the document
   * created only if missing, the file moved into the cache, the entry removed (see [attempt]). A
   * failure is then recorded in the outbox, outside the time limit ([endFailedAttempt]); a
   * cancellation records nothing and only puts the entry back to [SendState.WAITING].
   *
   * An entry already [SendState.SENDING] when the attempt starts gives [SendResult.RETRY], not
   * [SendResult.DONE]: either another job is sending it (only for a moment, after
   * [MediaSendScheduler.reschedule]), or an earlier attempt couldn't put it back, and it stays so
   * until the next start of the app. Ending the job then would leave the entry without one.
   */
  override suspend fun send(circleId: String, mediaId: String): SendResult =
      try {
        when {
          outbox.startSending(circleId, mediaId) -> attemptAndRecord(circleId, mediaId)
          outbox.get(circleId, mediaId)?.state == SendState.SENDING -> SendResult.RETRY
          else -> SendResult.DONE // gone, or FAILED until the user retries
        }
      } catch (e: CancellationException) {
        // without NonCancellable, the outbox's lock and disk work would throw at once
        withContext(NonCancellable) { resetSendingQuietly(circleId, mediaId, e) }
        throw e
      } catch (e: Exception) {
        // only a failed write of startSending or of the attempt's end gets here: try again later,
        // nothing counted. Left SENDING, the entry would wait for the next start of the app
        Log.w(TAG, "Couldn't update the outbox entry of media $mediaId of $circleId", e)
        resetSendingQuietly(circleId, mediaId, e)
        SendResult.RETRY
      } catch (e: Throwable) {
        // an Error (a bug, or out of memory): not handled, but the entry mustn't stay SENDING
        withContext(NonCancellable) { resetSendingQuietly(circleId, mediaId, e) }
        throw e
      }

  /**
   * Done in a Firestore transaction: a group of reads and writes that Firebase applies together or
   * not at all, and only with a connection. Here it checks that the document exists, then updates
   * it. Without the transaction, an update made offline would be kept on the phone and sent once
   * back online, and the call would wait until then, however long that takes; with it, the call
   * fails offline instead.
   *
   * A media that no longer exists (e.g. deleted from another phone) fails with NOT_FOUND: it is
   * never created again. The fields of [item] are written over the existing ones (an `update`, not
   * a replacement), so a field this app version doesn't know, added by a newer one, is kept.
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
          transaction.update(document, item.toDocument())
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
          if (transaction.get(document).exists()) transaction.delete(document)
        }
        .await()
    forgetRecentlySent(circle.id, listOf(item.id))
    if (hasFile) {
      fileStorage.delete(circle, item.storagePath)
      cache.evict(item.storagePath)
    }
  }

  override suspend fun getFile(circle: CareCircle, item: MediaItem): File {
    require(item !is MediaItem.Text) { "Media ${item.id} is a text: it has no file" }
    requireMediaOf(circle, item)
    // null too once its send moved the file into the cache, where the next line finds it
    outbox.fileOf(circle.id, item.id)?.let {
      return it
    }
    return cache.getFile(item.storagePath) { fileStorage.download(circle, item.storagePath, it) }
  }

  /**
   * The sends are cancelled first, though a send already under way may still finish: what it leaves
   * behind is cleared all the same. The outbox is cleared before the cache, so its files are gone
   * before a send could move them there, and the cache refuses a file moved in after its clear.
   */
  override suspend fun clearLocalMedia(circleId: String) {
    // checked first, so a bad id clears nothing: the outbox and the cache don't refuse the same ids
    require(
        circleId.isNotBlank() &&
            circleId != "." &&
            circleId != ".." &&
            '/' !in circleId &&
            '_' !in circleId
    ) {
      "Invalid circleId: \"$circleId\""
    }
    forgetRecentlySent(circleId)
    runAll(
        { scheduler.cancelCircle(circleId) },
        { outbox.clear(circleId) },
        { cache.clearCircle(circleId) },
    )
  }

  override suspend fun clearLocalMedia() {
    recentlySent.value = emptyMap()
    runAll({ scheduler.cancelAll() }, { outbox.clear() }, { cache.clear() })
  }

  /**
   * Runs the timed attempt at sending [mediaId] of [circleId], whose entry is now
   * [SendState.SENDING], and records its end. The time limit covers only the attempt, so recording
   * a timeout or a failure always has the time it needs.
   */
  private suspend fun attemptAndRecord(circleId: String, mediaId: String): SendResult {
    val result =
        try {
          // withTimeout would throw a TimeoutCancellationException instead of null, easy to take
          // for a stopped run (or to rethrow by mistake)
          withTimeoutOrNull(MediaSender.ATTEMPT_TIMEOUT_MS) { attempt(circleId, mediaId) }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.w(TAG, "Send of media $mediaId of $circleId failed", e)
          return endFailedAttempt(circleId, mediaId, toSendResult(e))
        }
    return when (result) {
      null -> {
        Log.w(TAG, "Send of media $mediaId of $circleId took over the time limit")
        endFailedAttempt(circleId, mediaId, SendResult.RETRY)
      }
      SendResult.FAILED -> endFailedAttempt(circleId, mediaId, SendResult.FAILED)
      else -> result
    }
  }

  /**
   * The steps of one send of [mediaId] of [circleId], each safe to repeat: the file's upload (none
   * for a text), the document created only if missing, the file moved into the cache, the entry
   * removed. Throws the first step's error.
   *
   * @return [SendResult.DONE] once sent, or if the entry was removed meanwhile (e.g. discarded);
   *   [SendResult.FAILED] if the file of a picture or an audio is missing and its document doesn't
   *   exist, so nothing can send it.
   */
  private suspend fun attempt(circleId: String, mediaId: String): SendResult {
    val (item, bucket, file) = outbox.toSend(circleId, mediaId) ?: return SendResult.DONE
    val document = mediaDocument(circleId, mediaId)
    if (item !is MediaItem.Text) {
      if (file == null) return sentWithoutFile(circleId, item, document)
      // the outbox kept the circle's bucket: no need to read the circle
      fileStorage.upload(CareCircle(id = circleId, storageBucket = bucket), item, file)
    }
    createIfMissing(document, item)
    rememberSent(circleId, item)
    if (file != null) moveToCache(item, file)
    outbox.remove(circleId, mediaId)
    return SendResult.DONE
  }

  /**
   * What to do with an entry whose file is gone: if its document exists, an earlier attempt sent it
   * and moved its file into the cache, then couldn't remove the entry (failed or killed); the entry
   * is removed now. Otherwise nothing can send it: [SendResult.FAILED].
   */
  private suspend fun sentWithoutFile(
      circleId: String,
      item: MediaItem,
      document: DocumentReference,
  ): SendResult {
    // in a transaction, so that offline it fails (and is retried) instead of reading this phone's
    // copy, which may not know the document yet
    val sent = firestore.runTransaction { it.get(document).exists() }.await()
    if (!sent) {
      Log.w(TAG, "Media ${item.id} of $circleId lost its file before being sent")
      return SendResult.FAILED
    }
    rememberSent(circleId, item)
    outbox.remove(circleId, item.id)
    return SendResult.DONE
  }

  /** Keeps [item], whose document now exists, in [recentlySent] until a list shows it. */
  private fun rememberSent(circleId: String, item: MediaItem) = recentlySent.update {
    it + (circleId to (it[circleId].orEmpty() + (item.id to item)))
  }

  /** Removes [mediaIds] of [circleId] from [recentlySent], or all of [circleId] if null. */
  private fun forgetRecentlySent(circleId: String, mediaIds: Collection<String>? = null) =
      recentlySent.update { all ->
        val left = if (mediaIds == null) emptyMap() else all[circleId].orEmpty() - mediaIds.toSet()
        if (left.isEmpty()) all - circleId else all + (circleId to left)
      }

  /**
   * Creates [item]'s document unless it already exists: an earlier attempt may have created it, and
   * it may have changed since (e.g. its moderation status, or an edit), which must be kept. In a
   * transaction, which fails offline instead of keeping the write on the phone until later.
   */
  private suspend fun createIfMissing(document: DocumentReference, item: MediaItem) {
    firestore
        .runTransaction { transaction ->
          if (!transaction.get(document).exists()) transaction.set(document, item.toDocument())
        }
        .await()
  }

  /**
   * Moves [file], just sent, into the cache, so the media opens without a download. Failing only
   * costs a download later, so it is logged and ignored.
   */
  private suspend fun moveToCache(item: MediaItem, file: File) {
    try {
      cache.put(item.storagePath, file)
    } catch (e: IOException) {
      Log.w(TAG, "Couldn't keep the file of media ${item.id} in the cache", e)
    }
  }

  /**
   * Ends a failed attempt of [mediaId] of [circleId] in the outbox: [SendResult.RETRY] (a failure
   * that may go away) records a failed attempt, [SendResult.FAILED] (one retrying can't fix) marks
   * the entry failed. Returns the result the attempt gives.
   *
   * If the attempt no longer owns its entry (the outbox changes nothing): [SendResult.DONE] if the
   * entry is gone (e.g. discarded meanwhile), else [SendResult.RETRY] without counting. DONE would
   * end the job and leave the entry, e.g. put back to WAITING by another job, without any.
   */
  private suspend fun endFailedAttempt(
      circleId: String,
      mediaId: String,
      failure: SendResult,
  ): SendResult {
    // a run stopped at the same moment as the failure must not count it: it is a cancellation
    currentCoroutineContext().ensureActive()
    val ended =
        if (failure == SendResult.FAILED) {
          SendResult.FAILED.takeIf { outbox.markFailed(circleId, mediaId) }
        } else {
          outbox.recordFailedAttempt(circleId, mediaId, MediaSender.MAX_FAILED_ATTEMPTS)?.let {
            if (it >= MediaSender.MAX_FAILED_ATTEMPTS) SendResult.FAILED else failure
          }
        }
    return ended ?: if (outbox.get(circleId, mediaId) == null) SendResult.DONE else SendResult.RETRY
  }

  /**
   * After a send of [mediaId] of [circleId] ended by [cause] (a cancellation, an outbox write that
   * failed, or an [Error]), puts its entry back to [SendState.WAITING] if still
   * [SendState.SENDING], nothing counted. If that fails too, it is logged, and the send still ends
   * with [cause]: the entry stays [SendState.SENDING], its job retrying without counting ([send]),
   * until the next start of the app resets it and [scheduleWaitingSends] schedules it.
   */
  private suspend fun resetSendingQuietly(circleId: String, mediaId: String, cause: Throwable) {
    try {
      outbox.resetSending(circleId, mediaId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Couldn't put media $mediaId of $circleId back to waiting after $cause", e)
    }
  }

  /**
   * Whether the failed attempt that threw [error] may succeed later ([SendResult.RETRY]) or never
   * will ([SendResult.FAILED]): Firestore says the user isn't allowed (signed out, no longer a
   * member, or not the media's author), or the media is refused before anything is sent (e.g. an
   * empty file). Everything else may go away: network errors, Firebase unavailable or cancelling a
   * transfer itself (a `StorageException`, not a cancellation of the send), and unexpected errors.
   *
   * Storage's "not authorized" and "not authenticated" may go away too: when Storage can't get the
   * user's token (e.g. on a network that isn't really working yet), it sends the request without
   * it, and the rules refuse it. They are counted like the others, so a send Storage really refuses
   * still ends [SendResult.FAILED], after [MediaSender.MAX_FAILED_ATTEMPTS] attempts.
   */
  private fun toSendResult(error: Exception): SendResult {
    val permanent =
        when (error) {
          is FirebaseFirestoreException -> error.code in PERMANENT_FIRESTORE_CODES
          is IllegalArgumentException -> true
          else -> false
        }
    return if (permanent) SendResult.FAILED else SendResult.RETRY
  }

  /**
   * Runs every one of [steps], even after one fails, then throws the first error, with the others
   * added to it. A cancellation stops at once.
   */
  private suspend fun runAll(vararg steps: suspend () -> Unit) {
    var first: Exception? = null
    for (step in steps) {
      try {
        step()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val earlier = first
        if (earlier == null) first = e else earlier.addSuppressed(e)
      }
    }
    first?.let { throw it }
  }

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
    const val CARE_CIRCLES_COLLECTION = "careCircles"
    const val MEDIA_COLLECTION = "media"

    // field names, as written by toMap()
    const val ID_FIELD = "id"
    const val STATUS_FIELD = "status"
    const val CREATED_AT_FIELD = "createdAt"
    const val SHOWN_IDS_FIELD = "shownIds"

    /** Firestore errors a send can't get past by trying again: the user isn't allowed. */
    val PERMANENT_FIRESTORE_CODES =
        setOf(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.UNAUTHENTICATED,
        )
  }
}
