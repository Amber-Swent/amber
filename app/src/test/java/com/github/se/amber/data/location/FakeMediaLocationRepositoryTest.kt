// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.location

import com.github.se.amber.model.location.MediaMarker
import com.github.se.amber.model.location.TestPlaces.LAUSANNE
import com.github.se.amber.model.location.toMediaMarkers
import com.github.se.amber.model.media.MediaItem
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeMediaLocationRepositoryTest {

  private val pins = listOf(MediaItem.Picture(id = "p1", location = LAUSANNE)).toMediaMarkers()

  @Test
  fun givesEachCircleItsOwnPinsLiveAndRecordsTheCirclesAskedFor() = runTest {
    val repository = FakeMediaLocationRepository(mapOf("circle1" to pins))

    assertEquals(pins, repository.observeMarkers("circle1").first())
    assertTrue(repository.observeMarkers("circle2").first().isEmpty())

    repository.markers.value = mapOf("circle2" to pins)
    assertEquals(pins, repository.observeMarkers("circle2").first())
    assertEquals(listOf("circle1", "circle2", "circle2"), repository.observedCircleIds)
  }

  @Test
  fun aFailingCircleStopsWithItsErrorBeforeAnyPinOthersStillWork() = runTest {
    val denied = IllegalStateException("permission denied")
    val repository = FakeMediaLocationRepository(mapOf("circle1" to pins, "circle2" to pins))
    repository.failures["circle1"] = denied
    val emitted = mutableListOf<List<MediaMarker>>()
    var caught: Throwable? = null

    repository.observeMarkers("circle1").onEach { emitted.add(it) }.catch { caught = it }.collect()

    assertTrue(emitted.isEmpty())
    assertSame(denied, caught)
    assertEquals(pins, repository.observeMarkers("circle2").first())
  }
}
