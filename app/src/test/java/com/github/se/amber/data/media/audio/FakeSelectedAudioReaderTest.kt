// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.data.media.audio

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Checks that [FakeSelectedAudioReader] follows the [SelectedAudioReader] contract and that its
 * test helpers work, since the ViewModel tests rely on both.
 */
class FakeSelectedAudioReaderTest {

  @get:Rule val folder = TemporaryFolder()

  private val reader by lazy { FakeSelectedAudioReader(folder.root, durationMs = 7_000L) }

  @Test
  fun readReturnsANewM4aFileWithTheDuration() = runTest {
    val first = reader.read("content://audio/1")
    val second = reader.read("content://audio/2")

    assertTrue(first.file.name.endsWith(".m4a"))
    assertEquals(folder.root, first.file.parentFile)
    assertEquals("audio from content://audio/1", first.file.readText())
    assertEquals(7_000L, first.durationMs)
    assertNotEquals(first.file, second.file)
    assertEquals(listOf("content://audio/1", "content://audio/2"), reader.reads)
  }

  @Test
  fun theWaveformIsEmptyUnlessTheTestSetsOne() = runTest {
    assertEquals(emptyList<Float>(), reader.read("content://audio/1").waveform)

    val waveform = List(WAVEFORM_BARS) { it / (WAVEFORM_BARS - 1f) }
    reader.waveform = waveform

    assertEquals(waveform, reader.read("content://audio/2").waveform)
  }

  @Test
  fun readThrowsTheErrorItIsGiven() = runTest {
    val notM4a = IllegalArgumentException("not an .m4a")
    reader.error = notM4a

    assertSame(notM4a, runCatching { reader.read("content://audio.mp3") }.exceptionOrNull())
    assertEquals(listOf("content://audio.mp3"), reader.reads)
    assertEquals(0, folder.root.listFiles()!!.size)
  }
}
