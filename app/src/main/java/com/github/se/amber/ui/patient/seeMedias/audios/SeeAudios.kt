// Written by MeiliV, with assistance from OpenAI Codex.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.patient.seeMedias.audios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.ui.media.audio.AudioPlaybackState
import com.github.se.amber.ui.media.audio.DisplayAudioPlayback
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMedias
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMediasConfig

/** Test tags for the title, messages, and audio cards on [SeeAudiosScreen]. */
object SeeAudiosTestTags {
  const val NO_AUDIOS = "see_audios_no_audios"
  const val ERROR = "see_audios_error"
  const val TITLE = "see_audios_title"
  const val LOADING = "see_audios_loading"

  fun audio(id: String = "") = "see_audios_audio_$id"

  fun audioCard(id: String = "") = "see_audios_audio_card_$id"
}

/** Texts and test tags of the audios screen, given to [SeeGeneralMedias]. */
private val audiosScreenConfig =
    SeeGeneralMediasConfig(
        titleRes = R.string.seeAudiosTitle,
        retryRes = R.string.seeAudiosRetry,
        emptyRes = R.string.seeAudiosEmpty,
        titleTag = SeeAudiosTestTags.TITLE,
        errorTag = SeeAudiosTestTags.ERROR,
        emptyTag = SeeAudiosTestTags.NO_AUDIOS,
        loadingTag = SeeAudiosTestTags.LOADING,
        cardTag = { id -> SeeAudiosTestTags.audioCard(id) },
    )

/**
 * Displays approved audios in a scrollable list with playback controls and a media card for each
 * memory. Loading and blocking errors replace the list; playback errors stay on their audio card.
 *
 * @param audioViewModel provides list and per-card playback states.
 * @param onBackClick called when the top navigation action is selected.
 * @param onTabSelected called when a bottom navigation tab is selected.
 */
@Composable
fun SeeAudiosScreen(
    audioViewModel: SeeAudiosViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    onTabSelected: (Tab) -> Unit = {},
) {
  val uiState by audioViewModel.uiState.collectAsStateWithLifecycle()
  val playbackStates by audioViewModel.playbackStates.collectAsStateWithLifecycle()

  SeeGeneralMedias(
      uiState = uiState,
      config = audiosScreenConfig,
      onRetry = audioViewModel::retry,
      onErrorShown = audioViewModel::clearErrorMsg,
      onBackClick = onBackClick,
      onTabSelected = onTabSelected,
  ) { audio, _, modifier ->
    DisplayAudioPlayback(
        modifier = modifier.testTag(SeeAudiosTestTags.audio(audio.id)),
        state = playbackStates[audio.id] ?: AudioPlaybackState.Idle(durationMs = audio.durationMs),
        waveform = audio.waveform,
        onPlayPause = { audioViewModel.onPlayPause(audio) },
        onRestart = { audioViewModel.onRestart(audio) },
        onRetry = { audioViewModel.onPlayPause(audio) },
    )
  }
}
