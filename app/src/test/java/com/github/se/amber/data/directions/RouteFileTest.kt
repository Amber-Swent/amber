// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.directions

import com.github.se.amber.data.directions.TestRoutes.CATHEDRAL
import com.github.se.amber.data.directions.TestRoutes.FLON
import com.github.se.amber.data.directions.TestRoutes.FLON_TO_CATHEDRAL
import com.github.se.amber.model.directions.Maneuver
import com.github.se.amber.model.directions.Route
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Checks [RouteFile] on a real folder. Robolectric gives it Android's org.json. */
@RunWith(RobolectricTestRunner::class)
class RouteFileTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private fun routeFile(file: File = File(tempFolder.root, LAST_ROUTE_FILE_NAME)) =
      RouteFile(file, Dispatchers.Unconfined)

  @Test
  fun keepsTheLastRouteSavedAndNothingElse() = runBlocking {
    val file = routeFile()
    assertNull(file.load())

    file.save(FLON_TO_CATHEDRAL)
    assertEquals(FLON_TO_CATHEDRAL, file.load())

    val back: Route =
        FLON_TO_CATHEDRAL.copy(destination = FLON, points = FLON_TO_CATHEDRAL.points.reversed())
    file.save(back)
    assertEquals(back, routeFile().load())
    // No temporary file left behind
    assertEquals(listOf(LAST_ROUTE_FILE_NAME), tempFolder.root.list().orEmpty().toList())
  }

  @Test
  fun aBrokenOrUnreadableFileReadsAsNoneAnUnknownManeuverAsUnknownAndAFailedSaveSaysSo() =
      runBlocking {
        val stored = File(tempFolder.root, LAST_ROUTE_FILE_NAME)
        stored.writeText("""{"destination": {"lat": ${CATHEDRAL.lat}}""")
        assertNull(routeFile(stored).load())
        // Can't be read at all
        assertNull(routeFile(tempFolder.newFolder("unreadable")).load())

        // A maneuver this version doesn't know, e.g. from a newer one: the rest of the route still
        // works
        routeFile(stored).save(FLON_TO_CATHEDRAL)
        val turn = FLON_TO_CATHEDRAL.steps.last().maneuver.name
        stored.writeText(stored.readText().replace("\"$turn\"", "\"JUMP\""))
        assertEquals(
            Maneuver.UNKNOWN,
            routeFile(stored).load()?.steps?.last()?.maneuver,
        )

        // A folder where the file should go: it can't be replaced
        val blocked = tempFolder.newFolder("blocked")
        File(blocked, "inside").writeText("x")
        assertThrows(IllegalStateException::class.java) {
          runBlocking { routeFile(blocked).save(FLON_TO_CATHEDRAL) }
        }
        assertFalse(File(tempFolder.root, "blocked.tmp").exists())
      }
}
