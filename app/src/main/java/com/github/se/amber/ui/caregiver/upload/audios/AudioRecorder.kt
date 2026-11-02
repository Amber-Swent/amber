/* Written by LaMaier, with assistance from
 * Claude (Anthropic) via Claude Code.
 */
package com.github.se.amber.ui.caregiver.upload.audios

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.github.se.amber.data.media.audio.WAVEFORM_BARS
import com.github.se.amber.ui.theme.AUDIO_WAVEFORM_BAR_FRACTION
import com.github.se.amber.ui.theme.AUDIO_WAVEFORM_HEIGHT
import com.github.se.amber.ui.theme.AUDIO_WAVEFORM_MIN_LEVEL
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.BORDER_THICKNESS
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.HALF
import com.github.se.amber.ui.theme.ROUND_CORNER
import com.github.se.amber.ui.theme.WEIGHT_ONE

/** Test tags owned by [AudioRecorder]. */
object AudioRecorderTestTags {
  const val DELETE_BUTTON = "audio_recorder_delete_button"
  const val STOP_BUTTON = "audio_recorder_stop_button"
  const val WAVEFORM = "audio_recorder_waveform"
}

/**
 * Displays an audio being recorded, or the audio that was recorded.
 *
 * The delete button is on the left, then comes the waveform, then the stop button on the right. The
 * stop button is only there while recording. While recording, [waveform] grows as the microphone
 * levels come; once the recording is over, it holds the full audio.
 *
 * @param modifier The modifier applied to the row.
 * @param isRecording Whether the recording is in progress; it shows the stop button.
 * @param waveform The levels from 0 (silence) to 1 (the loudest), in playback order, one bar each.
 * @param onDelete The callback invoked when the recording must be thrown away.
 * @param onStop The callback invoked when the recording must be stopped.
 */
@Composable
fun DisplayAudioRecorder(
    modifier: Modifier = Modifier,
    isRecording: Boolean = false,
    waveform: List<Float> = emptyList(),
    onDelete: () -> Unit = {},
    onStop: () -> Unit = {},
) {
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .border(
                  width = BORDER_THICKNESS,
                  color = MaterialTheme.colorScheme.tertiary,
                  shape = RoundedCornerShape(ROUND_CORNER),
              )
              .clip(RoundedCornerShape(ROUND_CORNER))
              .padding(CAREGIVER_PADDING_MEDIUM),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(CAREGIVER_PADDING_MEDIUM),
  ) {
    DisplayAudioIconButton(
        modifier = Modifier.testTag(AudioRecorderTestTags.DELETE_BUTTON),
        icon = Icons.Outlined.Delete,
        contentDescription = "Delete the audio",
        onClick = onDelete,
    )
    DisplayAudioWaveform(
        modifier = Modifier.weight(WEIGHT_ONE).testTag(AudioRecorderTestTags.WAVEFORM),
        waveform = waveform,
    )
    if (isRecording) {
      DisplayAudioIconButton(
          modifier = Modifier.testTag(AudioRecorderTestTags.STOP_BUTTON),
          icon = Icons.Outlined.Stop,
          contentDescription = "Stop the recording",
          onClick = onStop,
      )
    }
  }
}

/** Circular icon button with the caregiver button colors, used by [DisplayAudioRecorder]. */
@Composable
private fun DisplayAudioIconButton(
    modifier: Modifier = Modifier,
    icon: ImageVector, // No built-in default value
    contentDescription: String = "",
    onClick: () -> Unit = {},
) {
  FilledIconButton(
      modifier = modifier,
      onClick = onClick,
      shape = CircleShape,
      colors =
          IconButtonDefaults.filledIconButtonColors(
              containerColor = MaterialTheme.colorScheme.tertiary,
              contentColor = MaterialTheme.colorScheme.onTertiary,
          ),
  ) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onTertiary,
    )
  }
}

/**
 * Draws one vertical bar per level, from left to right. There is room for [WAVEFORM_BARS] bars, so
 * a waveform that is still growing fills the left part only.
 *
 * @param modifier The modifier applied to the drawing.
 * @param waveform The levels from 0 (silence) to 1 (the loudest), in playback order.
 */
@Composable
fun DisplayAudioWaveform(
    modifier: Modifier = Modifier,
    waveform: List<Float> = emptyList(),
) {
  val color = MaterialTheme.colorScheme.secondary
  Canvas(modifier = modifier.fillMaxWidth().height(AUDIO_WAVEFORM_HEIGHT)) {
    val slotWidth = size.width / WAVEFORM_BARS
    waveform.take(WAVEFORM_BARS).forEachIndexed { index, level ->
      val x = (index + HALF) * slotWidth
      val halfBarHeight = size.height * level.coerceAtLeast(AUDIO_WAVEFORM_MIN_LEVEL) * HALF
      drawLine(
          color = color,
          start = Offset(x, size.height * HALF - halfBarHeight),
          end = Offset(x, size.height * HALF + halfBarHeight),
          strokeWidth = slotWidth * AUDIO_WAVEFORM_BAR_FRACTION,
          cap = StrokeCap.Butt,
      )
    }
  }
}

/** Preview of [DisplayAudioRecorder] while recording. */
@Preview(showBackground = true)
@Composable
private fun DisplayAudioRecorderRecordingPreview() {
  AmberAppTheme(dynamicColor = false) {
    DisplayAudioRecorder(isRecording = true, waveform = List(WAVEFORM_BARS / 2) { it / 32f })
  }
}

/** Preview of [DisplayAudioRecorder] once the recording is over. */
@Preview(showBackground = true)
@Composable
private fun DisplayAudioRecorderRecordedPreview() {
  AmberAppTheme(dynamicColor = false) {
    DisplayAudioRecorder(waveform = List(WAVEFORM_BARS) { (it % 8) / 8f })
  }
}
