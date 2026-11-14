// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.location

import com.github.se.amber.model.location.TestPositions.LAUSANNE_FLON
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeLocationUpdatesRepositoryTest {

  private val here = LocationUpdate.Position(LAUSANNE_FLON)

  @Test
  fun `gives the permission, then Searching and each update, counting the running collections`() =
      runTest(UnconfinedTestDispatcher()) {
        val repository = FakeLocationUpdatesRepository(permission = false)
        assertFalse(repository.hasPermission())
        repository.permission = true
        assertTrue(repository.hasPermission())

        val received = mutableListOf<LocationUpdate>()
        val job = launch { repository.observeLocation().collect { received.add(it) } }
        assertEquals(1, repository.activeCollectors)

        repository.updates.value = here
        repository.updates.value = LocationUpdate.LocationOff
        job.cancel()

        assertEquals(listOf(LocationUpdate.Searching, here, LocationUpdate.LocationOff), received)
        assertEquals(0, repository.activeCollectors)
      }

  @Test
  fun `a failure stops the running collection after its updates, and the next one at once`() =
      runTest(UnconfinedTestDispatcher()) {
        val denied = SecurityException("permission taken back")
        val repository = FakeLocationUpdatesRepository()
        val received = mutableListOf<LocationUpdate>()
        var caught: Throwable? = null
        launch { repository.observeLocation().catch { caught = it }.collect { received.add(it) } }
        repository.updates.value = here

        repository.failure.value = denied

        assertEquals(listOf(LocationUpdate.Searching, here), received)
        // merge rethrows a copy of the error (coroutines' stack trace recovery)
        assertSameError(denied, caught)
        assertEquals(0, repository.activeCollectors)

        var caughtAgain: Throwable? = null
        repository.observeLocation().catch { caughtAgain = it }.collect()
        assertSameError(denied, caughtAgain)

        repository.failure.value = null
        assertEquals(here, repository.observeLocation().first())
      }

  private fun assertSameError(expected: Throwable, actual: Throwable?) {
    assertEquals(expected::class, actual?.let { it::class })
    assertEquals(expected.message, actual?.message)
  }
}
