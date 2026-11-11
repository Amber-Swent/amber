// Written by Alaabrine, with assistance from Cursor (Grok).

package com.github.se.amber.data.circle

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Checks [DataStoreActiveCircleRepository] against issue #294: the default circle, a kept choice, a
 * fallback when that circle is gone, a clear on sign-out, and a collector that sees a switch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreActiveCircleRepositoryTest {

  @Test
  fun defaultIsTheFirstNonBlankCircle() = runTest {
    val repository = repository()

    val active = repository.activeCircleId("caregiver", listOf(" ", "circle-a", "circle-b")).first()

    assertEquals("circle-a", active)
  }

  @Test
  fun noUsableCircleEmitsNull() = runTest {
    val repository = repository()

    assertNull(repository.activeCircleId("caregiver", emptyList()).first())
    assertNull(repository.activeCircleId("caregiver", listOf("", " ")).first())
    assertNull(repository.activeCircleId(" ", listOf("circle-a")).first())
  }

  @Test
  fun choiceIsKept() = runTest {
    val store = newDataStore()
    val repository = DataStoreActiveCircleRepository(store)
    val circles = listOf("circle-a", "circle-b")

    repository.setActiveCircle("caregiver", "circle-b")

    assertEquals("circle-b", repository.activeCircleId("caregiver", circles).first())
    val reopened = DataStoreActiveCircleRepository(store)
    assertEquals("circle-b", reopened.activeCircleId("caregiver", circles).first())
  }

  @Test
  fun fallsBackToTheFirstCircleWhenTheStoredOneIsRemoved() = runTest {
    val repository = repository()
    repository.setActiveCircle("caregiver", "circle-b")

    val whileGone = repository.activeCircleId("caregiver", listOf("circle-a")).first()
    val whenItReturns =
        repository.activeCircleId("caregiver", listOf("circle-a", "circle-b")).first()

    assertEquals("circle-a", whileGone)
    assertEquals("circle-b", whenItReturns)
  }

  @Test
  fun clearOnSignOutForgetsOnlyThatUsersChoice() = runTest {
    val repository = repository()
    repository.setActiveCircle("caregiver", "circle-b")
    repository.setActiveCircle("other", "circle-d")

    repository.clear("caregiver")
    repository.clear(" ")

    assertEquals(
        "circle-a",
        repository.activeCircleId("caregiver", listOf("circle-a", "circle-b")).first(),
    )
    assertEquals(
        "circle-d",
        repository.activeCircleId("other", listOf("circle-c", "circle-d")).first(),
    )
  }

  @Test
  fun choiceIsStoredPerUid() = runTest {
    val repository = repository()
    val circles = listOf("circle-a", "circle-b", "circle-c")
    repository.setActiveCircle("caregiver", "circle-b")
    repository.setActiveCircle("patient", "circle-c")

    assertEquals("circle-b", repository.activeCircleId("caregiver", circles).first())
    assertEquals("circle-c", repository.activeCircleId("patient", circles).first())
    assertEquals("circle-a", repository.activeCircleId("someone-else", circles).first())
  }

  @Test
  fun collectorsSeeTheCircleChangeAfterASwitch() = runTest {
    val repository = repository()
    val seen = mutableListOf<String?>()
    val circles = listOf("circle-a", "circle-b")
    val collecting = backgroundScope.launch {
      repository.activeCircleId("caregiver", circles).collect { seen += it }
    }
    advanceUntilIdle()

    repository.setActiveCircle("caregiver", "circle-b")
    advanceUntilIdle()
    collecting.cancel()

    assertEquals(listOf("circle-a", "circle-b"), seen.toList())
  }

  @Test
  fun readFailureFallsBackToTheFirstCircle() = runTest {
    val broken =
        object : DataStore<Preferences> {
          override val data: Flow<Preferences> = flow { throw IOException("disk") }

          override suspend fun updateData(
              transform: suspend (t: Preferences) -> Preferences
          ): Preferences = error("unused")
        }
    val repository = DataStoreActiveCircleRepository(broken)

    val active = repository.activeCircleId("caregiver", listOf("circle-a", "circle-b")).first()

    assertEquals("circle-a", active)
  }

  @Test
  fun blankUidOrCircleIdDoesNotReplaceTheStoredChoice() = runTest {
    val repository = repository()
    val circles = listOf("circle-a", "circle-b")
    repository.setActiveCircle("caregiver", "circle-b")

    repository.setActiveCircle(" ", "circle-a")
    repository.setActiveCircle("caregiver", " ")

    assertEquals("circle-b", repository.activeCircleId("caregiver", circles).first())
  }

  private fun TestScope.repository(): DataStoreActiveCircleRepository =
      DataStoreActiveCircleRepository(newDataStore())

  private fun TestScope.newDataStore(): DataStore<Preferences> {
    val directory: Path = createTempDirectory(prefix = "active-circle")
    return PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { directory.resolve("active_circle.preferences_pb").toFile() },
    )
  }
}
