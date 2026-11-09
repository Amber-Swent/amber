// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.media

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.se.amber.R
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.ui.media.audio.AudioPlaybackState
import com.github.se.amber.ui.media.audio.AudioPlaybackTestTags
import com.github.se.amber.ui.media.audio.DisplayAudioPlayback
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** Checks that the media card and the audio playback show their text in French. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class MediaTranslationTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val picture =
      MediaItem.Picture(
          id = "m1",
          description = "Une belle journée",
          date = null,
          location = GeoLocation(lat = null, lng = null, placeName = null),
      )

  private fun setCard(state: MediaCardUIState) {
    val viewModel = mockk<MediaCardViewModel>(relaxed = true)
    every { viewModel.uiState } returns MutableStateFlow(state)
    composeTestRule.setContent { MediaCard(media = picture, mediaCardViewModel = viewModel) }
    composeTestRule.waitForIdle()
  }

  @Test
  fun cardToggleUnknownDateAndUnknownLocationAreInFrench() {
    setCard(MediaCardUIState(isLoading = false))

    composeTestRule
        .onNodeWithTag(MediaCardTestTags.toggleButtonTextShow("m1"))
        .assertTextEquals("voir les infos")
    composeTestRule.onNodeWithTag(MediaCardTestTags.date("m1")).assertTextEquals("Date inconnue")
    composeTestRule.onNodeWithTag(MediaCardTestTags.location("m1")).assertTextEquals("Lieu inconnu")
  }

  @Test
  fun expandedCardShowsTheAuthorAndTheRelatedPersonsInFrench() {
    setCard(
        MediaCardUIState(
            addedByNickname = "Alice",
            relatedNicknames = listOf("Bob"),
            isLoading = false,
        )
    )

    composeTestRule.onNodeWithTag(MediaCardTestTags.toggleButtonTextShow("m1")).performClick()
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(MediaCardTestTags.toggleButtonTextHide("m1"))
        .assertTextEquals("masquer les infos")
    composeTestRule
        .onNodeWithTag(MediaCardTestTags.addedBy("m1"))
        .assertTextEquals("Ajouté par Alice")
    composeTestRule
        .onNodeWithTag(MediaCardTestTags.relatedTitle("m1"))
        .assertTextEquals("Souvenir partagé avec")
  }

  @Test
  fun cardErrorToastIsInFrench() {
    setCard(
        MediaCardUIState(isLoading = false, errorMsgRes = R.string.mediaCardErrorAuthorNotFound)
    )

    assertEquals("Auteur introuvable.", ShadowToast.getTextOfLatestToast())
  }

  @Test
  fun playbackButtonsAreReadInFrench() {
    composeTestRule.setContent { DisplayAudioPlayback(state = AudioPlaybackState.Idle()) }

    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE)
        .assertContentDescriptionEquals("Lire")
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.RESTART)
        .assertContentDescriptionEquals("Recommencer")
  }

  @Test
  fun playbackErrorAndRetryAreInFrench() {
    composeTestRule.setContent { DisplayAudioPlayback(state = AudioPlaybackState.Error()) }

    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.ERROR)
        .assertTextEquals("Cet audio n'a pas pu être lu.")
    composeTestRule.onNodeWithText("Réessayer").assertIsDisplayed()
  }
}
