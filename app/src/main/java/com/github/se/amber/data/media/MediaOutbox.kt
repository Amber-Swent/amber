// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import android.util.Log
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
 * {dir}/{circleId}/{mediaId}.json   {"state": "WAITING", "item": {"type": "picture", ...}}
 * {dir}/{circleId}/{mediaId}.bin    the media's file, moved in by add(); none for a text
 * ```
 *
 * The `.json` is the whole [OutboxEntry]: `"item"` holds the media's fields as they will be written
 * to Firestore (see [toMap]; the id is the file's name), and `"state"` its [SendState]. The `.bin`
 * is written before the `.json`, and removed after it, so a `.json` always has its file. Each file
 * is written to a temporary `.tmp` file and renamed once complete, so an entry is always whole.
 *
 * The state is stored because [SendState.FAILED] must survive a restart: the media's background job
 * has ended, so nothing would send it again, and the user must still see it failed and be able to
 * retry or discard it. [SendState.WAITING] is the state of any other entry after a restart.
 *
 * The entries are read from disk once, on the first call, then kept in memory; every call waits for
 * the previous ones. At that first read, entries still [SendState.SENDING] go back to
 * [SendState.WAITING], on disk too: the process that was sending them is gone, so none can be
 * running. Memory and disk then always hold the same state.
 *
 * The outbox only stores the [SendState] it is given ([setState]); it never sends or retries
 * anything itself. A [SendState.FAILED] entry stays as it is, across restarts, until the repository
 * puts it back to [SendState.WAITING] (`retrySend`) or removes it (`discardUnsent`).
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

  /** Every entry, by circleId then mediaId; empty until [loaded]. */
  private val entries = MutableStateFlow<Map<String, Map<String, OutboxEntry>>>(emptyMap())

  /** Guards the disk and [entries]: every call runs alone. */
  private val mutex = Mutex()

  private var loaded = false // guarded by mutex

  /**
   * The entries of [circleId], oldest first ([MediaItem.createdAt]). Emits again whenever one is
   * added, changes state or is removed.
   */
  fun observe(circleId: String): Flow<List<OutboxEntry>> = flow {
    locked {} // reads the disk on first use
    emitAll(
        entries
            .map { all ->
              all[circleId]
                  .orEmpty()
                  .values
                  .sortedWith(compareBy({ it.item.createdAt }, { it.item.id }))
            }
            .distinctUntilChanged()
    )
  }

  /** The entry [mediaId] of [circleId], or null if it isn't in the outbox. */
  suspend fun get(circleId: String, mediaId: String): OutboxEntry? = locked {
    entries.value[circleId]?.get(mediaId)
  }

  /**
   * The file of the entry [mediaId] of [circleId], or null if it isn't in the outbox or has no file
   * (a text). It stays in the outbox: open it right away, since it is gone once the media is sent.
   */
  suspend fun fileOf(circleId: String, mediaId: String): File? = locked {
    if (entries.value[circleId]?.containsKey(mediaId) != true) return@locked null
    binFile(circleId, mediaId).takeIf { it.exists() }
  }

  /**
   * Adds [item] to the outbox of [circleId], [SendState.WAITING], with [file] as its file, or none
   * if [file] is null (a text). [file] is moved into the outbox: once the call returns it is gone
   * from where it was.
   *
   * If the call fails, nothing is added and [file] is back where it was: [item] is turned into JSON
   * before [file] is touched, and if writing the entry fails after the move, [file] is moved back.
   * Only if that move back fails too (logged) is [file] lost.
   *
   * @throws IllegalArgumentException if [circleId] or [item]'s id isn't a valid id (blank, `.`,
   *   `..`, or containing '/'), if [item] is already in the outbox, or if [file] doesn't exist.
   * @throws IOException if the media can't be written (e.g. disk full).
   * @throws org.json.JSONException if [item] can't be written as JSON (e.g. a NaN latitude).
   */
  suspend fun add(circleId: String, item: MediaItem, file: File?) {
    requireValidId(circleId)
    requireValidId(item.id)
    locked {
      require(entries.value[circleId]?.containsKey(item.id) != true) {
        "Media ${item.id} is already in the outbox of $circleId"
      }
      require(file == null || file.isFile) { "No file to add at $file" }
      // mkdirs() doesn't do anything if the directory already exists
      val folder = File(dir, circleId).apply { mkdirs() }
      val entry = OutboxEntry(item, SendState.WAITING)
      // before touching file: bad data is refused while the file is still where it was
      val json = entryJson(entry)
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
      entries.update { it + (circleId to (it[circleId].orEmpty() + (item.id to entry))) }
    }
  }

  /**
   * Sets the [SendState] of the entry [mediaId] of [circleId], on disk and in memory.
   *
   * @return false if it isn't in the outbox (nothing changes).
   * @throws IOException if the change can't be written; the entry then keeps its old state.
   */
  suspend fun setState(circleId: String, mediaId: String, state: SendState): Boolean = locked {
    val entry = entries.value[circleId]?.get(mediaId) ?: return@locked false
    if (entry.state != state) {
      val changed = entry.copy(state = state)
      writeEntry(circleId, changed)
      entries.update { it + (circleId to (it[circleId].orEmpty() + (mediaId to changed))) }
    }
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
    if (entries.value[circleId]?.containsKey(mediaId) != true) return@locked
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
   * touching the disk or [entries] goes through it; the private helpers below don't take the lock
   * themselves, so they must only be called from inside a [block].
   */
  private suspend fun <T> locked(block: suspend () -> T): T = mutex.withLock {
    withContext(ioDispatcher) {
      if (!loaded) {
        entries.value = readAll()
        loaded = true
      }
      block()
    }
  }

  /**
   * Reads every entry from disk, and deletes what an interrupted call left behind: `.tmp` files,
   * and `.bin` files without their `.json`. An entry that can't be read (e.g. written by a newer
   * app version) is skipped and left on disk. Must be called inside [locked].
   *
   * An entry still [SendState.SENDING] is set back to [SendState.WAITING], and its `.json` is
   * rewritten so. If that write fails, the entry is still WAITING in memory (logged): its `.json`
   * says SENDING only until the next state change, or until the next start resets it again.
   */
  private fun readAll(): Map<String, Map<String, OutboxEntry>> {
    dir.mkdirs()
    val all = mutableMapOf<String, Map<String, OutboxEntry>>()
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
      val circleEntries =
          ids.mapNotNull { mediaId ->
                readEntry(circleId, mediaId)?.let { entry ->
                  mediaId to
                      if (entry.state == SendState.SENDING) resetSend(circleId, entry) else entry
                }
              }
              .toMap()
      if (circleEntries.isNotEmpty()) all[circleId] = circleEntries
    }
    return all
  }

  /**
   * [entry], whose send was interrupted, back to [SendState.WAITING], on disk and in the returned
   * entry. A failed write is only logged (see [readAll]). Must be called inside [locked].
   */
  private fun resetSend(circleId: String, entry: OutboxEntry): OutboxEntry {
    val waiting = entry.copy(state = SendState.WAITING)
    try {
      writeEntry(circleId, waiting)
    } catch (e: Exception) {
      Log.w(TAG, "Couldn't save outbox entry ${entry.item.id} as waiting", e)
    }
    return waiting
  }

  /**
   * The entry stored in `{mediaId}.json`, or null (logged) if it can't be read. Must be called
   * inside [locked].
   */
  private fun readEntry(circleId: String, mediaId: String): OutboxEntry? {
    val entry =
        try {
          val json = JSONObject(jsonFile(circleId, mediaId).readText())
          val state = SendState.entries.find { it.name == json.optString(STATE) }
          val item = mediaItemFromMap(mediaId, json.getJSONObject(ITEM).toMap())
          if (state != null && item != null) OutboxEntry(item, state) else null
        } catch (e: Exception) { // unreadable file or malformed JSON
          Log.w(TAG, "Couldn't read outbox entry $mediaId", e)
          return null
        }
    if (entry == null) Log.w(TAG, "Couldn't read outbox entry $mediaId: unknown type or state")
    return entry
  }

  /**
   * Writes [entry] to its `.json`, replacing the previous one at once. Must be called inside
   * [locked].
   */
  private fun writeEntry(circleId: String, entry: OutboxEntry) =
      writeJson(circleId, entry.item.id, entryJson(entry))

  /**
   * The `.json` text of [entry].
   *
   * @throws org.json.JSONException if it can't be written as JSON (e.g. a NaN latitude).
   */
  private fun entryJson(entry: OutboxEntry): String =
      JSONObject().put(STATE, entry.state.name).put(ITEM, entry.item.toMap().toJson()).toString()

  /**
   * Writes [json] to the `.json` of [mediaId], replacing the previous one at once. Must be called
   * inside [locked].
   */
  private fun writeJson(circleId: String, mediaId: String, json: String) {
    val folder = File(dir, circleId).apply { mkdirs() }
    moveIntoPlace(folder, jsonFile(circleId, mediaId)) { it.writeText(json) }
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
   * Keeps in [entries] only those whose `.json` is still on disk, after some were deleted. Must be
   * called inside [locked].
   */
  private fun forgetDeletedEntries() {
    entries.update { all ->
      all.mapValues { (circleId, circleEntries) ->
            circleEntries.filterKeys { jsonFile(circleId, it).exists() }
          }
          .filterValues { it.isNotEmpty() }
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

  private fun jsonFile(circleId: String, mediaId: String) =
      File(File(dir, circleId), mediaId + JSON)

  private fun binFile(circleId: String, mediaId: String) = File(File(dir, circleId), mediaId + BIN)

  private companion object {
    const val TAG = "MediaOutbox"
    const val JSON = ".json"
    const val BIN = ".bin"
    const val TMP = ".tmp"
    const val STATE = "state"
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
