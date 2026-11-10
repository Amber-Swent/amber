// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.media.audio

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.github.se.amber.R
import com.github.se.amber.data.media.audio.WAVEFORM_BARS
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.BORDER_THICKNESS
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.ROUND_CORNER

/** Test tags owned by [DisplayAudioRecorder]. */
object AudioRecorderTestTags {
  const val DELETE_BUTTON = "audio_recorder_delete_button"
  const val STOP_BUTTON = "audio_recorder_stop_button"
  const val WAVEFORM = "audio_recorder_waveform"
}

/**
 * Displays an audio being recorded, or the audio that was recorded. Use by the caregiver screen.
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
  val waveformState =
      when {
        isRecording -> AudioWaveformState.Recording(levels = waveform)
        waveform.isEmpty() -> AudioWaveformState.None
        else -> AudioWaveformState.Recorded(levels = waveform)
      }
  MediaAudio(
      modifier =
          modifier
              .fillMaxWidth()
              .border(
                  width = BORDER_THICKNESS,
                  color = MaterialTheme.colorScheme.outline,
                  shape = RoundedCornerShape(ROUND_CORNER),
              )
              .clip(RoundedCornerShape(ROUND_CORNER))
              .padding(CAREGIVER_PADDING_MEDIUM),
      waveform = waveformState,
      leftIcon = Icons.Outlined.Delete,
      leftDescription = stringResource(R.string.audioRecorderDelete),
      onLeftClick = onDelete,
      leftTestTag = AudioRecorderTestTags.DELETE_BUTTON,
      rightIcon = if (isRecording) Icons.Outlined.Stop else null,
      rightDescription = stringResource(R.string.audioRecorderStop),
      onRightClick = onStop,
      rightTestTag = AudioRecorderTestTags.STOP_BUTTON,
      waveformTestTag = AudioRecorderTestTags.WAVEFORM,
  )
}

/** Preview of [DisplayAudioRecorder] while recording. */
@Preview(showBackground = true)
@Composable
internal fun DisplayAudioRecorderRecordingPreview() {
  AmberAppTheme(dynamicColor = false) {
    DisplayAudioRecorder(isRecording = true, waveform = List(WAVEFORM_BARS / 2) { it / 32f })
  }
}

/** Preview of [DisplayAudioRecorder] once the recording is over. */
@Preview(showBackground = true)
@Composable
internal fun DisplayAudioRecorderRecordedPreview() {
  AmberAppTheme(dynamicColor = false) {
    DisplayAudioRecorder(waveform = List(WAVEFORM_BARS) { (it % 8) / 8f })
  }
}
