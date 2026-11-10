// Written by Yuhan YANG, with assistance from Cursor.
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.media.audio

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.github.se.amber.R
import com.github.se.amber.data.media.audio.WAVEFORM_BARS
import com.github.se.amber.ui.patient.DisplayPatientButton
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.BORDER_THICKNESS
import com.github.se.amber.ui.theme.HALF
import com.github.se.amber.ui.theme.PATIENT_BUTTON_HEIGHT_SMALL
import com.github.se.amber.ui.theme.PATIENT_PADDING_MEDIUM
import com.github.se.amber.ui.theme.PATIENT_PADDING_SMALL
import com.github.se.amber.ui.theme.ROUND_CORNER
import com.github.se.amber.ui.theme.SECOND_FIELD_WIDTH
import com.github.se.amber.ui.theme.WEIGHT_ONE

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val ZERO_MS = 0L
private const val ZERO_FRACTION = 0f
private const val SECOND_PAD_CHAR = '0'

private const val PREVIEW_DURATION_MS = 190_000L
private const val PREVIEW_PLAYING_POSITION_MS = 42_000L
private const val PREVIEW_PAUSED_POSITION_MS = 95_000L
private const val PREVIEW_WAVEFORM_PAIR = 2
private val PREVIEW_WAVEFORM =
    List(WAVEFORM_BARS) { if (it % PREVIEW_WAVEFORM_PAIR == 0) HALF else WEIGHT_ONE }

/** How far one memo has played. Every memo except the active one is [AudioPlaybackState.Idle]. */
sealed interface AudioPlaybackState {
  val positionMs: Long

  val durationMs: Long

  /** Nothing is playing. [positionMs] stays 0 and [durationMs] can already be known. */
  data class Idle(
      override val positionMs: Long = ZERO_MS,
      override val durationMs: Long = ZERO_MS,
  ) : AudioPlaybackState

  /** The file is being fetched. The buttons are disabled. */
  data class Loading(
      override val positionMs: Long = ZERO_MS,
      override val durationMs: Long = ZERO_MS,
  ) : AudioPlaybackState

  /** This memo is playing at [positionMs]. */
  data class Playing(
      override val positionMs: Long = ZERO_MS,
      override val durationMs: Long = ZERO_MS,
  ) : AudioPlaybackState

  /** This memo is paused at [positionMs], including when it has finished at 0. */
  data class Paused(
      override val positionMs: Long = ZERO_MS,
      override val durationMs: Long = ZERO_MS,
  ) : AudioPlaybackState

  /**
   * Playback failed. [errorMsgRes] is shown with a retry button. It is a string resource, so the
   * screen shows it in the app language.
   */
  data class Error(
      @param:StringRes val errorMsgRes: Int = R.string.audioPlaybackError,
      override val positionMs: Long = ZERO_MS,
      override val durationMs: Long = ZERO_MS,
  ) : AudioPlaybackState
}

/** Test tags owned by [DisplayAudioPlayback]. */
object AudioPlaybackTestTags {
  const val CARD = "audio_playback_card"
  const val PLAY_PAUSE = "audio_playback_play_pause"
  const val RESTART = "audio_playback_restart"
  const val PROGRESS = "audio_playback_progress"
  const val TIME = "audio_playback_time"
  const val ERROR = "audio_playback_error"
  const val RETRY = "audio_playback_retry"
  const val SPINNER = "audio_playback_spinner"
}

/**
 * Play, pause, restart, and retry controls for one audio memo.
 *
 * Draws [state] only. Loading disables the buttons and shows a spinner. An error replaces the
 * controls with [AudioPlaybackState.Error.errorMsgRes] and a retry button.
 *
 * @param modifier modifier applied to the card.
 * @param state which controls are shown. Idle until playback starts.
 * @param onPlayPause called for the play or pause button.
 * @param onRestart called for the restart button.
 * @param onRetry called for the retry button.
 * @param waveform levels from 0 (silence) to 1 (the loudest), in playback order. The played portion
 *   is colored by [DisplayAudioWaveform].
 */
@NonRestartableComposable
@Composable
fun DisplayAudioPlayback(
    modifier: Modifier = Modifier,
    state: AudioPlaybackState = AudioPlaybackState.Idle(),
    onPlayPause: () -> Unit = {},
    onRestart: () -> Unit = {},
    onRetry: () -> Unit = {},
    waveform: List<Float> = emptyList(),
) {
  Column(
      modifier =
          modifier
              .fillMaxWidth(WEIGHT_ONE)
              .border(
                  BORDER_THICKNESS,
                  MaterialTheme.colorScheme.outline,
                  RoundedCornerShape(ROUND_CORNER),
              )
              .clip(RoundedCornerShape(ROUND_CORNER))
              .background(MaterialTheme.colorScheme.surface)
              .padding(PATIENT_PADDING_SMALL)
              .testTag(AudioPlaybackTestTags.CARD),
      verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL),
  ) {
    when (state) {
      is AudioPlaybackState.Error ->
          PlaybackError(errorMsgRes = state.errorMsgRes, onRetry = onRetry)
      is AudioPlaybackState.Idle,
      is AudioPlaybackState.Loading,
      is AudioPlaybackState.Playing,
      is AudioPlaybackState.Paused -> PlaybackTransport(state, onPlayPause, onRestart, waveform)
    }
  }
}

/**
 * Fraction of [durationMs] reached by [positionMs], from 0 to 1. Unknown or empty audio is 0.
 *
 * @param positionMs how far the memo has played, in milliseconds.
 * @param durationMs how long the memo lasts, in milliseconds.
 */
internal fun playbackFraction(positionMs: Long = ZERO_MS, durationMs: Long = ZERO_MS): Float {
  if (durationMs <= ZERO_MS) return ZERO_FRACTION
  return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(ZERO_FRACTION, WEIGHT_ONE)
}

/**
 * Elapsed time over total, as `m:ss / m:ss`.
 *
 * @param positionMs how far the memo has played, in milliseconds.
 * @param durationMs how long the memo lasts, in milliseconds.
 */
internal fun formatPlaybackTime(positionMs: Long = ZERO_MS, durationMs: Long = ZERO_MS): String =
    "${formatPlaybackClock(positionMs)} / ${formatPlaybackClock(durationMs)}"

/**
 * One clock reading, as `m:ss`. A negative time is shown as zero.
 *
 * @param timeMs the time to format, in milliseconds.
 */
internal fun formatPlaybackClock(timeMs: Long = ZERO_MS): String {
  val totalSeconds = timeMs.coerceAtLeast(ZERO_MS) / MILLIS_PER_SECOND
  val minutes = totalSeconds / SECONDS_PER_MINUTE
  val seconds =
      (totalSeconds % SECONDS_PER_MINUTE).toString().padStart(SECOND_FIELD_WIDTH, SECOND_PAD_CHAR)
  return "$minutes:$seconds"
}

/** TalkBack name of the play or pause button for [state], as a string resource. */
@StringRes
internal fun playPauseDescription(state: AudioPlaybackState = AudioPlaybackState.Idle()): Int =
    when (state) {
      is AudioPlaybackState.Loading -> R.string.audioPlaybackLoading
      is AudioPlaybackState.Playing -> R.string.audioPlaybackPause
      is AudioPlaybackState.Idle,
      is AudioPlaybackState.Paused,
      is AudioPlaybackState.Error -> R.string.audioPlaybackPlay
    }

/** Icon of the play or pause button for [state]. */
internal fun playPauseIcon(state: AudioPlaybackState = AudioPlaybackState.Idle()): ImageVector =
    if (state is AudioPlaybackState.Playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow

/** Whether the play and restart buttons accept clicks. Loading does not. */
internal fun playbackControlsEnabled(
    state: AudioPlaybackState = AudioPlaybackState.Idle()
): Boolean = state !is AudioPlaybackState.Loading

@Composable
internal fun PlaybackTransport(
    state: AudioPlaybackState = AudioPlaybackState.Idle(),
    onPlayPause: () -> Unit = {},
    onRestart: () -> Unit = {},
    waveform: List<Float> = emptyList(),
) {
  val enabled = playbackControlsEnabled(state)
  val fraction = playbackFraction(state.positionMs, state.durationMs)
  MediaAudio(
      waveform = AudioWaveformState.Listening(levels = waveform, playedFraction = fraction),
      leftIcon = playPauseIcon(state),
      leftDescription = stringResource(playPauseDescription(state)),
      onLeftClick = onPlayPause,
      leftEnabled = enabled,
      showLeftSpinner = state is AudioPlaybackState.Loading,
      leftTestTag = AudioPlaybackTestTags.PLAY_PAUSE,
      leftSpinnerTestTag = AudioPlaybackTestTags.SPINNER,
      rightIcon = Icons.Outlined.Replay,
      rightDescription = stringResource(R.string.audioPlaybackRestart),
      onRightClick = onRestart,
      rightEnabled = enabled,
      rightTestTag = AudioPlaybackTestTags.RESTART,
      waveformTestTag = AudioPlaybackTestTags.PROGRESS,
      waveformModifier = Modifier.progressSemantics(fraction),
  )
  Text(
      text = formatPlaybackTime(state.positionMs, state.durationMs),
      modifier = Modifier.fillMaxWidth(WEIGHT_ONE).testTag(AudioPlaybackTestTags.TIME),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface,
  )
}

@Composable
internal fun PlaybackError(
    @StringRes errorMsgRes: Int = R.string.audioPlaybackError,
    onRetry: () -> Unit = {},
) {
  Text(
      text = stringResource(errorMsgRes),
      modifier = Modifier.fillMaxWidth(WEIGHT_ONE).testTag(AudioPlaybackTestTags.ERROR),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.error,
  )
  DisplayPatientButton(
      buttonHeight = PATIENT_BUTTON_HEIGHT_SMALL,
      buttonWidth = WEIGHT_ONE,
      text = stringResource(R.string.audioPlaybackRetry),
      textStyle = MaterialTheme.typography.bodyLarge,
      onClick = onRetry,
      testTag = AudioPlaybackTestTags.RETRY,
  )
}

/** The states shown by [AudioPlaybackPreview] and [AudioPlaybackDarkPreview]. */
@Composable
private fun AudioPlaybackPreviewStates() {
  Column(verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_MEDIUM)) {
    DisplayAudioPlayback(
        state = AudioPlaybackState.Idle(durationMs = PREVIEW_DURATION_MS),
        waveform = PREVIEW_WAVEFORM,
    )
    DisplayAudioPlayback(
        state =
            AudioPlaybackState.Playing(
                positionMs = PREVIEW_PLAYING_POSITION_MS,
                durationMs = PREVIEW_DURATION_MS,
            ),
        waveform = PREVIEW_WAVEFORM,
    )
    DisplayAudioPlayback(
        state =
            AudioPlaybackState.Paused(
                positionMs = PREVIEW_PAUSED_POSITION_MS,
                durationMs = PREVIEW_DURATION_MS,
            ),
        waveform = PREVIEW_WAVEFORM,
    )
    DisplayAudioPlayback(
        state = AudioPlaybackState.Loading(durationMs = PREVIEW_DURATION_MS),
        waveform = PREVIEW_WAVEFORM,
    )
    DisplayAudioPlayback(
        state = AudioPlaybackState.Error()
    )
  }
}

/** Preview of [DisplayAudioPlayback] in each state. */
@Preview(showBackground = true, name = "Audio playback light")
@Composable
internal fun AudioPlaybackPreview() {
  AmberAppTheme(dynamicColor = false, darkTheme = false) { AudioPlaybackPreviewStates() }
}

@Preview(
    showBackground = true,
    name = "Audio playback dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
internal fun AudioPlaybackDarkPreview() {
  AmberAppTheme(dynamicColor = false, darkTheme = true) { AudioPlaybackPreviewStates() }
}
