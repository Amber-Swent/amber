// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.media.audio

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.github.se.amber.R
import com.github.se.amber.data.media.audio.WAVEFORM_BARS
import com.github.se.amber.model.media.AudioRecordingState
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.BORDER_THICKNESS
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_MEDIUM
import com.github.se.amber.ui.theme.HALF
import com.github.se.amber.ui.theme.NO_PLAYED_FRACTION
import com.github.se.amber.ui.theme.ROUND_CORNER

/** Test tags owned by [DisplayAudioRecorder]. */
object AudioRecorderTestTags {
  const val DELETE_BUTTON = "audio_recorder_delete_button"
  const val STOP_BUTTON = "audio_recorder_stop_button"
  const val PLAY_BUTTON = "audio_recorder_play_button"
  const val WAVEFORM = "audio_recorder_waveform"
}

/**
 * Displays the audio of the caregiver screen once the recording has started, in one of three
 * states. The delete button is always on the left, then comes the waveform, then one button:
 * - [AudioRecordingState.RECORDING]: the waveform grows as the microphone levels come; stop on the
 *   right.
 * - [AudioRecordingState.RECORDED]: the full waveform; play on the right.
 * - [AudioRecordingState.LISTENING]: the full waveform while it is heard, the part already heard in
 *   tertiary; stop on the right.
 *
 * [AudioRecordingState.IDLE] has no audio to show: it is drawn like [AudioRecordingState.RECORDED].
 * The caller passes what stop does in each state, since stopping a recording and stopping the
 * listening are not the same action.
 *
 * @param modifier The modifier applied to the row.
 * @param recordingState Which of the states to display.
 * @param waveform The levels from 0 (silence) to 1 (the loudest), in playback order, one bar each.
 * @param playedFraction How much of the audio has been heard, from 0 to 1. Only used while
 *   listening.
 * @param onDelete The callback invoked when the audio must be thrown away.
 * @param onStop The callback invoked when the stop button is pressed (recording or listening).
 * @param onPlay The callback invoked when the play button is pressed (recorded).
 */
@Composable
fun DisplayAudioRecorder(
    modifier: Modifier = Modifier,
    recordingState: AudioRecordingState = AudioRecordingState.RECORDED,
    waveform: List<Float> = emptyList(),
    playedFraction: Float = NO_PLAYED_FRACTION,
    onDelete: () -> Unit = {},
    onStop: () -> Unit = {},
    onPlay: () -> Unit = {},
) {
  val waveformState =
      when {
        recordingState == AudioRecordingState.RECORDING ->
            AudioWaveformState.Recording(levels = waveform)
        waveform.isEmpty() -> AudioWaveformState.None
        recordingState == AudioRecordingState.LISTENING ->
            AudioWaveformState.Listening(levels = waveform, playedFraction = playedFraction)
        else -> AudioWaveformState.Recorded(levels = waveform)
      }
  val isStopShown =
      recordingState == AudioRecordingState.RECORDING ||
          recordingState == AudioRecordingState.LISTENING
  MediaAudio(
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
      waveform = waveformState,
      isPatient = false,
      leftIcon = Icons.Outlined.Delete,
      leftDescription = stringResource(R.string.audioRecorderDelete),
      onLeftClick = onDelete,
      leftTestTag = AudioRecorderTestTags.DELETE_BUTTON,
      rightButton =
          if (isStopShown) {
            MediaAudioButton(
                icon = Icons.Outlined.Stop,
                description =
                    stringResource(
                        if (recordingState == AudioRecordingState.LISTENING)
                            R.string.audioRecorderStopListening
                        else R.string.audioRecorderStop
                    ),
                onClick = onStop,
                testTag = AudioRecorderTestTags.STOP_BUTTON,
            )
          } else {
            MediaAudioButton(
                icon = Icons.Outlined.PlayArrow,
                description = stringResource(R.string.audioPlaybackPlay),
                onClick = onPlay,
                testTag = AudioRecorderTestTags.PLAY_BUTTON,
            )
          },
      waveformTestTag = AudioRecorderTestTags.WAVEFORM,
      playedColor = MaterialTheme.colorScheme.tertiary,
  )
}

/** Preview of [DisplayAudioRecorder] while recording. */
@Preview(showBackground = true)
@Composable
internal fun DisplayAudioRecorderRecordingPreview() {
  AmberAppTheme(dynamicColor = false) {
    DisplayAudioRecorder(
        recordingState = AudioRecordingState.RECORDING,
        waveform = List(WAVEFORM_BARS / 2) { it / 32f },
    )
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

/** Preview of [DisplayAudioRecorder] while the recorded audio is heard. */
@Preview(showBackground = true)
@Composable
internal fun DisplayAudioRecorderListeningPreview() {
  AmberAppTheme(dynamicColor = false) {
    DisplayAudioRecorder(
        recordingState = AudioRecordingState.LISTENING,
        waveform = List(WAVEFORM_BARS) { (it % 8) / 8f },
        playedFraction = HALF,
    )
  }
}
