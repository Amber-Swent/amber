// Written by MeiliV, with assistance from OpenAI Codex.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.patient.seeMedias.audios

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.ui.media.MediaCard
import com.github.se.amber.ui.media.audio.AudioPlaybackState
import com.github.se.amber.ui.media.audio.DisplayAudioPlayback
import com.github.se.amber.ui.navigation.BottomNavigationMenu
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationAction
import com.github.se.amber.ui.navigation.TopNavigationMenu
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.patient.DisplayPatientButton
import com.github.se.amber.ui.patient.DisplayPatientTitle
import com.github.se.amber.ui.theme.PATIENT_BUTTON_WIDTH_BIG
import com.github.se.amber.ui.theme.PATIENT_PADDING_MEDIUM
import com.github.se.amber.ui.theme.PATIENT_PADDING_SMALL

/** Test tags for the title, messages, and audio cards on [SeeAudiosScreen]. */
object SeeAudiosTestTags {
  const val NO_AUDIOS = "see_audios_no_audios"
  const val ERROR = "see_audios_error"
  const val TITLE = "see_audios_title"
  const val LOADING = "see_audios_loading"

  fun audio(id: String = "") = "see_audios_audio_$id"

  fun audioCard(id: String = "") = "see_audios_audio_card_$id"
}

/**
 * Displays approved audios in a scrollable list with playback controls and a media card for each
 * memory. Loading and blocking errors replace the list; playback errors stay on their audio card.
 *
 * @param audioViewModel provides list and per-card playback states.
 * @param onBackClick called when the top navigation action is selected.
 * @param onTabSelected called when a bottom navigation tab is selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeeAudiosScreen(
    audioViewModel: SeeAudiosViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    onTabSelected: (Tab) -> Unit = {},
) {
  val uiState by audioViewModel.uiState.collectAsStateWithLifecycle()
  val playbackStates by audioViewModel.playbackStates.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val resources = LocalResources.current
  val errorMsgRes =
      when (val state = uiState) {
        is SeeAudiosUIState.Content -> state.errorMsgRes
        is SeeAudiosUIState.Empty -> state.errorMsgRes
        is SeeAudiosUIState.Loading,
        is SeeAudiosUIState.BlockingError -> null
      }

  LaunchedEffect(errorMsgRes) {
    if (errorMsgRes != null) {
      Toast.makeText(context, resources.getString(errorMsgRes), Toast.LENGTH_LONG).show()
      audioViewModel.clearErrorMsg()
    }
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Column {
                TopNavigationMenu(
                    modifier = Modifier.testTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU),
                    action = TopNavigationAction.CANCEL,
                    onBackClick = onBackClick,
                )
                DisplayPatientTitle(
                    title = stringResource(R.string.seeAudiosTitle),
                    testTag = SeeAudiosTestTags.TITLE,
                )
              }
            }
        )
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = PatientTab.SeeMedias,
            onTabSelected = onTabSelected,
            tabs = PatientTab.patientTabs,
            modifier = Modifier.testTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU),
        )
      },
      content = { paddingValues ->
        when (val actualState = uiState) {
          is SeeAudiosUIState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator(modifier = Modifier.testTag(SeeAudiosTestTags.LOADING))
            }
          }

          is SeeAudiosUIState.BlockingError -> {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(paddingValues)
                        .padding(PATIENT_PADDING_MEDIUM)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement =
                    Arrangement.spacedBy(PATIENT_PADDING_SMALL, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                  modifier = Modifier.testTag(SeeAudiosTestTags.ERROR),
                  text = stringResource(actualState.errorMsgRes),
                  textAlign = TextAlign.Center,
                  color = MaterialTheme.colorScheme.error,
                  style = MaterialTheme.typography.titleLarge,
              )
              DisplayPatientButton(
                  text = stringResource(R.string.seeAudiosRetry),
                  onClick = audioViewModel::retry,
              )
            }
          }

          is SeeAudiosUIState.Empty -> {
            Box(
                modifier =
                    Modifier.fillMaxSize().padding(paddingValues).padding(PATIENT_PADDING_MEDIUM),
                contentAlignment = Alignment.Center,
            ) {
              Text(
                  modifier = Modifier.testTag(SeeAudiosTestTags.NO_AUDIOS),
                  text = stringResource(R.string.seeAudiosEmpty),
                  textAlign = TextAlign.Center,
                  color = MaterialTheme.colorScheme.onBackground,
                  style = MaterialTheme.typography.titleLarge,
              )
            }
          }

          is SeeAudiosUIState.Content -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(PATIENT_PADDING_SMALL),
                verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL),
            ) {
              items(actualState.allAudios, key = { it.id }) { audio ->
                // Wrapping the two components into a column with padding so that the audio and its
                // MediaCard do not touch
                Column(verticalArrangement = Arrangement.spacedBy(PATIENT_PADDING_SMALL)) {
                  DisplayAudioPlayback(
                      modifier =
                          Modifier.fillMaxWidth(PATIENT_BUTTON_WIDTH_BIG)
                              .testTag(SeeAudiosTestTags.audio(audio.id)),
                      state =
                          playbackStates[audio.id]
                              ?: AudioPlaybackState.Idle(durationMs = audio.durationMs),
                      waveform = audio.waveform,
                      onPlayPause = { audioViewModel.onPlayPause(audio) },
                      onRestart = { audioViewModel.onRestart(audio) },
                      onRetry = { audioViewModel.onPlayPause(audio) },
                  )
                  MediaCard(
                      modifier = Modifier.fillMaxWidth(PATIENT_BUTTON_WIDTH_BIG),
                      media = audio,
                      testTag = SeeAudiosTestTags.audioCard(audio.id),
                  )
                }
              }
            }
          }
        }
      },
  )
}
