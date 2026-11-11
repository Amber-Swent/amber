// Written by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.media.audio

import com.github.se.amber.data.media.audio.WAVEFORM_BARS
import com.github.se.amber.ui.theme.AUDIO_WAVEFORM_MIN_LEVEL
import com.github.se.amber.ui.theme.HALF
import org.junit.Assert.assertEquals
import org.junit.Test

/** Checks which levels and played portion each waveform state produces. */
class WaveformLogicTest {

  private val levels = listOf(0.2f, 0.8f)

  @Test
  fun noneHasNoLevels() = assertEquals(emptyList<Float>(), waveformLevels(AudioWaveformState.None))

  @Test
  fun recordingKeepsItsLevels() =
      assertEquals(levels, waveformLevels(AudioWaveformState.Recording(levels)))

  @Test
  fun recordedKeepsItsLevels() =
      assertEquals(levels, waveformLevels(AudioWaveformState.Recorded(levels)))

  @Test
  fun listeningKeepsItsLevels() =
      assertEquals(levels, waveformLevels(AudioWaveformState.Listening(levels, HALF)))

  @Test
  fun listeningWithUnknownLevelsGetsAFlatTrack() =
      assertEquals(
          List(WAVEFORM_BARS) { AUDIO_WAVEFORM_MIN_LEVEL },
          waveformLevels(AudioWaveformState.Listening()),
      )

  @Test
  fun recordedWithNoLevelsStaysEmpty() =
      assertEquals(emptyList<Float>(), waveformLevels(AudioWaveformState.Recorded()))

  @Test
  fun playedFractionBelowZeroIsZero() =
      assertEquals(0f, playedFraction(AudioWaveformState.Listening(playedFraction = -1f)))

  @Test
  fun playedFractionAboveOneIsOne() =
      assertEquals(1f, playedFraction(AudioWaveformState.Listening(playedFraction = 1.5f)))

  @Test
  fun playedFractionInsideTheRangeIsKept() =
      assertEquals(0.4f, playedFraction(AudioWaveformState.Listening(playedFraction = 0.4f)))

  @Test
  fun recordingHasNothingPlayed() =
      assertEquals(0f, playedFraction(AudioWaveformState.Recording(levels)))

  @Test
  fun recordedHasNothingPlayed() =
      assertEquals(0f, playedFraction(AudioWaveformState.Recorded(levels)))
}
