// Written by MeiliV, with assistance from OpenAI Codex.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.patient.seeMedias.audios

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.media.MediaCardViewModel
import com.github.se.amber.ui.media.audio.AudioPlaybackState
import com.github.se.amber.ui.media.audio.AudioPlaybackTestTags
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SeeAudiosScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** Text of [id] in the current test language, with [args] for its placeholders. */
  private fun string(id: Int, vararg args: Any): String =
      ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

  // Like SeePicturesTest, provide MediaCard's ViewModel without its Firebase defaults.
  private fun testOwner(audioViewModel: SeeAudiosViewModel): ViewModelStoreOwner =
      object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
        override val viewModelStore = ViewModelStore()
        override val defaultViewModelProviderFactory =
            object : ViewModelProvider.Factory {
              @Suppress("UNCHECKED_CAST")
              override fun <T : ViewModel> create(modelClass: Class<T>): T =
                  when (modelClass) {
                    SeeAudiosViewModel::class.java -> audioViewModel as T
                    MediaCardViewModel::class.java -> MediaCardViewModel(mockk(), mockk()) as T
                    else -> error("Unexpected ViewModel: $modelClass")
                  }
            }
      }

  private fun setContent(
      onBackClick: () -> Unit = {},
      onTabSelected: (Tab) -> Unit = {},
      state: SeeAudiosUIState =
          SeeAudiosUIState.Empty(
              UserProfile(uid = "patient", role = Role.PATIENT, person = Person()),
              CareCircle(id = "circle1"),
          ),
      onPlayAudio: (String) -> Unit = {},
      onReplayAudio: (String) -> Unit = {},
      playbackStates: MutableStateFlow<Map<String, AudioPlaybackState>> =
          MutableStateFlow(emptyMap()),
  ): SeeAudiosViewModel {
    val viewModel = mockk<SeeAudiosViewModel>(relaxed = true)
    every { viewModel.uiState } returns MutableStateFlow(state)
    every { viewModel.playbackStates } returns playbackStates
    composeTestRule.setContent {
      CompositionLocalProvider(LocalViewModelStoreOwner provides testOwner(viewModel)) {
        AmberAppTheme(dynamicColor = false) {
          SeeAudiosScreen(
              audioViewModel = viewModel,
              onBackClick = onBackClick,
              onTabSelected = onTabSelected,
              onPlayAudio = onPlayAudio,
              onReplayAudio = onReplayAudio,
          )
        }
      }
    }
    return viewModel
  }

  @Test
  fun playback_state_updates_the_card_and_retry_calls_view_model() {
    val audio = MediaItem.Audio(id = "a1")
    val playbackStates =
        MutableStateFlow<Map<String, AudioPlaybackState>>(
            mapOf(audio.id to AudioPlaybackState.Playing(2_000L, 5_000L))
        )
    val viewModel =
        setContent(
            state =
                SeeAudiosUIState.Content(
                    UserProfile(uid = "patient", role = Role.PATIENT, person = Person()),
                    CareCircle(id = "circle1"),
                    listOf(audio),
                ),
            playbackStates = playbackStates,
        )

    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.TIME).assertTextEquals("0:02 / 0:05")
    composeTestRule.runOnIdle {
      playbackStates.value = mapOf(audio.id to AudioPlaybackState.Error())
    }
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.ERROR)
        .assertTextEquals(string(R.string.audioPlaybackError))
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.RETRY).performClick()
    verify { viewModel.onPlayPause(audio) }
  }

  @Test
  fun content_shows_audio_and_card_and_calls_controls() {
    val audio = MediaItem.Audio(id = "a1")
    var played: String? = null
    var replayed: String? = null
    setContent(
        state =
            SeeAudiosUIState.Content(
                UserProfile(uid = "patient", role = Role.PATIENT, person = Person()),
                CareCircle(id = "circle1"),
                listOf(audio),
            ),
        onPlayAudio = { played = it },
        onReplayAudio = { replayed = it },
    )

    composeTestRule.onNodeWithTag(SeeAudiosTestTags.audio("a1")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SeeAudiosTestTags.audioCard("a1")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE).performClick()
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.RESTART).performClick()
    assertEquals("a1", played)
    assertEquals("a1", replayed)
  }

  @Test
  fun empty_state_shows_the_audio_message() {
    setContent()

    composeTestRule
        .onNodeWithTag(SeeAudiosTestTags.NO_AUDIOS)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.seeAudiosEmpty))
  }

  @Test
  fun title_and_navigation_menus_are_displayed() {
    setContent()

    composeTestRule.onNodeWithTag(SeeAudiosTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU)
        .assertIsDisplayed()
  }

  @Test
  fun back_button_calls_onBackClick() {
    var clicked = false
    setContent(onBackClick = { clicked = true })

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    assertTrue(clicked)
  }

  @Test
  fun bottom_tab_calls_onTabSelected() {
    var selected: Tab? = null
    setContent(onTabSelected = { selected = it })

    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.tabTag(PatientTab.Home))
        .performClick()

    assertEquals(PatientTab.Home, selected)
  }

  @Test
  fun preview_displays_the_empty_message() {
    val viewModel = mockk<SeeAudiosViewModel>(relaxed = true)
    every { viewModel.uiState } returns
        MutableStateFlow(
            SeeAudiosUIState.Empty(
                UserProfile(uid = "patient", role = Role.PATIENT, person = Person()),
                CareCircle(id = "circle1"),
            )
        )
    composeTestRule.setContent {
      CompositionLocalProvider(LocalViewModelStoreOwner provides testOwner(viewModel)) {
        SeeAudiosScreenPreview()
      }
    }

    composeTestRule
        .onNodeWithTag(SeeAudiosTestTags.NO_AUDIOS)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.seeAudiosEmpty))
  }

  @Test
  @Config(qualifiers = "fr")
  fun screenIsShownInFrench() {
    setContent()

    composeTestRule.onNodeWithTag(SeeAudiosTestTags.TITLE).assertTextEquals("Voir les audios")
    composeTestRule
        .onNodeWithTag(SeeAudiosTestTags.NO_AUDIOS)
        .assertTextEquals("Il n'y a pas encore d'audios.")
  }
}
