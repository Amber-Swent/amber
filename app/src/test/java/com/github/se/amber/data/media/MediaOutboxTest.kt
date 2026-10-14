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

  private fun fileOnDisk(circleId: String, name: String) = File(File(dir, circleId), name)

  /** Makes [file] impossible to delete, on every OS: a folder that isn't empty. */
  private fun makeUndeletable(file: File) {
    file.delete()
    File(file, "inside").apply { parentFile!!.mkdirs() }.writeText("x")
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
    outbox.setState("c1", "m1", SendState.SENDING)
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
            emptyList(),
        ),
        seen,
    )
  }

  @Test
  fun entriesAndTheirStateSurviveARestart() = runTest {
    val first = newOutbox()
    first.add("c1", picture("m1"), source("bytes of m1"))
    first.add("c1", text("m2"), null)
    first.add("c1", picture("m3"), source())
    first.setState("c1", "m3", SendState.FAILED)

    val restarted = newOutbox()

    assertEquals(OutboxEntry(picture("m1"), SendState.WAITING), restarted.get("c1", "m1"))
    assertEquals(OutboxEntry(text("m2"), SendState.WAITING), restarted.get("c1", "m2"))
    // a failed media isn't retried on its own, even after a restart
    assertEquals(SendState.FAILED, restarted.get("c1", "m3")?.state)
    assertEquals("bytes of m1", restarted.fileOf("c1", "m1")!!.readText())
  }

  @Test
  fun aSendInterruptedByARestartIsWaitingAgain() = runTest {
    val first = newOutbox()
    first.add("c1", picture("m1"), source())
    first.setState("c1", "m1", SendState.SENDING)

    val restarted = newOutbox()

    assertEquals(SendState.WAITING, restarted.get("c1", "m1")?.state)
    // on disk too: memory and disk hold the same state
    val json = JSONObject(fileOnDisk("c1", "m1.json").readText())
    assertEquals("WAITING", json.getString("state"))
    assertEquals("picture", json.getJSONObject("item").getString("type")) // the media is kept
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
  fun setStateOfAMissingEntryChangesNothing() = runTest {
    val outbox = newOutbox()
    outbox.add("c1", picture("m1"), source())

    assertFalse(outbox.setState("c1", "m2", SendState.FAILED))
    assertFalse(outbox.setState("c2", "m1", SendState.FAILED))
    assertTrue(outbox.setState("c1", "m1", SendState.WAITING)) // already WAITING
    assertEquals(listOf("m1"), outbox.idsOf("c1"))
    assertNull(outbox.get("c1", "m2"))
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
}
