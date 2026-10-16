// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import android.util.Log
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.OutboxEntry
import com.github.se.amber.model.media.SendState
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The media added on this phone and not in Firebase yet, kept on disk until they are sent, across
 * app restarts. Unlike [MediaFileCache], nothing here is ever evicted: it is the only copy of these
 * media. Used by [MediaRepositoryFirebase] only, never by ViewModels.
 *
 * On disk, one folder per circle, named by its circleId, and two files per media:
 * ```
 * {dir}/{circleId}/{mediaId}.json   {"state": "WAITING", "failedAttempts": 0, "storageBucket": "...",
 *                                    "item": {...}}
 * {dir}/{circleId}/{mediaId}.bin    the media's file, moved in by add(); none for a text
 * ```
 *
 * The `.json` holds the media's [OutboxEntry], how many of its send attempts failed in a row, and
 * its circle's bucket: `"item"` is the media's fields as they will be written to Firestore (see
 * [toMap]; the id is the file's name), `"state"` its [SendState], `"failedAttempts"` that count (0
 * if missing), and `"storageBucket"` the [CareCircle.storageBucket] its file goes to (blank if
 * missing, and for a text, which needs none). The `.bin` is written before the `.json` and removed
 * after it, so a `.json` always has its file. Each file is written to a temporary `.tmp` file and
 * renamed once complete, so an entry is always whole.
 *
 * Both are stored so that they survive a restart. A [SendState.FAILED] entry has no background job
 * left to send it, and the user must still see it and be able to retry or discard it. The count
 * lets the send give up after a number of failures even when they are spread over several runs of
 * the app. The bucket lets the send upload the file without looking the circle up. Both stay inside
 * the outbox: [observe] and [get] show only the [OutboxEntry]; the send reads the bucket through
 * [toSend].
 *
 * The entries are read from disk once, on the first call, then kept in memory; calls run one at a
 * time, so each sees the changes of the previous ones. At that first read, entries still
 * [SendState.SENDING] go back to [SendState.WAITING]: the process that was sending them is gone.
 *
 * The outbox only records what the repository tells it; it never sends anything itself. A send
 * attempt marks its entry [SendState.SENDING] ([startSending]), then ends it with exactly one of:
 * [remove] (sent), [resetSending] (cancelled), [recordFailedAttempt] (failed, may succeed later) or
 * [markFailed] (failed, retrying can't help). A [SendState.FAILED] entry stays so until [retry] or
 * [remove]. Each state change happens only from the state it expects, checked and changed in one
 * step, so a call that comes too late (e.g. from a job that was replaced) changes nothing.
 *
 * Only one instance may use a given [dir]. The constructor doesn't touch the disk.
 *
 * @param dir outbox folder, e.g. `File(context.filesDir, "media-outbox")`; created if missing.
 *   Never a cache folder: the system may empty those.
 * @param ioDispatcher where the disk work runs; tests pass a test dispatcher.
 */
class MediaOutbox(
    private val dir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

  /** Every entry with its failed-attempt count, by circleId then mediaId; empty until [loaded]. */
  private val records = MutableStateFlow<Map<String, Map<String, OutboxRecord>>>(emptyMap())

  /** Guards the disk and [records]: every call runs alone. */
  private val mutex = Mutex()

  private var loaded = false // guarded by mutex

  /**
   * The entries of [circleId], oldest first ([MediaItem.createdAt]). Emits again whenever one is
   * added, changes state or is removed, but not when only its failed-attempt count changes.
   */
  fun observe(circleId: String): Flow<List<OutboxEntry>> = flow {
    locked {} // reads the disk on first use
    emitAll(
        records
            .map { all ->
              all[circleId]
                  .orEmpty()
                  .values
                  .map { it.entry }
                  .sortedWith(compareBy({ it.item.createdAt }, { it.item.id }))
            }
            .distinctUntilChanged()
    )
  }

  /** The entry [mediaId] of [circleId], or null if it isn't in the outbox. */
  suspend fun get(circleId: String, mediaId: String): OutboxEntry? = locked {
    records.value[circleId]?.get(mediaId)?.entry
  }

  /**
   * What a send of the entry [mediaId] of [circleId] needs, read in one step; null if the entry
   * isn't in the outbox.
   */
  suspend fun toSend(circleId: String, mediaId: String): ToSend? = locked {
    val record = records.value[circleId]?.get(mediaId) ?: return@locked null
    ToSend(
        item = record.entry.item,
        storageBucket = record.storageBucket,
        file = binFile(circleId, mediaId).takeIf { it.exists() },
    )
  }

  /**
   * Every [SendState.WAITING] entry, of every circle, as (circleId, mediaId) pairs: those a send
   * job should exist for. Read after the [SendState.SENDING] reset of the first call, so entries
   * cut short by the end of their process are included.
   */
  suspend fun waitingIds(): List<Pair<String, String>> = locked {
    records.value.flatMap { (circleId, circleRecords) ->
      circleRecords.values
          .filter { it.entry.state == SendState.WAITING }
          .map { circleId to it.entry.item.id }
    }
  }

  /**
   * The file of the entry [mediaId] of [circleId], or null if it isn't in the outbox or has no file
   * (a text). It stays in the outbox: open it right away, since it is gone once the media is sent.
   */
  suspend fun fileOf(circleId: String, mediaId: String): File? = locked {
    if (records.value[circleId]?.containsKey(mediaId) != true) return@locked null
    binFile(circleId, mediaId).takeIf { it.exists() }
  }

  /**
   * Adds [item] to the outbox of [circle], [SendState.WAITING] with no failed attempt, with [file]
   * as its file, or none if [file] is null (a text). [circle]'s bucket is kept with the entry
   * ([toSend]); it isn't checked here. [file] is moved into the outbox: once the call returns it is
   * gone from where it was.
   *
   * If the call fails, nothing is added and [file] is back where it was: [item] is turned into JSON
   * before [file] is touched, and if writing the entry fails after the move, [file] is moved back.
   * Only if that move back fails too (logged) is [file] lost.
   *
   * @throws IllegalArgumentException if [circle]'s id or [item]'s id isn't a valid id (blank, `.`,
   *   `..`, or containing '/'), if [item] is already in the outbox, or if [file] doesn't exist.
   * @throws IOException if the media can't be written (e.g. disk full).
   * @throws org.json.JSONException if [item] can't be written as JSON (e.g. a NaN latitude).
   */
  suspend fun add(circle: CareCircle, item: MediaItem, file: File?) {
    val circleId = circle.id
    requireValidId(circleId)
    requireValidId(item.id)
    locked {
      require(records.value[circleId]?.containsKey(item.id) != true) {
        "Media ${item.id} is already in the outbox of $circleId"
      }
      require(file == null || file.isFile) { "No file to add at $file" }
      // mkdirs() doesn't do anything if the directory already exists
      val folder = File(dir, circleId).apply { mkdirs() }
      val record = OutboxRecord(OutboxEntry(item, SendState.WAITING), circle.storageBucket)
      // before touching file: bad data is refused while the file is still where it was
      val json = recordJson(record)
      var moved: File? = null // where the caller's file is, once moved into the outbox
      try {
        if (file != null) {
          // moved into a temp file first: across storage volumes a move is really a copy, and a
          // half-copied file must never appear under its name
          val temp = File.createTempFile("entry", TMP, folder)
          try {
            Files.move(file.toPath(), temp.toPath(), REPLACE_EXISTING)
          } catch (e: Exception) {
            temp.delete() // NOSONAR: a failed move leaves file where it was; a leftover is removed
            throw e
          }
          moved = temp
          val bin = binFile(circleId, item.id)
          Files.move(temp.toPath(), bin.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
          moved = bin
        }
        writeJson(circleId, item.id, json)
      } catch (e: Exception) {
        if (moved != null) giveBack(moved, checkNotNull(file), item.id, e)
        throw e
      }
      remember(circleId, record)
    }
  }

  /**
   * Starts a send attempt of the entry [mediaId] of [circleId]: moves it from [SendState.WAITING]
   * to [SendState.SENDING]. Its failed-attempt count stays as it is.
   *
   * Changes the entry only if it is [SendState.WAITING]. Otherwise there is nothing for the attempt
   * to do: the entry is gone, already being sent, or [SendState.FAILED] (sent again only after
   * [retry]).
   *
   * @return true if the entry was [SendState.WAITING] and is now [SendState.SENDING]; false if it
   *   isn't in the outbox or is in another state (nothing changes).
   * @throws IOException if the change can't be written; the entry then stays [SendState.WAITING].
   */
  suspend fun startSending(circleId: String, mediaId: String): Boolean = locked {
    moveState(circleId, mediaId, from = SendState.WAITING, to = SendState.SENDING)
  }

  /**
   * Ends a cancelled send attempt of the entry [mediaId] of [circleId]: moves it from
   * [SendState.SENDING] back to [SendState.WAITING], without counting a failure (the system stopped
   * the attempt; nothing went wrong with the media).
   *
   * Changes the entry only if it is still [SendState.SENDING], and then only its state: a change
   * made since the attempt started (e.g. [retry] resetting the count) is kept.
   *
   * @return true if the entry was [SendState.SENDING] and is now [SendState.WAITING]; false if it
   *   isn't in the outbox or is in another state (nothing changes).
   * @throws IOException if the change can't be written; the entry then stays [SendState.SENDING]
   *   until the next start of the app resets it.
   */
  suspend fun resetSending(circleId: String, mediaId: String): Boolean = locked {
    moveState(circleId, mediaId, from = SendState.SENDING, to = SendState.WAITING)
  }

  /**
   * Ends a failed send attempt of the entry [mediaId] of [circleId], when the failure may go away
   * (e.g. a network error): adds 1 to its failed-attempt count, and moves it from
   * [SendState.SENDING] back to [SendState.WAITING] for a later attempt, or to [SendState.FAILED]
   * once the count reaches [maxAttempts]. The count and the state are saved in one write, so a
   * crash can't keep one without the other.
   *
   * Changes the entry only if it is still [SendState.SENDING]: otherwise the attempt has already
   * been ended some other way, and nothing is recorded.
   *
   * @param maxAttempts how many failed attempts in a row make the entry [SendState.FAILED]; at
   *   least 1. The send passes [MediaSender.MAX_FAILED_ATTEMPTS].
   * @return the new failed-attempt count; null if the entry isn't in the outbox or isn't
   *   [SendState.SENDING] (nothing changes).
   * @throws IllegalArgumentException if [maxAttempts] is less than 1.
   * @throws IOException if the change can't be written; the entry then keeps its old count and
   *   state.
   */
  suspend fun recordFailedAttempt(circleId: String, mediaId: String, maxAttempts: Int): Int? {
    require(maxAttempts >= 1) { "maxAttempts must be at least 1, not $maxAttempts" }
    return locked {
      val record = records.value[circleId]?.get(mediaId)
      if (record?.entry?.state != SendState.SENDING) return@locked null
      val failedAttempts = record.failedAttempts + 1
      val state = if (failedAttempts >= maxAttempts) SendState.FAILED else SendState.WAITING
      save(circleId, record.withState(state).copy(failedAttempts = failedAttempts))
      failedAttempts
    }
  }

  /**
   * Ends a send attempt of the entry [mediaId] of [circleId] that failed for a reason retrying
   * can't fix (e.g. permission denied, or its file is missing): moves it from [SendState.SENDING]
   * to [SendState.FAILED] at once. Unlike [recordFailedAttempt], nothing is counted; the count
   * stays as it is, and [retry] resets it.
   *
   * Changes the entry only if it is still [SendState.SENDING]: otherwise the attempt has already
   * been ended some other way.
   *
   * @return true if the entry was [SendState.SENDING] and is now [SendState.FAILED]; false if it
   *   isn't in the outbox or is in another state (nothing changes).
   * @throws IOException if the change can't be written; the entry then stays [SendState.SENDING]
   *   until the next start of the app resets it.
   */
  suspend fun markFailed(circleId: String, mediaId: String): Boolean = locked {
    moveState(circleId, mediaId, from = SendState.SENDING, to = SendState.FAILED)
  }

  /**
   * Lets the [SendState.FAILED] entry [mediaId] of [circleId] be sent again: moves it back to
   * [SendState.WAITING] and its failed-attempt count back to 0, so its next send has all its
   * attempts again. The caller then schedules that send ([MediaSendScheduler.reschedule]).
   *
   * Changes the entry only if it is [SendState.FAILED]. The check and the change happen together,
   * so if the user taps "retry" twice quickly, only the first call returns true, and the caller
   * schedules a single send.
   *
   * @return true if the entry was [SendState.FAILED] and is now [SendState.WAITING]; false if it
   *   isn't in the outbox or is in another state (nothing changes).
   * @throws IOException if the change can't be written; the entry then stays [SendState.FAILED].
   */
  suspend fun retry(circleId: String, mediaId: String): Boolean = locked {
    val record = records.value[circleId]?.get(mediaId)
    if (record?.entry?.state != SendState.FAILED) return@locked false
    save(circleId, record.withState(SendState.WAITING).copy(failedAttempts = 0))
    true
  }

  /**
   * Removes the entry [mediaId] of [circleId] and its file, if still there (a send moves it to the
   * cache first). No effect if it isn't in the outbox.
   *
   * @throws IOException if some of its files can't be deleted. If its `.json` is gone, the entry is
   *   removed all the same; otherwise it stays in the outbox. A `.bin` left alone is deleted at the
   *   next start.
   */
  suspend fun remove(circleId: String, mediaId: String): Unit = locked {
    if (records.value[circleId]?.containsKey(mediaId) != true) return@locked
    try {
      // the .json first: once it is gone, a .bin left alone is no longer an entry
      deleteFiles(listOf(jsonFile(circleId, mediaId), binFile(circleId, mediaId)))
    } finally {
      forgetDeletedEntries()
    }
  }

  /**
   * Removes every entry of [circleId], with their files. Their media is then lost.
   *
   * @throws IllegalArgumentException if [circleId] isn't a valid id.
   * @throws IOException if some files can't be deleted: every file is tried first, and the entries
   *   whose `.json` is still there stay in the outbox.
   */
  suspend fun clear(circleId: String) {
    requireValidId(circleId)
    locked { clearFolders(listOf(File(dir, circleId))) }
  }

  /** Removes every entry of every circle, like [clear] for each. */
  suspend fun clear(): Unit = locked {
    clearFolders(dir.listFiles { f -> f.isDirectory }.orEmpty().asList())
  }

  /**
   * Runs [block] alone, on [ioDispatcher], after reading the disk once if not done yet. Every call
   * touching the disk or [records] goes through it; the private helpers below don't take the lock
   * themselves, so they must only be called from inside a [block].
   */
  private suspend fun <T> locked(block: suspend () -> T): T = mutex.withLock {
    withContext(ioDispatcher) {
      if (!loaded) {
        records.value = readAll()
        loaded = true
      }
      block()
    }
  }

  /**
   * Reads every entry from disk, and deletes what an interrupted call left behind: `.tmp` files,
   * and `.bin` files without their `.json`. An entry that can't be read (e.g. written by a newer
   * app version) is skipped and left on disk. Entries still [SendState.SENDING] are reset
   * ([resetOnLoad]). Must be called inside [locked].
   */
  private fun readAll(): Map<String, Map<String, OutboxRecord>> {
    dir.mkdirs()
    val all = mutableMapOf<String, Map<String, OutboxRecord>>()
    for (folder in dir.listFiles { f -> f.isDirectory }.orEmpty()) {
      val circleId = folder.name
      val files = folder.listFiles().orEmpty()
      val ids = files.filter { it.name.endsWith(JSON) }.map { it.name.removeSuffix(JSON) }.toSet()
      for (leftover in files) {
        val orphanBin = leftover.name.endsWith(BIN) && leftover.name.removeSuffix(BIN) !in ids
        val isLeftover = leftover.name.endsWith(TMP) || orphanBin
        if (isLeftover && !leftover.delete() && leftover.exists()) {
          // only wasted space, never an entry: tried again at the next start
          Log.w(TAG, "Couldn't delete the leftover ${leftover.name} of the outbox")
        }
      }
      val circleRecords =
          ids.mapNotNull { mediaId ->
                readRecord(circleId, mediaId)?.let { record ->
                  mediaId to
                      if (record.entry.state == SendState.SENDING) resetOnLoad(circleId, record)
                      else record
                }
              }
              .toMap()
      if (circleRecords.isNotEmpty()) all[circleId] = circleRecords
    }
    return all
  }

  /**
   * The record stored in `{mediaId}.json`, or null (logged) if it can't be read. A missing or
   * invalid failed-attempt count reads as 0, a missing or invalid bucket as blank (entries written
   * before the bucket was kept). Must be called inside [locked].
   */
  private fun readRecord(circleId: String, mediaId: String): OutboxRecord? {
    val record =
        try {
          val json = JSONObject(jsonFile(circleId, mediaId).readText())
          val state = SendState.entries.find { it.name == json.optString(STATE) }
          val item = mediaItemFromMap(mediaId, json.getJSONObject(ITEM).toMap())
          val failedAttempts = json.optInt(FAILED_ATTEMPTS, 0).coerceAtLeast(0)
          val storageBucket = json.opt(STORAGE_BUCKET) as? String ?: ""
          if (state != null && item != null) {
            OutboxRecord(OutboxEntry(item, state), storageBucket, failedAttempts)
          } else {
            null
          }
        } catch (e: Exception) { // unreadable file or malformed JSON
          Log.w(TAG, "Couldn't read outbox entry $mediaId", e)
          return null
        }
    if (record == null) Log.w(TAG, "Couldn't read outbox entry $mediaId: unknown type or state")
    return record
  }

  /**
   * [record], whose send attempt was cut short by the end of its process, back to
   * [SendState.WAITING], on disk and in the returned record. The interrupted attempt isn't counted
   * as failed. If the write fails (logged), the entry is WAITING in memory all the same: its
   * `.json` still says SENDING, until its next change or the next start. Must be called inside
   * [locked].
   */
  private fun resetOnLoad(circleId: String, record: OutboxRecord): OutboxRecord {
    val waiting = record.withState(SendState.WAITING)
    try {
      writeRecord(circleId, waiting)
    } catch (e: Exception) {
      Log.w(TAG, "Couldn't save outbox entry ${record.entry.item.id} as waiting", e)
    }
    return waiting
  }

  /**
   * Moves the entry [mediaId] of [circleId] from [from] to [to], keeping its failed-attempt count,
   * if it is [from]. Must be called inside [locked], which makes the check and the change one step.
   *
   * @return true if it moved; false if it isn't in the outbox or isn't [from] (nothing changes).
   * @throws IOException if the change can't be written; the entry then stays [from].
   */
  private fun moveState(
      circleId: String,
      mediaId: String,
      from: SendState,
      to: SendState,
  ): Boolean {
    val record = records.value[circleId]?.get(mediaId)
    if (record?.entry?.state != from) return false
    save(circleId, record.withState(to))
    return true
  }

  /**
   * Writes [record] to its `.json`, then keeps it in memory. If the write fails, memory keeps the
   * old record, like the disk. Must be called inside [locked].
   *
   * @throws IOException if it can't be written.
   */
  private fun save(circleId: String, record: OutboxRecord) {
    writeRecord(circleId, record)
    remember(circleId, record)
  }

  /** Keeps [record] in memory, replacing the previous one. Must be called inside [locked]. */
  private fun remember(circleId: String, record: OutboxRecord) {
    val mediaId = record.entry.item.id
    records.update { it + (circleId to (it[circleId].orEmpty() + (mediaId to record))) }
  }

  /**
   * Writes [record] to its `.json`, replacing the previous one at once. Must be called inside
   * [locked].
   */
  private fun writeRecord(circleId: String, record: OutboxRecord) =
      writeJson(circleId, record.entry.item.id, recordJson(record))

  /**
   * The `.json` text of [record].
   *
   * @throws org.json.JSONException if it can't be written as JSON (e.g. a NaN latitude).
   */
  private fun recordJson(record: OutboxRecord): String =
      JSONObject()
          .put(STATE, record.entry.state.name)
          .put(FAILED_ATTEMPTS, record.failedAttempts)
          .put(STORAGE_BUCKET, record.storageBucket)
          .put(ITEM, record.entry.item.toMap().toJson())
          .toString()

  /**
   * Writes [json] to the `.json` of [mediaId], replacing the previous one at once. Must be called
   * inside [locked].
   */
  private fun writeJson(circleId: String, mediaId: String, json: String) {
    val folder = File(dir, circleId).apply { mkdirs() }
    moveIntoPlace(folder, jsonFile(circleId, mediaId)) { it.writeText(json) }
  }

  /**
   * Fills a new `.tmp` file of [folder] with [fill], then renames it to [target], replacing it.
   * Both are in [folder], so the rename is atomic: [target] is either the old file or the new one.
   * Must be called inside [locked].
   */
  private fun moveIntoPlace(folder: File, target: File, fill: (File) -> Unit) {
    val temp = File.createTempFile("entry", TMP, folder)
    try {
      fill(temp)
      Files.move(temp.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    } finally {
      temp.delete() // NOSONAR: no-op once renamed; a leftover is removed at the next start
    }
  }

  /**
   * Moves the file of a failed [add] back from [moved] to [source], where the caller had it. If
   * even that fails, the file is lost: it is logged, and the reason is added to [cause]. Must be
   * called inside [locked].
   */
  private fun giveBack(moved: File, source: File, mediaId: String, cause: Exception) {
    try {
      Files.move(moved.toPath(), source.toPath(), REPLACE_EXISTING)
    } catch (e: Exception) {
      cause.addSuppressed(e)
      // its copy in the outbox is a leftover now, removed at the next start
      Log.e(TAG, "Couldn't give back the file of media $mediaId after a failed add: it is lost", e)
    }
  }

  /**
   * Deletes [folders] and everything in them, trying every file, then [forgetDeletedEntries]. Must
   * be called inside [locked].
   *
   * @throws IOException if some files are left.
   */
  private fun clearFolders(folders: List<File>) {
    val files = folders.flatMap { it.listFiles().orEmpty().asList() }
    try {
      deleteFiles(files)
    } finally {
      folders.forEach { it.delete() } // NOSONAR: an empty folder left behind is harmless
      forgetDeletedEntries()
    }
  }

  /**
   * Deletes each of [files], trying them all even if some fail. Must be called inside [locked].
   *
   * @throws IOException if some still exist afterwards.
   */
  private fun deleteFiles(files: List<File>) {
    // File.delete() also returns false when the file was already gone, which is fine
    val left = files.filter { !it.delete() && it.exists() }
    if (left.isNotEmpty()) {
      throw IOException("Could not delete ${left.size} outbox file(s), e.g. ${left.first()}")
    }
  }

  /**
   * Keeps in [records] only those whose `.json` is still on disk, after some were deleted. Must be
   * called inside [locked].
   */
  private fun forgetDeletedEntries() {
    records.update { all ->
      all.mapValues { (circleId, circleRecords) ->
            circleRecords.filterKeys { jsonFile(circleId, it).exists() }
          }
          .filterValues { it.isNotEmpty() }
    }
  }

  private fun jsonFile(circleId: String, mediaId: String) =
      File(File(dir, circleId), mediaId + JSON)

  private fun binFile(circleId: String, mediaId: String) = File(File(dir, circleId), mediaId + BIN)

  /**
   * What a send of one entry needs ([toSend]).
   *
   * @property item the media, as it will be written to Firestore.
   * @property storageBucket the bucket its file goes to, as given to [add]; blank for a text.
   * @property file its file in the outbox, to upload; null for a text, or if the file is gone (e.g.
   *   an earlier send moved it into the cache, then couldn't remove the entry).
   */
  data class ToSend(val item: MediaItem, val storageBucket: String, val file: File?)

  /**
   * What the outbox keeps of one media: its [entry], as [observe] and [get] show it, and what only
   * the outbox and the send see: the [storageBucket] its file goes to, and how many of its send
   * attempts failed in a row.
   */
  private data class OutboxRecord(
      val entry: OutboxEntry,
      val storageBucket: String,
      val failedAttempts: Int = 0,
  ) {
    /** This record with its entry in [state], and the same bucket and failed-attempt count. */
    fun withState(state: SendState) = copy(entry = entry.copy(state = state))
  }

  private companion object {
    const val TAG = "MediaOutbox"
    const val JSON = ".json"
    const val BIN = ".bin"
    const val TMP = ".tmp"
    const val STATE = "state"
    const val FAILED_ATTEMPTS = "failedAttempts"
    const val STORAGE_BUCKET = "storageBucket"
    const val ITEM = "item"

    /** A circle or media id, used as a folder or file name: never a path. */
    fun requireValidId(id: String) =
        require(id.isNotBlank() && id != "." && id != ".." && '/' !in id) { "Invalid id: \"$id\"" }

    /** [toMap]'s values as JSON: nested maps become objects, lists arrays, null is left out. */
    fun Map<String, Any?>.toJson(): JSONObject =
        JSONObject().also { json ->
          for ((key, value) in this) if (value != null) json.put(key, value.toJsonValue())
        }

    fun Any.toJsonValue(): Any =
        when (this) {
          is Map<*, *> -> entries.associate { (k, v) -> k.toString() to v }.toJson()
          is List<*> ->
              JSONArray().also { array -> filterNotNull().forEach { array.put(it.toJsonValue()) } }
          else -> this
        }

    /** The plain values of this JSON object, the reverse of [toJson]. */
    fun JSONObject.toMap(): Map<String, Any?> =
        keys().asSequence().associateWith { fromJsonValue(get(it)) }

    fun fromJsonValue(value: Any?): Any? =
        when (value) {
          JSONObject.NULL -> null
          is JSONObject -> value.toMap()
          is JSONArray -> (0 until value.length()).map { fromJsonValue(value.get(it)) }
          else -> value
        }
  }
}
