package com.github.se.amber.ui.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.ui.theme.AmberAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaCardTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun mediaCard_composesWithoutCrashing() {
    composeTestRule.setContent { AmberAppTheme { MediaCard() } }
    composeTestRule.waitForIdle()
  }

  @Test
  fun mediaCard_composesWithExplicitParameters() {
    val viewModel = MediaCardViewModel()

    composeTestRule.setContent {
      AmberAppTheme {
        MediaCard(
            modifier = Modifier,
            media = MediaItem.Picture(),
            testTag = "media_card",
            mediaCardViewModel = viewModel,
        )
      }
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun mediaCard_recomposes() {
    var tag by mutableStateOf("tag_1")
    var counter by mutableStateOf(0)
    val viewModel = MediaCardViewModel()

    composeTestRule.setContent {
      AmberAppTheme {
        counter
        MediaCard(testTag = tag, mediaCardViewModel = viewModel)
      }
    }

    composeTestRule.runOnIdle { tag = "tag_2" }
    composeTestRule.runOnIdle { counter++ }
    composeTestRule.waitForIdle()
  }
}
