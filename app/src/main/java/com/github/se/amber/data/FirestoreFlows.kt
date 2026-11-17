// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * An item of a live list, and whether the server has it yet.
 *
 * The flag is never stored in Firestore. That is why it wraps the item instead of being one of its
 * fields.
 *
 * @property value the item, as this phone sees it.
 * @property isSynced false while the item has a change the server hasn't confirmed yet. For
 *   example, an appointment added offline.
 */
data class Synced<T>(val value: T, val isSynced: Boolean)

/**
 * Watches what this query finds. Emits the result at once, then again on each change, until the
 * collector stops. A collector slower than the changes only gets the latest result. Older ones are
 * dropped, since they are out of date anyway.
 *
 * If Firebase refuses to answer (for example, permission denied after the user left the circle),
 * the flow ends with that error, so the collector can catch it. Going offline doesn't end it. The
 * result then comes from what this phone has already loaded. Firestore's ready-made
 * `Query.snapshots()` isn't used since it ends with a cancellation which a ViewModel's coroutine
 * treats as a normal stop, so the error would go unnoticed.
 *
 * @param metadataChanges [MetadataChanges.INCLUDE] also emits when only the metadata changes, for
 *   example when the server confirms a write.
 * @return the results of the query, which ends with Firebase's error.
 */
fun Query.observeSnapshots(
    metadataChanges: MetadataChanges = MetadataChanges.EXCLUDE
): Flow<QuerySnapshot> {
  val snapshots = callbackFlow {
    val listener =
        EventListener<QuerySnapshot> { snapshot, error ->
          when {
            error != null -> close(error)
            snapshot != null -> trySend(snapshot)
          }
        }
    // Firestore's one-argument call is the same as EXCLUDE. It is kept for EXCLUDE, so media's
    // tests, which stub it, still check this code unchanged.
    val registration =
        if (metadataChanges == MetadataChanges.EXCLUDE) addSnapshotListener(listener)
        else addSnapshotListener(metadataChanges, listener)
    awaitClose { registration.remove() }
  }
  return snapshots.conflate()
}

/**
 * Watches the documents this query finds, each with whether the server has it yet. Built on
 * [observeSnapshots], so it emits and ends the same way.
 *
 * It also emits when the server confirms a write, so [Synced.isSynced] turns true. A change that
 * leaves the list the same emits nothing.
 *
 * @param map reads one document. It returns null for a document it can't read, which is then
 *   skipped. One bad document must not break the whole list.
 * @param onError turns Firebase's error into the error the callers expect.
 * @return the readable documents, in the query's order, which ends with `onError(error)`.
 */
fun <T : Any> Query.observeDocuments(
    map: (DocumentSnapshot) -> T?,
    onError: (Exception) -> Exception,
): Flow<List<Synced<T>>> =
    // TODO explain the .INCLUDE in a small comment.
    observeSnapshots(MetadataChanges.INCLUDE)
        .map { snapshot ->
          snapshot.documents.mapNotNull { document ->
            map(document)?.let { Synced(it, isSynced = !document.metadata.hasPendingWrites()) }
          }
        }
        // Other metadata changes, such as going online, emit the same list again.
        .distinctUntilChanged()
        .catch { error -> throw if (error is Exception) onError(error) else error }
