// Written by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.media.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.github.se.amber.data.media.audio.WAVEFORM_BARS
import com.github.se.amber.ui.patient.DisplayPatientIconButton
import com.github.se.amber.ui.theme.AUDIO_WAVEFORM_BAR_FRACTION
import com.github.se.amber.ui.theme.AUDIO_WAVEFORM_HEIGHT
import com.github.se.amber.ui.theme.AUDIO_WAVEFORM_MIN_LEVEL
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.HALF
import com.github.se.amber.ui.theme.WEIGHT_ONE

private const val NO_PLAYED_FRACTION = 0f

/** Test tags owned by [MediaAudio]. Callers can pass their own. */
object MediaAudioTestTags {
  const val LEFT_BUTTON = "media_audio_left_button"
  const val RIGHT_BUTTON = "media_audio_right_button"
  const val WAVEFORM = "media_audio_waveform"
}

/**
 * What the middle of [MediaAudio] draws. The caller chooses the state. This row does not record or
 * play anything.
 */
sealed interface AudioWaveformState {
  /** Nothing has been recorded. The row shows an empty waveform. */
  data object None : AudioWaveformState

  /**
   * A recording in progress. [levels] are the bars so far, from the left. Later levels appear as
   * they arrive.
   */
  data class Recording(val levels: List<Float> = emptyList()) : AudioWaveformState

  /** The finished waveform. It does not move, and no part of it is marked as played. */
  data class Recorded(val levels: List<Float> = emptyList()) : AudioWaveformState

  /**
   * The finished waveform while it is being heard. The portion up to [playedFraction] is drawn in
   * secondary. [playedFraction] is from 0 to 1.
   */
  data class Listening(
      val levels: List<Float> = emptyList(),
      val playedFraction: Float = NO_PLAYED_FRACTION,
  ) : AudioWaveformState
}

/**
 * One audio row: a button, the waveform, and an optional button.
 *
 * The caller passes each icon, what TalkBack reads, and what a click does, so the same row can show
 * play, pause, stop, delete, or anything else. Leave [rightIcon] unset to hide the right button.
 * [waveform] is drawn as given.
 *
 * @param modifier modifier applied to the row.
 * @param waveform which of the four waveform states to draw.
 * @param leftIcon icon of the left button.
 * @param leftDescription what TalkBack reads for the left button.
 * @param onLeftClick called when the left button is clicked.
 * @param leftEnabled whether the left button accepts clicks.
 * @param showLeftSpinner replaces the left icon with a spinner.
 * @param leftTestTag test tag of the left button.
 * @param leftSpinnerTestTag test tag of the left spinner. No tag is set when empty.
 * @param rightIcon icon of the right button. The button is absent when this is null.
 * @param rightDescription what TalkBack reads for the right button.
 * @param onRightClick called when the right button is clicked.
 * @param rightEnabled whether the right button accepts clicks.
 * @param rightTestTag test tag of the right button.
 * @param waveformTestTag test tag of the waveform.
 * @param waveformModifier extra modifier applied to the waveform, after it takes the free width.
 */
@Composable
fun MediaAudio(
    modifier: Modifier = Modifier,
    waveform: AudioWaveformState = AudioWaveformState.None,
    leftIcon: ImageVector = Icons.Outlined.PlayArrow,
    leftDescription: String = "",
    onLeftClick: () -> Unit = {},
    leftEnabled: Boolean = true,
    showLeftSpinner: Boolean = false,
    leftTestTag: String = MediaAudioTestTags.LEFT_BUTTON,
    leftSpinnerTestTag: String = "",
    rightIcon: ImageVector? = null,
    rightDescription: String = "",
    onRightClick: () -> Unit = {},
    rightEnabled: Boolean = true,
    rightTestTag: String = MediaAudioTestTags.RIGHT_BUTTON,
    waveformTestTag: String = MediaAudioTestTags.WAVEFORM,
    waveformModifier: Modifier = Modifier,
) {
  Row(
      modifier = modifier,
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_MEDIUM),
  ) {
    DisplayPatientIconButton(
        icon = leftIcon,
        description = leftDescription,
        enabled = leftEnabled,
        showSpinner = showLeftSpinner,
        onClick = onLeftClick,
        testTag = leftTestTag,
        spinnerTestTag = leftSpinnerTestTag,
    )
    DisplayAudioWaveform(
        modifier = Modifier.weight(WEIGHT_ONE).then(waveformModifier).testTag(waveformTestTag),
        waveform = waveform,
    )
    if (rightIcon != null) {
      DisplayPatientIconButton(
          icon = rightIcon,
          description = rightDescription,
          enabled = rightEnabled,
          onClick = onRightClick,
          testTag = rightTestTag,
      )
    }
  }
}

/**
 * Draws [waveform]. There is room for [WAVEFORM_BARS] bars, so a waveform that is still growing
 * fills the left part only. [AudioWaveformState.Listening] paints the played portion in secondary.
 * Every other state leaves the bars outline.
 *
 * @param modifier The modifier applied to the drawing.
 * @param waveform Which waveform to draw.
 */
@Composable
fun DisplayAudioWaveform(
    modifier: Modifier = Modifier,
    waveform: AudioWaveformState = AudioWaveformState.None,
) {
  val unplayedColor = MaterialTheme.colorScheme.outline
  val playedColor = MaterialTheme.colorScheme.secondary
  val levels = waveformLevels(waveform)
  val fraction =
      when (waveform) {
        is AudioWaveformState.Listening ->
            waveform.playedFraction.coerceIn(NO_PLAYED_FRACTION, WEIGHT_ONE)
        AudioWaveformState.None,
        is AudioWaveformState.Recording,
        is AudioWaveformState.Recorded -> NO_PLAYED_FRACTION
      }

  Canvas(modifier = modifier.fillMaxWidth().height(AUDIO_WAVEFORM_HEIGHT)) {
    val bars = displayedWaveformLevels(levels)
    drawWaveformBars(bars, unplayedColor)
    if (fraction > NO_PLAYED_FRACTION) {
      clipRect(right = size.width * fraction) { drawWaveformBars(bars, playedColor) }
    }
  }
}

/** The levels [DisplayAudioWaveform] draws for [waveform]. None has none. */
private fun waveformLevels(waveform: AudioWaveformState = AudioWaveformState.None): List<Float> =
    when (waveform) {
      AudioWaveformState.None -> emptyList()
      is AudioWaveformState.Recording -> waveform.levels
      is AudioWaveformState.Recorded -> waveform.levels
      is AudioWaveformState.Listening -> waveform.levels
    }

/**
 * Draws [levels] in the same slots [DisplayAudioWaveform] always uses. Called once for the whole
 * waveform, then again clipped to the played portion.
 */
private fun DrawScope.drawWaveformBars(levels: List<Float>, color: Color) {
  val slotWidth = size.width / WAVEFORM_BARS
  levels.forEachIndexed { index, level ->
    val x = (index + HALF) * slotWidth
    val halfBarHeight = size.height * level * HALF
    drawLine(
        color = color,
        start = Offset(x, size.height * HALF - halfBarHeight),
        end = Offset(x, size.height * HALF + halfBarHeight),
        strokeWidth = slotWidth * AUDIO_WAVEFORM_BAR_FRACTION,
        cap = StrokeCap.Butt,
    )
  }
}

/**
 * Gives the levels that [DisplayAudioWaveform] really draws: at most [WAVEFORM_BARS] of them, and
 * none below [AUDIO_WAVEFORM_MIN_LEVEL], so that every bar remains visible.
 *
 * @param waveform The levels from 0 (silence) to 1 (the loudest), in playback order.
 */
internal fun displayedWaveformLevels(waveform: List<Float> = emptyList()): List<Float> =
    waveform.take(WAVEFORM_BARS).map { it.coerceAtLeast(AUDIO_WAVEFORM_MIN_LEVEL) }

/** Preview of [MediaAudio] in each waveform state, with the caller choosing the buttons. */
@Preview(showBackground = true, name = "Media audio")
@Composable
internal fun MediaAudioPreview() {
  AmberAppTheme(dynamicColor = false) {
    Column(verticalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_MEDIUM)) {
      MediaAudio(waveform = AudioWaveformState.None, leftDescription = "Record")
      MediaAudio(
          waveform = AudioWaveformState.Recording(levels = List(WAVEFORM_BARS / 2) { HALF }),
          leftIcon = Icons.Outlined.Delete,
          leftDescription = "Delete the audio",
          rightIcon = Icons.Outlined.Stop,
          rightDescription = "Stop the recording",
      )
      MediaAudio(
          waveform = AudioWaveformState.Recorded(levels = List(WAVEFORM_BARS) { HALF }),
          leftIcon = Icons.Outlined.Delete,
          leftDescription = "Delete the audio",
      )
      MediaAudio(
          waveform =
              AudioWaveformState.Listening(
                  levels = List(WAVEFORM_BARS) { HALF },
                  playedFraction = HALF,
              ),
          leftIcon = Icons.Outlined.PlayArrow,
          leftDescription = "Play",
      )
    }
  }
}
