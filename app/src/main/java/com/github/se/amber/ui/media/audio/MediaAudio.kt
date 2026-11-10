// Written by Yuhan YANG, with assistance from Cursor.
// Modified by Yuhan YANG, with assistance from Cursor.

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.github.se.amber.R
import com.github.se.amber.data.media.audio.WAVEFORM_BARS
import com.github.se.amber.ui.caregiver.DisplayCaregiverIconButton
import com.github.se.amber.ui.patient.DisplayPatientIconButton
import com.github.se.amber.ui.theme.AUDIO_WAVEFORM_BAR_FRACTION
import com.github.se.amber.ui.theme.AUDIO_WAVEFORM_HEIGHT
import com.github.se.amber.ui.theme.AUDIO_WAVEFORM_MIN_LEVEL
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.HALF
import com.github.se.amber.ui.theme.PATIENT_PADDING_SMALL
import com.github.se.amber.ui.theme.WEIGHT_ONE

/** None of the waveform has been played yet. */
private const val NO_PLAYED_FRACTION = 0f

/** Test tags owned by [MediaAudio]. Callers can pass their own. */
object MediaAudioTestTags {
  const val LEFT_BUTTON = "media_audio_left_button"
  const val RIGHT_BUTTON = "media_audio_right_button"
  const val WAVEFORM = "media_audio_waveform"
}

/**
 * The optional button on the right of [MediaAudio].
 *
 * [icon], [description], and [onClick] come together. A shown button therefore always has an icon,
 * a TalkBack description, and a click. Leave the whole button unset to hide it.
 *
 * @param icon icon of the button.
 * @param description what TalkBack reads for the button.
 * @param onClick called when the button is clicked.
 * @param enabled whether the button accepts clicks.
 * @param testTag test tag of the button.
 */
data class MediaAudioButton(
    val icon: ImageVector,
    val description: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val testTag: String = MediaAudioTestTags.RIGHT_BUTTON,
)

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
 * play, pause, stop, delete, or anything else. Leave [rightButton] unset to hide the right button.
 * [waveform] only chooses what is drawn. The ViewModel owns the levels and the state: it decides
 * when a waveform is recording, recorded or listening, and keeps the levels within [WAVEFORM_BARS].
 * This row does not record, play or resample anything.
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
 * @param rightButton the right button. The button is absent when this is null.
 * @param isPatient whether this row is on a patient screen. A caregiver screen uses the smaller
 *   caregiver button and spacing.
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
    rightButton: MediaAudioButton? = null,
    isPatient: Boolean = true,
    waveformTestTag: String = MediaAudioTestTags.WAVEFORM,
    waveformModifier: Modifier = Modifier,
) {
  val buttonSpacing = if (isPatient) PATIENT_PADDING_SMALL else CAREGIVER_PADDING_SMALL
  Row(
      modifier = modifier,
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
  ) {
    MediaAudioIconButton(
        isPatient = isPatient,
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
    if (rightButton != null) {
      MediaAudioIconButton(
          isPatient = isPatient,
          icon = rightButton.icon,
          description = rightButton.description,
          enabled = rightButton.enabled,
          onClick = rightButton.onClick,
          testTag = rightButton.testTag,
      )
    }
  }
}

/** The left or right button of [MediaAudio], at the patient or caregiver size. */
@Composable
private fun MediaAudioIconButton(
    isPatient: Boolean = true,
    icon: ImageVector = Icons.Outlined.PlayArrow,
    description: String = "",
    enabled: Boolean = true,
    showSpinner: Boolean = false,
    onClick: () -> Unit = {},
    testTag: String = MediaAudioTestTags.LEFT_BUTTON,
    spinnerTestTag: String = "",
) {
  if (isPatient) {
    DisplayPatientIconButton(
        icon = icon,
        description = description,
        enabled = enabled,
        showSpinner = showSpinner,
        onClick = onClick,
        testTag = testTag,
        spinnerTestTag = spinnerTestTag,
    )
  } else {
    DisplayCaregiverIconButton(
        icon = icon,
        description = description,
        enabled = enabled,
        showSpinner = showSpinner,
        onClick = onClick,
        testTag = testTag,
        spinnerTestTag = spinnerTestTag,
    )
  }
}

/**
 * Draws [waveform]. There is room for [WAVEFORM_BARS] bars, so a waveform that is still growing
 * fills the left part only. [AudioWaveformState.Listening] paints the played portion in secondary.
 * Every other state leaves the bars outline. This composable only draws. It does not resample:
 * levels beyond [WAVEFORM_BARS] are not drawn. Keeping the levels within [WAVEFORM_BARS], including
 * while a recording grows, is the responsibility of the ViewModel (or the data layer for a finished
 * audio).
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
  val bars = displayedWaveformLevels(waveformLevels(waveform))
  val fraction = playedFraction(waveform)

  Canvas(modifier = modifier.fillMaxWidth().height(AUDIO_WAVEFORM_HEIGHT)) {
    drawWaveformBars(bars, unplayedColor)
    if (fraction > NO_PLAYED_FRACTION) {
      clipRect(right = size.width * fraction) { drawWaveformBars(bars, playedColor) }
    }
  }
}

/**
 * The levels [DisplayAudioWaveform] draws for [waveform]. Listening with unknown levels gets a flat
 * track.
 */
internal fun waveformLevels(waveform: AudioWaveformState = AudioWaveformState.None): List<Float> =
    when (waveform) {
      AudioWaveformState.None -> emptyList()
      is AudioWaveformState.Recording -> waveform.levels
      is AudioWaveformState.Recorded -> waveform.levels
      is AudioWaveformState.Listening ->
          // Unknown waveform (e.g. a picked file): show a flat track so the progress stays visible.
          waveform.levels.ifEmpty { List(WAVEFORM_BARS) { AUDIO_WAVEFORM_MIN_LEVEL } }
    }

/**
 * How much of the waveform is painted as played, from 0 to 1. Only [AudioWaveformState.Listening]
 * plays.
 */
internal fun playedFraction(waveform: AudioWaveformState = AudioWaveformState.None): Float =
    when (waveform) {
      is AudioWaveformState.Listening ->
          waveform.playedFraction.coerceIn(NO_PLAYED_FRACTION, WEIGHT_ONE)
      AudioWaveformState.None,
      is AudioWaveformState.Recording,
      is AudioWaveformState.Recorded -> NO_PLAYED_FRACTION
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
 * none below [AUDIO_WAVEFORM_MIN_LEVEL], so that every bar remains visible. Extra levels are
 * dropped, not resampled: the caller must already provide at most [WAVEFORM_BARS] levels.
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
      MediaAudio(
          waveform = AudioWaveformState.None,
          leftDescription = stringResource(R.string.audioRecorderRecord),
      )
      MediaAudio(
          waveform = AudioWaveformState.Recording(levels = List(WAVEFORM_BARS / 2) { HALF }),
          isPatient = false,
          leftIcon = Icons.Outlined.Delete,
          leftDescription = stringResource(R.string.audioRecorderDelete),
          rightButton =
              MediaAudioButton(
                  icon = Icons.Outlined.Stop,
                  description = stringResource(R.string.audioRecorderStop),
                  onClick = {},
              ),
      )
      MediaAudio(
          waveform = AudioWaveformState.Recorded(levels = List(WAVEFORM_BARS) { HALF }),
          isPatient = false,
          leftIcon = Icons.Outlined.Delete,
          leftDescription = stringResource(R.string.audioRecorderDelete),
      )
      MediaAudio(
          waveform =
              AudioWaveformState.Listening(
                  levels = List(WAVEFORM_BARS) { HALF },
                  playedFraction = HALF,
              ),
          leftIcon = Icons.Outlined.PlayArrow,
          leftDescription = stringResource(R.string.audioPlaybackPlay),
      )
    }
  }
}
