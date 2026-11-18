// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.location

import com.github.se.amber.model.location.MediaMarker
import com.github.se.amber.model.location.TestPlaces.GENEVA
import com.github.se.amber.model.location.TestPlaces.LAUSANNE
import com.github.se.amber.model.location.toMediaMarkers
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ApprovedMediaLocationRepositoryTest {

  private val mediaRepository = mockk<MediaRepository>()
  private val repository = ApprovedMediaLocationRepository(mediaRepository)

  private val picture = MediaItem.Picture(id = "p1", location = LAUSANNE)
  private val audio = MediaItem.Audio(id = "a1", location = GENEVA)
  private val textWithoutPlace = MediaItem.Text(id = "t1")

  @Test
  fun givesTheLivePinsOfTheApprovedMediaOnlyWhenThePinsChange() =
      runTest(UnconfinedTestDispatcher()) {
        val media = MutableStateFlow<List<MediaItem>>(listOf(picture, textWithoutPlace))
        every { mediaRepository.observeApprovedMedia("circle1") } returns media
        val emitted = mutableListOf<List<MediaMarker>>()
        val job = launch { repository.observeMarkers("circle1").toList(emitted) }

        media.value = listOf(audio, picture, textWithoutPlace) // a new pin
        media.value = listOf(MediaItem.Text(id = "t2"), audio, picture) // no pin changes
        job.cancel()

        assertEquals(
            listOf(listOf(picture).toMediaMarkers(), listOf(audio, picture).toMediaMarkers()),
            emitted,
        )
        verify(exactly = 1) { mediaRepository.observeApprovedMedia("circle1") }
      }

  @Test
  fun stopsWithTheMediasOwnError() = runTest {
    val denied = IllegalStateException("permission denied")
    every { mediaRepository.observeApprovedMedia("circle1") } returns flow { throw denied }
    var caught: Throwable? = null

    repository.observeMarkers("circle1").catch { caught = it }.collect()

    assertSame(denied, caught)
  }
}
