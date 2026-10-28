// this code was written with the aid of AI

package com.github.se.amber.ui.patient.seeMedias

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SeeMediasScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var viewModel: SeeMediasViewModel

  @Before
  fun setUp() {
    viewModel = SeeMediasViewModel()
  }

  @Test
  fun screen_displaysTitleButtonsAndBottomNavigation() {
    composeTestRule.setContent { SeeMediasScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithText("See Medias").assertIsDisplayed()
    composeTestRule.onNodeWithText("See Pictures").assertIsDisplayed()
    composeTestRule.onNodeWithText("See Texts").assertIsDisplayed()
    composeTestRule.onNodeWithText("See Audio").assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU)
        .assertIsDisplayed()
  }

  @Test
  fun clickingButtons_triggersTheMatchingCallbackOnly() {
    var pictureClicks = 0
    var textClicks = 0
    var audioClicks = 0

    composeTestRule.setContent {
      SeeMediasScreen(
          viewModel = viewModel,
          onSeePictureClick = { pictureClicks++ },
          onSeeTextClick = { textClicks++ },
          onSeeAudioClick = { audioClicks++ },
      )
    }

    composeTestRule.onNodeWithText("See Pictures").performScrollTo().performClick()
    assertEquals(listOf(1, 0, 0), listOf(pictureClicks, textClicks, audioClicks))

    composeTestRule.onNodeWithText("See Texts").performScrollTo().performClick()
    assertEquals(listOf(1, 1, 0), listOf(pictureClicks, textClicks, audioClicks))

    composeTestRule.onNodeWithText("See Audio").performScrollTo().performClick()
    assertEquals(listOf(1, 1, 1), listOf(pictureClicks, textClicks, audioClicks))
  }

  @Test
  fun screen_withDefaultParameters_doesNotCrashOnClick() {
    // Default callbacks are no-ops, and the default viewModel() is provided by the activity.
    composeTestRule.setContent { SeeMediasScreen() }

    composeTestRule.onNodeWithText("See Pictures").performScrollTo().performClick()
    composeTestRule.onNodeWithText("See Texts").performScrollTo().performClick()
    composeTestRule.onNodeWithText("See Audio").performScrollTo().performClick()

    composeTestRule.onNodeWithText("See Medias").assertIsDisplayed()
  }
}
