// Written by MeiliV, with assistance from OpenAI Codex.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.patient.seeMedias.audios

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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
import com.github.se.amber.ui.media.MediaCardTestTags
import com.github.se.amber.ui.media.MediaCardViewModel
import com.github.se.amber.ui.media.audio.AudioPlaybackState
import com.github.se.amber.ui.media.audio.AudioPlaybackTestTags
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.patient.PatientUtilsTestTags
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
import org.robolectric.shadows.ShadowToast

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
      uiState: MutableStateFlow<SeeAudiosUIState> = MutableStateFlow(state),
      playbackStates: MutableStateFlow<Map<String, AudioPlaybackState>> =
          MutableStateFlow(emptyMap()),
  ): SeeAudiosViewModel {
    val viewModel = mockk<SeeAudiosViewModel>(relaxed = true)
    every { viewModel.uiState } returns uiState
    every { viewModel.playbackStates } returns playbackStates
    composeTestRule.setContent {
      CompositionLocalProvider(LocalViewModelStoreOwner provides testOwner(viewModel)) {
        AmberAppTheme(dynamicColor = false) {
          SeeAudiosScreen(
              audioViewModel = viewModel,
              onBackClick = onBackClick,
              onTabSelected = onTabSelected,
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
    val viewModel =
        setContent(
            state =
                SeeAudiosUIState.Content(
                    UserProfile(uid = "patient", role = Role.PATIENT, person = Person()),
                    CareCircle(id = "circle1"),
                    listOf(audio),
                ),
        )

    composeTestRule.onNodeWithTag(SeeAudiosTestTags.audio("a1")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SeeAudiosTestTags.audioCard("a1")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE).performClick()
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.RESTART).performClick()
    verify(exactly = 1) { viewModel.onPlayPause(audio) }
    verify(exactly = 1) { viewModel.onRestart(audio) }
  }

  @Test
  fun content_updates_when_audios_are_removed() {
    val profile = UserProfile(uid = "patient", role = Role.PATIENT, person = Person())
    val circle = CareCircle(id = "circle1")
    val audio = MediaItem.Audio(id = "a1")
    val secondAudio = MediaItem.Audio(id = "a2")
    val uiState =
        MutableStateFlow<SeeAudiosUIState>(
            SeeAudiosUIState.Content(profile, circle, listOf(audio, secondAudio))
        )
    setContent(uiState = uiState)
    composeTestRule.onNodeWithTag(SeeAudiosTestTags.audio("a1")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SeeAudiosTestTags.audio("a2")).assertIsDisplayed()

    composeTestRule.runOnIdle {
      uiState.value = SeeAudiosUIState.Content(profile, circle, listOf(secondAudio))
    }
    composeTestRule.onNodeWithTag(SeeAudiosTestTags.audio("a1")).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SeeAudiosTestTags.audio("a2")).assertIsDisplayed()

    composeTestRule.runOnIdle { uiState.value = SeeAudiosUIState.Empty(profile, circle) }
    composeTestRule.onNodeWithTag(SeeAudiosTestTags.audio("a2")).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SeeAudiosTestTags.NO_AUDIOS).assertIsDisplayed()
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
  fun loading_hides_messages_and_audio_controls() {
    setContent(state = SeeAudiosUIState.Loading)

    composeTestRule.onNodeWithTag(SeeAudiosTestTags.NO_AUDIOS).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SeeAudiosTestTags.ERROR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.CARD).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SeeAudiosTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SeeAudiosTestTags.LOADING).assertIsDisplayed()
  }

  @Test
  fun blocking_error_shows_message_and_retry_calls_view_model() {
    val viewModel =
        setContent(state = SeeAudiosUIState.BlockingError(R.string.seeAudiosErrorLoadMedia))

    composeTestRule
        .onNodeWithTag(SeeAudiosTestTags.ERROR)
        .assertTextEquals(string(R.string.seeAudiosErrorLoadMedia))
    composeTestRule.onNodeWithTag(SeeAudiosTestTags.NO_AUDIOS).assertDoesNotExist()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).performClick()
    verify(exactly = 1) { viewModel.retry() }
  }

  @Test
  fun empty_and_content_errors_show_toast_without_hiding_the_screen() {
    val profile = UserProfile(uid = "patient", role = Role.PATIENT, person = Person())
    val circle = CareCircle(id = "circle1")
    val uiState =
        MutableStateFlow<SeeAudiosUIState>(
            SeeAudiosUIState.Empty(profile, circle, errorMsgRes = R.string.seeAudiosErrorLoadMedia)
        )
    val viewModel =
        setContent(
            uiState = uiState,
        )

    composeTestRule.waitForIdle()
    assertEquals(string(R.string.seeAudiosErrorLoadMedia), ShadowToast.getTextOfLatestToast())
    verify(exactly = 1) { viewModel.clearErrorMsg() }
    composeTestRule.onNodeWithTag(SeeAudiosTestTags.NO_AUDIOS).assertIsDisplayed()

    val audio = MediaItem.Audio(id = "a1")
    composeTestRule.runOnIdle { uiState.value = SeeAudiosUIState.Empty(profile, circle) }
    composeTestRule.waitForIdle()
    composeTestRule.runOnIdle {
      uiState.value =
          SeeAudiosUIState.Content(
              profile,
              circle,
              listOf(audio),
              errorMsgRes = R.string.seeAudiosErrorLoadMedia,
          )
    }

    composeTestRule.waitForIdle()
    assertEquals(string(R.string.seeAudiosErrorLoadMedia), ShadowToast.getTextOfLatestToast())
    verify(exactly = 2) { viewModel.clearErrorMsg() }
    composeTestRule.onNodeWithTag(SeeAudiosTestTags.audio(audio.id)).assertIsDisplayed()
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
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.CARD).assertDoesNotExist()
  }

  @Test
  fun loading_error_and_content_previews_show_their_states() {
    var preview by mutableIntStateOf(0)
    composeTestRule.setContent {
      when (preview) {
        0 -> SeeAudiosLoadingPreview()
        1 -> SeeAudiosErrorPreview()
        else -> SeeAudiosContentPreview()
      }
    }

    composeTestRule.onNodeWithTag(SeeAudiosTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SeeAudiosTestTags.LOADING).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SeeAudiosTestTags.audio("sample1")).assertDoesNotExist()

    composeTestRule.runOnIdle { preview = 1 }
    composeTestRule
        .onNodeWithTag(SeeAudiosTestTags.ERROR)
        .assertTextEquals(string(R.string.seeAudiosErrorLoadProfile))
    composeTestRule.onNodeWithTag(SeeAudiosTestTags.LOADING).assertDoesNotExist()

    composeTestRule.runOnIdle { preview = 2 }
    composeTestRule.onNodeWithTag(SeeAudiosTestTags.audio("sample1")).assertExists()
    composeTestRule.onNodeWithTag(SeeAudiosTestTags.audio("sample2")).assertExists()
    composeTestRule.onNodeWithTag(SeeAudiosTestTags.audioCard("sample1")).assertExists()
    composeTestRule.onNodeWithTag(SeeAudiosTestTags.audioCard("sample2")).assertExists()
    composeTestRule.onNodeWithTag(SeeAudiosTestTags.ERROR).assertDoesNotExist()
  }

  @Test
  fun content_preview_expands_sample_card_with_people() {
    composeTestRule.setContent { SeeAudiosContentPreview() }

    composeTestRule.onNodeWithTag(MediaCardTestTags.toggleButtonTextShow("sample1")).performClick()

    composeTestRule
        .onNodeWithTag(MediaCardTestTags.description("sample1"))
        .assertTextEquals("I have the best gossip...")
    composeTestRule
        .onNodeWithTag(MediaCardTestTags.relatedItem("sample1", 0))
        .assertTextEquals("Evangeline")
  }

  @Test
  fun error_preview_retry_keeps_blocking_error_visible() {
    composeTestRule.setContent { SeeAudiosErrorPreview() }

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).performClick()

    composeTestRule
        .onNodeWithTag(SeeAudiosTestTags.ERROR)
        .assertTextEquals(string(R.string.seeAudiosErrorLoadProfile))
  }

  @Test
  fun content_preview_second_card_can_expand_and_collapse() {
    composeTestRule.setContent { SeeAudiosContentPreview() }

    composeTestRule.onNodeWithTag(MediaCardTestTags.toggleButtonTextShow("sample2")).performClick()
    composeTestRule
        .onNodeWithTag(MediaCardTestTags.description("sample2"))
        .assertTextEquals("I saw otters at the zoo today!")

    composeTestRule.onNodeWithTag(MediaCardTestTags.toggleButtonTextHide("sample2")).performClick()
    composeTestRule.onNodeWithTag(MediaCardTestTags.description("sample2")).assertDoesNotExist()
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
