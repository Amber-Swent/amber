// Written by Yuhan YANG, with assistance from Cursor.
package com.github.se.amber.ui.media.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.github.se.amber.ui.patient.DisplayPatientButton
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.BORDER_THICKNESS
import com.github.se.amber.ui.theme.PADDING_MINI
import com.github.se.amber.ui.theme.PATIENT_BUTTON_HEIGHT_SMALL
import com.github.se.amber.ui.theme.PATIENT_ICON_SIZE
import com.github.se.amber.ui.theme.PATIENT_PADDING_MEDIUM
import com.github.se.amber.ui.theme.PATIENT_PADDING_SMALL
import com.github.se.amber.ui.theme.ROUND_CORNER
import com.github.se.amber.ui.theme.WEIGHT_ONE
import com.github.se.amber.ui.theme.lightGray

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECOND_FIELD_WIDTH = 2

/** Opacity Material uses for a disabled control. */
private const val DISABLED_ALPHA = 0.38f

/** How far one memo has played. Every memo except the active one is [AudioPlaybackState.Idle]. */
sealed interface AudioPlaybackState {
  val positionMs: Long

  val durationMs: Long

  /** Nothing is playing. [positionMs] stays 0 and [durationMs] can already be known. */
  data class Idle(
      override val positionMs: Long = 0L,
      override val durationMs: Long = 0L,
  ) : AudioPlaybackState

  /** The file is being fetched. The buttons are disabled. */
  data class Loading(
      override val positionMs: Long = 0L,
      override val durationMs: Long = 0L,
  ) : AudioPlaybackState

  /** This memo is playing at [positionMs]. */
  data class Playing(
      override val positionMs: Long = 0L,
      override val durationMs: Long = 0L,
  ) : AudioPlaybackState

  /** This memo is paused at [positionMs], including when it has finished at 0. */
  data class Paused(
      override val positionMs: Long = 0L,
      override val durationMs: Long = 0L,
  ) : AudioPlaybackState

  /** Playback failed. [message] is shown with a retry button. */
  data class Error(
      val message: String = "",
      override val positionMs: Long = 0L,
      override val durationMs: Long = 0L,
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

internal object AudioPlaybackDescriptions {
  const val PLAY = "Play"
  const val PAUSE = "Pause"
  const val RESTART = "Restart"
  const val LOADING = "Loading"
  const val RETRY = "Retry"
}

/**
 * Play, pause, restart, and retry controls for one audio memo.
 *
 * Draws [state] only. Loading disables the buttons and shows a spinner. An error replaces the
 * controls with [AudioPlaybackState.Error.message] and a retry button.
 *
 * @param modifier modifier applied to the card.
 * @param state which controls are shown. Idle until playback starts.
 * @param onPlayPause called for the play or pause button.
 * @param onRestart called for the restart button.
 * @param onRetry called for the retry button.
 */
@NonRestartableComposable
@Composable
fun DisplayAudioPlayback(
    modifier: Modifier = Modifier,
    state: AudioPlaybackState = AudioPlaybackState.Idle(),
    onPlayPause: () -> Unit = {},
    onRestart: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
  Column(
      modifier =
          modifier
              .fillMaxWidth(WEIGHT_ONE)
              .border(BORDER_THICKNESS, lightGray, RoundedCornerShape(ROUND_CORNER))
              .clip(RoundedCornerShape(ROUND_CORNER))
              .background(MaterialTheme.colorScheme.surface)
              .padding(PATIENT_PADDING_SMALL)
              .testTag(AudioPlaybackTestTags.CARD),
      verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL),
  ) {
    when (state) {
      is AudioPlaybackState.Error -> PlaybackError(message = state.message, onRetry = onRetry)
      is AudioPlaybackState.Idle,
      is AudioPlaybackState.Loading,
      is AudioPlaybackState.Playing,
      is AudioPlaybackState.Paused -> PlaybackTransport(state, onPlayPause, onRestart)
    }
  }
}

/**
 * Fraction of [durationMs] reached by [positionMs], from 0 to 1. Unknown or empty audio is 0.
 *
 * @param positionMs how far the memo has played, in milliseconds.
 * @param durationMs how long the memo lasts, in milliseconds.
 */
internal fun playbackFraction(positionMs: Long = 0L, durationMs: Long = 0L): Float {
  if (durationMs <= 0L) return 0f
  return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, WEIGHT_ONE)
}

/**
 * Elapsed time over total, as `m:ss / m:ss`.
 *
 * @param positionMs how far the memo has played, in milliseconds.
 * @param durationMs how long the memo lasts, in milliseconds.
 */
internal fun formatPlaybackTime(positionMs: Long = 0L, durationMs: Long = 0L): String =
    "${formatPlaybackClock(positionMs)} / ${formatPlaybackClock(durationMs)}"

/**
 * One clock reading, as `m:ss`. A negative time is shown as zero.
 *
 * @param timeMs the time to format, in milliseconds.
 */
internal fun formatPlaybackClock(timeMs: Long = 0L): String {
  val totalSeconds = timeMs.coerceAtLeast(0L) / MILLIS_PER_SECOND
  val minutes = totalSeconds / SECONDS_PER_MINUTE
  val seconds = (totalSeconds % SECONDS_PER_MINUTE).toString().padStart(SECOND_FIELD_WIDTH, '0')
  return "$minutes:$seconds"
}

/** TalkBack name of the play or pause button for [state]. */
internal fun playPauseDescription(state: AudioPlaybackState = AudioPlaybackState.Idle()): String =
    when (state) {
      is AudioPlaybackState.Loading -> AudioPlaybackDescriptions.LOADING
      is AudioPlaybackState.Playing -> AudioPlaybackDescriptions.PAUSE
      is AudioPlaybackState.Idle,
      is AudioPlaybackState.Paused,
      is AudioPlaybackState.Error -> AudioPlaybackDescriptions.PLAY
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
) {
  val enabled = playbackControlsEnabled(state)
  Row(
      horizontalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    PlaybackIconButton(
        icon = playPauseIcon(state),
        description = playPauseDescription(state),
        enabled = enabled,
        showSpinner = state is AudioPlaybackState.Loading,
        onClick = onPlayPause,
        testTag = AudioPlaybackTestTags.PLAY_PAUSE,
    )
    PlaybackIconButton(
        icon = Icons.Outlined.Replay,
        description = AudioPlaybackDescriptions.RESTART,
        enabled = enabled,
        onClick = onRestart,
        testTag = AudioPlaybackTestTags.RESTART,
    )
  }
  PlaybackProgress(positionMs = state.positionMs, durationMs = state.durationMs)
  Text(
      text = formatPlaybackTime(state.positionMs, state.durationMs),
      modifier = Modifier.fillMaxWidth(WEIGHT_ONE).testTag(AudioPlaybackTestTags.TIME),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface,
  )
}

@Composable
internal fun PlaybackError(message: String = "", onRetry: () -> Unit = {}) {
  Text(
      text = message,
      modifier = Modifier.fillMaxWidth(WEIGHT_ONE).testTag(AudioPlaybackTestTags.ERROR),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.error,
  )
  DisplayPatientButton(
      buttonHeight = PATIENT_BUTTON_HEIGHT_SMALL,
      buttonWidth = WEIGHT_ONE,
      text = AudioPlaybackDescriptions.RETRY,
      textStyle = MaterialTheme.typography.bodyLarge,
      onClick = onRetry,
      testTag = AudioPlaybackTestTags.RETRY,
  )
}

@Composable
internal fun PlaybackProgress(positionMs: Long = 0L, durationMs: Long = 0L) {
  val fraction = playbackFraction(positionMs, durationMs)
  Box(
      modifier =
          Modifier.fillMaxWidth(WEIGHT_ONE)
              .height(PADDING_MINI)
              .clip(RoundedCornerShape(ROUND_CORNER))
              .background(lightGray)
              .progressSemantics(fraction)
              .testTag(AudioPlaybackTestTags.PROGRESS)
  ) {
    Box(
        modifier =
            Modifier.fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(MaterialTheme.colorScheme.secondary)
    )
  }
}

/**
 * Square patient button. [DisplayPatientButton] always includes a text label and a width fraction,
 * so an icon-only control uses the same container color, content color, corner, and height
 * directly.
 */
@Composable
internal fun PlaybackIconButton(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.PlayArrow,
    description: String = "",
    enabled: Boolean = true,
    showSpinner: Boolean = false,
    onClick: () -> Unit = {},
    testTag: String = "",
) {
  val contentColor = MaterialTheme.colorScheme.onTertiary
  FilledIconButton(
      onClick = onClick,
      enabled = enabled,
      modifier = modifier.size(PATIENT_BUTTON_HEIGHT_SMALL).testTag(testTag),
      shape = RoundedCornerShape(ROUND_CORNER),
      colors =
          IconButtonDefaults.filledIconButtonColors(
              containerColor = MaterialTheme.colorScheme.tertiary,
              contentColor = contentColor,
              disabledContainerColor =
                  MaterialTheme.colorScheme.tertiary.copy(alpha = DISABLED_ALPHA),
              disabledContentColor = contentColor.copy(alpha = DISABLED_ALPHA),
          ),
  ) {
    if (showSpinner) {
      CircularProgressIndicator(
          modifier = Modifier.size(PATIENT_ICON_SIZE).testTag(AudioPlaybackTestTags.SPINNER),
          color = LocalContentColor.current,
      )
    } else {
      Icon(
          imageVector = icon,
          contentDescription = description,
          modifier = Modifier.size(PATIENT_ICON_SIZE),
      )
    }
  }
}

/** Preview of [DisplayAudioPlayback] in each state. */
@Preview(showBackground = true)
@Composable
internal fun AudioPlaybackPreview() {
  AmberAppTheme(dynamicColor = false) {
    Column(verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_MEDIUM)) {
      DisplayAudioPlayback(state = AudioPlaybackState.Idle(durationMs = 190_000L))
      DisplayAudioPlayback(
          state = AudioPlaybackState.Playing(positionMs = 42_000L, durationMs = 190_000L)
      )
      DisplayAudioPlayback(
          state = AudioPlaybackState.Paused(positionMs = 95_000L, durationMs = 190_000L)
      )
      DisplayAudioPlayback(state = AudioPlaybackState.Loading(durationMs = 190_000L))
      DisplayAudioPlayback(
          state = AudioPlaybackState.Error(message = "This memo could not be played.")
      )
    }
  }
}
