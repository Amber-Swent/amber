// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media

import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.OutboxEntry
import com.github.se.amber.model.media.SendState
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Checks [MediaOutbox] on a real folder. Robolectric gives it Android's org.json, which the plain
 * JVM only has as empty stubs.
 *
 * The tests follow the outbox's life: listing, adding, the state changes of a send, removing and
 * clearing, then what a restart reads back.
 */
@RunWith(RobolectricTestRunner::class)
class MediaOutboxTest {

  @get:Rule val tmp = TemporaryFolder()

  private val dir by lazy { File(tmp.root, "outbox") }

  private fun picture(id: String, createdAt: Long = 0L) =
      MediaItem.Picture(
          id = id,
          authorId = "u1",
          storagePath = "careCircles/c1/media/$id.jpg",
          createdAt = createdAt,
          // a whole latitude: JSON gives it back as an integer
          location = GeoLocation(lat = 46.0, lng = 6.6),
          width = 4,
          height = 3,
      )

  private fun text(id: String) = MediaItem.Text(id = id, authorId = "u1", text = "Hi!")

  /** A file holding [content], as the camera or the recorder would give it. */
  private fun source(content: String = "bytes"): File = tmp.newFile().apply { writeText(content) }

  /** A new outbox on [dir], as after an app restart. */
  private fun TestScope.newOutbox() =
      MediaOutbox(dir, ioDispatcher = StandardTestDispatcher(testScheduler))

  private suspend fun MediaOutbox.idsOf(circleId: String) =
      observe(circleId).first().map { it.item.id }

  private suspend fun MediaOutbox.stateOf(circleId: String, mediaId: String) =
      get(circleId, mediaId)?.state

  /** One send attempt of [mediaId] that fails for a reason that may go away, as a send does it. */
  private suspend fun MediaOutbox.failAttempt(
      circleId: String,
      mediaId: String,
      maxAttempts: Int = MediaSender.MAX_FAILED_ATTEMPTS,
  ): Int? {
    startSending(circleId, mediaId)
    return recordFailedAttempt(circleId, mediaId, maxAttempts)
  }

  /**
   * One send attempt of [mediaId] that fails for a reason retrying can't fix, as a send does it.
   */
  private suspend fun MediaOutbox.failForGood(circleId: String, mediaId: String) {
    startSending(circleId, mediaId)
    markFailed(circleId, mediaId)
  }

  private fun fileOnDisk(circleId: String, name: String) = File(File(dir, circleId), name)

  private fun jsonOnDisk(circleId: String, mediaId: String) =
      JSONObject(fileOnDisk(circleId, "$mediaId.json").readText())

  /** Changes the `.json` of [mediaId] on disk, as another app version or a damaged file would. */
  private fun rewriteJson(circleId: String, mediaId: String, change: JSONObject.() -> Unit) {
    val json = jsonOnDisk(circleId, mediaId).apply(change)
    fileOnDisk(circleId, "$mediaId.json").writeText(json.toString())
  }

  /** Makes [file] impossible to delete or replace, on every OS: a folder that isn't empty. */
  private fun makeUndeletable(file: File) {
    file.delete()
    File(file, "inside").apply { parentFile!!.mkdirs() }.writeText("x")
  }

  @Test
  fun entriesAreListedOldestFirstPerCircle() = runTest {
    val outbox = newOutbox()
    outbox.add("c1", picture("late", createdAt = 30), source())
    outbox.add("c1", picture("early", createdAt = 10), source())
    outbox.add("c1", text("middle").copy(createdAt = 20), null)
    outbox.add("c2", picture("other", createdAt = 0), source())

    assertEquals(listOf("early", "middle", "late"), outbox.idsOf("c1"))
    assertEquals(listOf("other"), outbox.idsOf("c2"))
    assertEquals(emptyList<String>(), outbox.idsOf("c3"))
  }

  @Test
  fun observeEmitsOnEveryChange() = runTest {
    val outbox = newOutbox()
    val seen = mutableListOf<List<Pair<String, SendState>>>()
    backgroundScope.launch {
      outbox.observe("c1").collect { list -> seen += list.map { it.item.id to it.state } }
    }
    runCurrent()

    outbox.add("c1", picture("m1"), source())
    runCurrent()
    outbox.startSending("c1", "m1")
    runCurrent()
    outbox.recordFailedAttempt("c1", "m1", MediaSender.MAX_FAILED_ATTEMPTS)
    runCurrent()
    outbox.add("c2", picture("m2"), source()) // another circle: no emission for c1
    runCurrent()
    outbox.remove("c1", "m1")
    runCurrent()

    assertEquals(
        listOf(
            emptyList(),
            listOf("m1" to SendState.WAITING),
            listOf("m1" to SendState.SENDING),
            listOf("m1" to SendState.WAITING),
            emptyList(),
        ),
        seen,
    )
  }

  @Test
  fun anAddedMediaIsWaitingWithItsFileMovedIn() = runTest {
    val outbox = newOutbox()
    val source = source("picture bytes")
    val item = picture("m1")

    outbox.add("c1", item, source)

    assertEquals(OutboxEntry(item, SendState.WAITING), outbox.get("c1", "m1"))
    assertEquals("picture bytes", outbox.fileOf("c1", "m1")!!.readText())
    assertFalse("the file must be moved, not copied", source.exists())
    assertEquals(0, jsonOnDisk("c1", "m1").getInt("failedAttempts"))
  }

  @Test
  fun aTextHasNoFile() = runTest {
    val outbox = newOutbox()

    outbox.add("c1", text("m1"), null)

    assertEquals(OutboxEntry(text("m1"), SendState.WAITING), outbox.get("c1", "m1"))
    assertNull(outbox.fileOf("c1", "m1"))
    assertFalse(fileOnDisk("c1", "m1.bin").exists())
  }

  @Test
  fun addingTheSameMediaTwiceIsRefused() = runTest {
    val outbox = newOutbox()
    outbox.add("c1", picture("m1"), source("first"))
    val second = source("second")

    val error = runCatching { outbox.add("c1", picture("m1"), second) }.exceptionOrNull()

    assertTrue(error is IllegalArgumentException)
    assertEquals("first", outbox.fileOf("c1", "m1")!!.readText())
    assertTrue("a refused file is left where it was", second.exists())
  }

  @Test
  fun invalidIdsAndMissingFilesAreRefused() = runTest {
    val outbox = newOutbox()

    for (badId in listOf("", " ", ".", "..", "a/b")) {
      val badCircle = runCatching { outbox.add(badId, picture("m1"), source()) }
      val badMedia = runCatching { outbox.add("c1", picture(badId), source()) }
      assertTrue("circle '$badId'", badCircle.exceptionOrNull() is IllegalArgumentException)
      assertTrue("media '$badId'", badMedia.exceptionOrNull() is IllegalArgumentException)
    }
    val missing = File(tmp.root, "missing.jpg")
    val noFile = runCatching { outbox.add("c1", picture("m1"), missing) }
    assertTrue(noFile.exceptionOrNull() is IllegalArgumentException)
    assertEquals(emptyList<String>(), outbox.idsOf("c1"))
  }

  @Test
  fun anAddThatCantBeWrittenLeavesNothingBehind() = runTest {
    val outbox = newOutbox()
    outbox.add("c2", picture("m0"), source()) // the outbox folder exists
    // a file where the circle's folder should be: nothing can be written in it
    File(dir, "c1").writeText("not a folder")
    val source = source()

    val error = runCatching { outbox.add("c1", picture("m1"), source) }.exceptionOrNull()

    assertTrue(error is IOException)
    assertNull(outbox.get("c1", "m1"))
    assertTrue("the file was never moved", source.exists())
    assertNull(newOutbox().get("c1", "m1"))
  }

  @Test
  fun aMediaThatCantBeWrittenAsJsonLeavesTheFileUntouched() = runTest {
    val outbox = newOutbox()
    val source = source("the only copy")
    // org.json refuses NaN
    val item = picture("m1").copy(location = GeoLocation(lat = Double.NaN, lng = 6.6))

    val error = runCatching { outbox.add("c1", item, source) }.exceptionOrNull()

    assertTrue(error is JSONException)
    assertEquals("the only copy", source.readText())
    assertNull(outbox.get("c1", "m1"))
    assertFalse(fileOnDisk("c1", "m1.bin").exists())
  }

  @Test
  fun anEntryThatCantBeWrittenAfterTheMoveGivesTheFileBack() = runTest {
    val outbox = newOutbox()
    outbox.idsOf("c1") // the outbox has read the disk
    // something in the way of the entry's .json (e.g. a full disk would fail the same way)
    makeUndeletable(fileOnDisk("c1", "m1.json"))
    val source = source("the only copy")

    val error = runCatching { outbox.add("c1", picture("m1"), source) }.exceptionOrNull()

    assertTrue(error is IOException)
    assertEquals("the file is back where it was", "the only copy", source.readText())
    assertNull(outbox.get("c1", "m1"))
    assertFalse(fileOnDisk("c1", "m1.bin").exists())
  }

  @Test
  fun startSendingMovesOnlyAWaitingEntry() = runTest {
    val outbox = newOutbox()
    outbox.add("c1", picture("waiting"), source())
    outbox.add("c1", picture("failed"), source())
    outbox.failForGood("c1", "failed")

    assertTrue(outbox.startSending("c1", "waiting"))
    assertFalse("already being sent", outbox.startSending("c1", "waiting"))
    // a FAILED entry is sent again only after a retry
    assertFalse(outbox.startSending("c1", "failed"))
    assertFalse(outbox.startSending("c1", "missing"))
    assertFalse(outbox.startSending("c2", "waiting"))

    assertEquals(SendState.SENDING, outbox.stateOf("c1", "waiting"))
    assertEquals("SENDING", jsonOnDisk("c1", "waiting").getString("state"))
    assertEquals(SendState.FAILED, outbox.stateOf("c1", "failed"))
    assertNull(outbox.get("c1", "missing"))
  }

  @Test
  fun startSendingKeepsTheFailedAttemptCount() = runTest {
    val outbox = newOutbox()
    outbox.add("c1", picture("m1"), source())
    outbox.failAttempt("c1", "m1")

    outbox.startSending("c1", "m1")

    assertEquals(1, jsonOnDisk("c1", "m1").getInt("failedAttempts"))
    assertEquals(2, outbox.recordFailedAttempt("c1", "m1", MediaSender.MAX_FAILED_ATTEMPTS))
  }

  @Test
  fun resetSendingPutsACancelledAttemptBackToWaitingWithoutCountingIt() = runTest {
    val outbox = newOutbox()
    outbox.add("c1", picture("m1"), source())
    outbox.failAttempt("c1", "m1")
    outbox.startSending("c1", "m1")

    assertTrue(outbox.resetSending("c1", "m1"))

    assertEquals(SendState.WAITING, outbox.stateOf("c1", "m1"))
    assertEquals("WAITING", jsonOnDisk("c1", "m1").getString("state"))
    assertEquals("the cancelled attempt isn't counted", 2, outbox.failAttempt("c1", "m1"))
  }

  @Test
  fun resetSendingLeavesAnEntryThatIsntSendingAlone() = runTest {
    val outbox = newOutbox()
    outbox.add("c1", picture("waiting"), source())
    outbox.add("c1", picture("failed"), source())
    outbox.failForGood("c1", "failed")

    assertFalse(outbox.resetSending("c1", "waiting"))
    // e.g. an attempt cancelled after another one had already made the entry FAILED
    assertFalse(outbox.resetSending("c1", "failed"))
    assertFalse(outbox.resetSending("c1", "missing"))

    assertEquals(SendState.WAITING, outbox.stateOf("c1", "waiting"))
    assertEquals(SendState.FAILED, outbox.stateOf("c1", "failed"))
    assertNull(outbox.get("c1", "missing"))
  }

  @Test
  fun aFailedAttemptIsCountedAndTheEntryWaitsForTheNextOne() = runTest {
    val outbox = newOutbox()
    outbox.add("c1", picture("m1"), source())

    assertEquals(1, outbox.failAttempt("c1", "m1", maxAttempts = 3))
    assertEquals(SendState.WAITING, outbox.stateOf("c1", "m1"))
    assertEquals(2, outbox.failAttempt("c1", "m1", maxAttempts = 3))
    assertEquals(SendState.WAITING, outbox.stateOf("c1", "m1"))

    val json = jsonOnDisk("c1", "m1")
    assertEquals(2, json.getInt("failedAttempts"))
    assertEquals("WAITING", json.getString("state"))
  }

  @Test
  fun theLastAllowedFailedAttemptMakesTheEntryFailed() = runTest {
    val outbox = newOutbox()
    val maxAttempts = MediaSender.MAX_FAILED_ATTEMPTS
    outbox.add("c1", picture("m1"), source())
    repeat(maxAttempts - 1) { outbox.failAttempt("c1", "m1", maxAttempts) }
    assertEquals(SendState.WAITING, outbox.stateOf("c1", "m1"))

    val count = outbox.failAttempt("c1", "m1", maxAttempts)

    assertEquals(maxAttempts, count)
    assertEquals(SendState.FAILED, outbox.stateOf("c1", "m1"))
    // the count and the state are written together
    val json = jsonOnDisk("c1", "m1")
    assertEquals(maxAttempts, json.getInt("failedAttempts"))
    assertEquals("FAILED", json.getString("state"))
  }

  @Test
  fun aFailedAttemptIsRecordedOnlyWhileSending() = runTest {
    val outbox = newOutbox()
    outbox.add("c1", picture("waiting"), source())
    outbox.add("c1", picture("failed"), source())
    outbox.failForGood("c1", "failed")

    assertNull(outbox.recordFailedAttempt("c1", "waiting", maxAttempts = 1))
    assertNull(outbox.recordFailedAttempt("c1", "failed", maxAttempts = 1))
    assertNull(outbox.recordFailedAttempt("c1", "missing", maxAttempts = 1))

    assertEquals(SendState.WAITING, outbox.stateOf("c1", "waiting"))
    assertEquals(SendState.FAILED, outbox.stateOf("c1", "failed"))
    assertEquals(0, jsonOnDisk("c1", "waiting").getInt("failedAttempts"))
    assertEquals(0, jsonOnDisk("c1", "failed").getInt("failedAttempts"))
  }

  @Test
  fun recordFailedAttemptRefusesAMaxBelowOne() = runTest {
    val outbox = newOutbox()
    outbox.add("c1", picture("m1"), source())
    outbox.startSending("c1", "m1")

    for (badMax in listOf(0, -1)) {
      val error = runCatching { outbox.recordFailedAttempt("c1", "m1", badMax) }.exceptionOrNull()
      assertTrue("max $badMax", error is IllegalArgumentException)
    }
    assertEquals(SendState.SENDING, outbox.stateOf("c1", "m1"))
    assertEquals(0, jsonOnDisk("c1", "m1").getInt("failedAttempts"))
  }

  @Test
  fun markFailedEndsAnAttemptAtOnceWithoutCountingIt() = runTest {
    val outbox = newOutbox()
    outbox.add("c1", picture("m1"), source())
    outbox.failAttempt("c1", "m1")
    outbox.startSending("c1", "m1")

    // e.g. permission denied: retrying can't help, whatever the count
    assertTrue(outbox.markFailed("c1", "m1"))

    assertEquals(SendState.FAILED, outbox.stateOf("c1", "m1"))
    val json = jsonOnDisk("c1", "m1")
    assertEquals("FAILED", json.getString("state"))
    assertEquals(1, json.getInt("failedAttempts"))
  }

  @Test
  fun markFailedLeavesAnEntryThatIsntSendingAlone() = runTest {
    val outbox = newOutbox()
    outbox.add("c1", picture("waiting"), source())
    outbox.add("c1", picture("failed"), source())
    outbox.failForGood("c1", "failed")

    assertFalse(outbox.markFailed("c1", "waiting"))
    assertFalse(outbox.markFailed("c1", "failed"))
    assertFalse(outbox.markFailed("c1", "missing"))

    assertEquals(SendState.WAITING, outbox.stateOf("c1", "waiting"))
    assertEquals(SendState.FAILED, outbox.stateOf("c1", "failed"))
    assertNull(outbox.get("c1", "missing"))
  }

  @Test
  fun retryPutsAFailedEntryBackToWaitingWithAllItsAttempts() = runTest {
    val outbox = newOutbox()
    outbox.add("c1", picture("m1"), source())
    outbox.failAttempt("c1", "m1", maxAttempts = 2)
    outbox.failAttempt("c1", "m1", maxAttempts = 2)
    assertEquals(SendState.FAILED, outbox.stateOf("c1", "m1"))

    assertTrue(outbox.retry("c1", "m1"))

    assertEquals(SendState.WAITING, outbox.stateOf("c1", "m1"))
    val json = jsonOnDisk("c1", "m1")
    assertEquals("WAITING", json.getString("state"))
    assertEquals(0, json.getInt("failedAttempts"))
    // a new series: one failure no longer reaches the limit
    assertEquals(1, outbox.failAttempt("c1", "m1", maxAttempts = 2))
    assertEquals(SendState.WAITING, outbox.stateOf("c1", "m1"))
  }

  @Test
  fun retryChangesOnlyAFailedEntry() = runTest {
    val outbox = newOutbox()
    outbox.add("c1", picture("waiting"), source())
    outbox.add("c1", picture("sending"), source())
    outbox.failAttempt("c1", "sending")
    outbox.startSending("c1", "sending")

    assertFalse(outbox.retry("c1", "waiting"))
    assertFalse(outbox.retry("c1", "sending"))
    assertFalse(outbox.retry("c1", "missing"))

    assertEquals(SendState.WAITING, outbox.stateOf("c1", "waiting"))
    assertEquals(SendState.SENDING, outbox.stateOf("c1", "sending"))
    assertEquals("its count is kept", 1, jsonOnDisk("c1", "sending").getInt("failedAttempts"))
  }

  @Test
  fun aSecondRetryDoesNothing() = runTest {
    val outbox = newOutbox()
    outbox.add("c1", picture("m1"), source())
    outbox.failForGood("c1", "m1")

    // a quick double tap on "retry": the second call finds the entry WAITING already, so the
    // caller schedules a single send
    assertTrue(outbox.retry("c1", "m1"))
    assertFalse(outbox.retry("c1", "m1"))

    assertEquals(SendState.WAITING, outbox.stateOf("c1", "m1"))
  }

  @Test
  fun aStateChangeThatCantBeWrittenKeepsTheOldOne() = runTest {
    val outbox = newOutbox()
    outbox.add("c1", picture("waiting"), source())
    outbox.add("c1", picture("sending"), source())
    outbox.failAttempt("c1", "sending")
    outbox.startSending("c1", "sending")
    outbox.add("c1", picture("failed"), source())
    outbox.failForGood("c1", "failed")
    // something in the way of every new .json file
    for (id in listOf("waiting", "sending", "failed")) makeUndeletable(fileOnDisk("c1", "$id.json"))

    val failedWrites =
        listOf(
            runCatching { outbox.startSending("c1", "waiting") },
            // would make it FAILED with a count of 2, if it could be written
            runCatching { outbox.recordFailedAttempt("c1", "sending", maxAttempts = 2) },
            runCatching { outbox.resetSending("c1", "sending") },
            runCatching { outbox.markFailed("c1", "sending") },
            runCatching { outbox.retry("c1", "failed") },
        )

    failedWrites.forEach { assertTrue(it.exceptionOrNull() is IOException) }
    assertEquals(SendState.WAITING, outbox.stateOf("c1", "waiting"))
    assertEquals(SendState.SENDING, outbox.stateOf("c1", "sending"))
    assertEquals(SendState.FAILED, outbox.stateOf("c1", "failed"))
    // once the disk works again, the count goes on from the old one
    fileOnDisk("c1", "sending.json").deleteRecursively()
    assertEquals(2, outbox.recordFailedAttempt("c1", "sending", MediaSender.MAX_FAILED_ATTEMPTS))
  }

  @Test
  fun removeDeletesTheEntryAndItsFile() = runTest {
    val outbox = newOutbox()
    outbox.add("c1", picture("m1"), source())
    outbox.add("c1", picture("m2"), source())

    outbox.remove("c1", "m1")

    assertNull(outbox.get("c1", "m1"))
    assertNull(outbox.fileOf("c1", "m1"))
    assertFalse(fileOnDisk("c1", "m1.json").exists())
    assertFalse(fileOnDisk("c1", "m1.bin").exists())
    assertEquals(listOf("m2"), outbox.idsOf("c1"))
    assertEquals(listOf("m2"), newOutbox().idsOf("c1"))
  }

  @Test
  fun removeWorksOnceTheFileWasMovedAwayAndIgnoresMissingEntries() = runTest {
    val outbox = newOutbox()
    outbox.add("c1", picture("m1"), source())
    // what a send does: the file goes to the cache before the entry is removed
    outbox.fileOf("c1", "m1")!!.renameTo(File(tmp.root, "cached.jpg"))

    outbox.remove("c1", "m1")
    outbox.remove("c1", "m1") // already gone
    outbox.remove("c9", "m9") // never there

    assertNull(outbox.get("c1", "m1"))
  }

  @Test
  fun aRemoveThatCantDeleteTheEntryKeepsIt() = runTest {
    val outbox = newOutbox()
    outbox.add("c1", picture("m1"), source())
    makeUndeletable(fileOnDisk("c1", "m1.json"))

    val error = runCatching { outbox.remove("c1", "m1") }.exceptionOrNull()

    assertTrue(error is IOException)
    assertEquals(listOf("m1"), outbox.idsOf("c1"))
  }

  @Test
  fun aRemoveThatCantDeleteTheFileReportsItButForgetsTheEntry() = runTest {
    val outbox = newOutbox()
    outbox.add("c1", picture("m1"), source())
    makeUndeletable(fileOnDisk("c1", "m1.bin"))

    val error = runCatching { outbox.remove("c1", "m1") }.exceptionOrNull()

    // a private file is still on the phone: the caller must know
    assertTrue(error is IOException)
    assertNull(outbox.get("c1", "m1"))
    assertFalse(fileOnDisk("c1", "m1.json").exists())
  }

  @Test
  fun clearOfACircleKeepsTheOthers() = runTest {
    val outbox = newOutbox()
    outbox.add("c1", picture("m1"), source())
    outbox.add("c1", text("m2"), null)
    outbox.add("c10", picture("m3"), source())

    outbox.clear("c1")

    assertEquals(emptyList<String>(), outbox.idsOf("c1"))
    assertEquals(listOf("m3"), outbox.idsOf("c10"))
    assertFalse(File(dir, "c1").exists())
    assertEquals(emptyList<String>(), newOutbox().idsOf("c1"))
    // a cleared circle can be used again
    outbox.add("c1", picture("m1"), source())
    assertEquals(listOf("m1"), outbox.idsOf("c1"))
  }

  @Test
  fun clearRemovesEveryCircle() = runTest {
    val outbox = newOutbox()
    outbox.add("c1", picture("m1"), source())
    outbox.add("c2", picture("m2"), source())

    outbox.clear()

    assertEquals(emptyList<String>(), outbox.idsOf("c1"))
    assertEquals(emptyList<String>(), outbox.idsOf("c2"))
    assertEquals(emptyList<File>(), dir.listFiles().orEmpty().toList())
    assertEquals(emptyList<String>(), newOutbox().idsOf("c2"))
  }

  @Test
  fun aClearThatCantDeleteEverythingTriesAllAndKeepsWhatIsLeft() = runTest {
    val outbox = newOutbox()
    outbox.add("c1", picture("m1"), source())
    outbox.add("c1", picture("m2"), source())
    outbox.add("c2", picture("m3"), source())
    makeUndeletable(fileOnDisk("c1", "m1.json"))

    val error = runCatching { outbox.clear() }.exceptionOrNull()

    assertTrue(error is IOException)
    assertEquals(listOf("m1"), outbox.idsOf("c1")) // its .json is still there
    assertEquals(emptyList<String>(), outbox.idsOf("c2"))
    assertFalse(fileOnDisk("c1", "m2.json").exists())
  }

  @Test
  fun clearRefusesAnInvalidCircleId() = runTest {
    val outbox = newOutbox()
    outbox.add("c1", picture("m1"), source())

    for (badId in listOf("", ".", "..", "c1/..")) {
      val error = runCatching { outbox.clear(badId) }.exceptionOrNull()
      assertTrue("'$badId'", error is IllegalArgumentException)
    }
    assertEquals(listOf("m1"), outbox.idsOf("c1"))
  }

  @Test
  fun entriesAndTheirStateSurviveARestart() = runTest {
    val first = newOutbox()
    first.add("c1", picture("m1"), source("bytes of m1"))
    first.add("c1", text("m2"), null)
    first.add("c1", picture("m3"), source())
    first.failForGood("c1", "m3")

    val restarted = newOutbox()

    assertEquals(OutboxEntry(picture("m1"), SendState.WAITING), restarted.get("c1", "m1"))
    assertEquals(OutboxEntry(text("m2"), SendState.WAITING), restarted.get("c1", "m2"))
    // a failed media isn't retried on its own, even after a restart
    assertEquals(SendState.FAILED, restarted.get("c1", "m3")?.state)
    assertEquals("bytes of m1", restarted.fileOf("c1", "m1")!!.readText())
  }

  @Test
  fun theFailedAttemptCountSurvivesARestart() = runTest {
    val first = newOutbox()
    first.add("c1", picture("m1"), source())
    first.failAttempt("c1", "m1", maxAttempts = 3)
    first.failAttempt("c1", "m1", maxAttempts = 3)

    val restarted = newOutbox()

    // failures spread over several runs of the app still add up
    assertEquals(3, restarted.failAttempt("c1", "m1", maxAttempts = 3))
    assertEquals(SendState.FAILED, restarted.stateOf("c1", "m1"))
  }

  @Test
  fun aSendInterruptedByARestartIsWaitingAgainAndNotCounted() = runTest {
    val first = newOutbox()
    first.add("c1", picture("m1"), source())
    first.failAttempt("c1", "m1")
    first.startSending("c1", "m1")

    val restarted = newOutbox()

    assertEquals(SendState.WAITING, restarted.stateOf("c1", "m1"))
    // on disk too: memory and disk hold the same state
    val json = jsonOnDisk("c1", "m1")
    assertEquals("WAITING", json.getString("state"))
    assertEquals(1, json.getInt("failedAttempts"))
    assertEquals("picture", json.getJSONObject("item").getString("type")) // the media is kept
  }

  @Test
  fun aMissingOrInvalidFailedAttemptCountReadsAsZero() = runTest {
    val first = newOutbox()
    for (id in listOf("old", "negative", "text")) first.add("c1", picture(id), source())
    // an entry written before the count existed, and two damaged ones
    rewriteJson("c1", "old") { remove("failedAttempts") }
    rewriteJson("c1", "negative") { put("failedAttempts", -3) }
    rewriteJson("c1", "text") { put("failedAttempts", "many") }

    val restarted = newOutbox()

    assertEquals(listOf("negative", "old", "text"), restarted.idsOf("c1").sorted())
    for (id in listOf("old", "negative", "text")) {
      assertEquals(id, 1, restarted.failAttempt("c1", id))
    }
  }

  @Test
  fun anEntryLoadedAtOrOverTheLimitFailsAtItsNextFailedAttempt() = runTest {
    val first = newOutbox()
    first.add("c1", picture("at"), source())
    first.add("c1", picture("over"), source())
    // e.g. a later version lowered the limit to 5, below counts already reached
    rewriteJson("c1", "at") { put("failedAttempts", 5) }
    rewriteJson("c1", "over") { put("failedAttempts", 7) }

    val restarted = newOutbox()

    for ((id, count) in listOf("at" to 6, "over" to 8)) {
      // not failed when loaded: it still gets one more attempt, which may succeed
      assertEquals(id, SendState.WAITING, restarted.stateOf("c1", id))
      assertEquals(id, count, restarted.failAttempt("c1", id, maxAttempts = 5))
      assertEquals(id, SendState.FAILED, restarted.stateOf("c1", id))
    }
  }

  @Test
  fun aRestartRemovesWhatInterruptedCallsLeftBehind() = runTest {
    val first = newOutbox()
    first.add("c1", picture("m1"), source())
    // a half-written file, and the file of an add that failed before its .json
    val tmpFile = fileOnDisk("c1", "entry123.tmp").apply { writeText("half") }
    val orphanBin = fileOnDisk("c1", "m2.bin").apply { writeText("orphan") }

    val restarted = newOutbox()

    assertEquals(listOf("m1"), restarted.idsOf("c1"))
    assertFalse(tmpFile.exists())
    assertFalse(orphanBin.exists())
    assertTrue(fileOnDisk("c1", "m1.bin").exists())
  }

  @Test
  fun anEntryThatCantBeReadIsSkippedButKept() = runTest {
    val first = newOutbox()
    first.add("c1", picture("m1"), source())
    // an entry from a newer app version, and a corrupted one
    fileOnDisk("c1", "m2.json")
        .writeText("""{"state":"WAITING","item":{"type":"video","authorId":"u1"}}""")
    fileOnDisk("c1", "m2.bin").writeText("video bytes")
    fileOnDisk("c1", "m3.json").writeText("{not json")

    val restarted = newOutbox()

    assertEquals(listOf("m1"), restarted.idsOf("c1"))
    // the outbox may hold the only copy of these media: nothing is deleted
    assertTrue(fileOnDisk("c1", "m2.json").exists())
    assertTrue(fileOnDisk("c1", "m2.bin").exists())
    assertTrue(fileOnDisk("c1", "m3.json").exists())
  }
}
