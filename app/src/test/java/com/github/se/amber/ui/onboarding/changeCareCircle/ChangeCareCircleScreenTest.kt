// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.onboarding.changeCareCircle

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ChangeCareCircleScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private fun showScreen(
      state: ChangeCareCircleUIState = ChangeCareCircleUIState(),
      onBackClick: () -> Unit = {},
  ) {
    val viewModel = ChangeCareCircleViewModel(state)
    composeTestRule.setContent {
      AmberAppTheme(dynamicColor = false) {
        ChangeCareCircleScreen(
            viewModel = viewModel,
            onBackClick = onBackClick,
        )
      }
    }
  }

  /** Returns a context whose resources are forced to [languageTag], whatever the test locale is. */
  private fun contextFor(languageTag: String): Context {
    val base = ApplicationProvider.getApplicationContext<Context>()
    val configuration =
        Configuration(base.resources.configuration).apply {
          setLocale(Locale.forLanguageTag(languageTag))
        }
    return base.createConfigurationContext(configuration)
  }

  @Test
  fun emptyStateShowsMessageWithoutCircleSections() {
    showScreen(ChangeCareCircleUIState(status = ChangeCareCircleStatus.EMPTY))

    val message =
        ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.changeCareCircleEmpty)
    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.EMPTY_MESSAGE).assertTextEquals(message)
    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.CURRENT_CIRCLE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.CIRCLE_LIST).assertDoesNotExist()
  }

  @Test
  fun defaultViewModelShowsLoadingState() {
    composeTestRule.setContent { AmberAppTheme(dynamicColor = false) { ChangeCareCircleScreen() } }

    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.LOADING_INDICATOR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.EMPTY_MESSAGE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.LOADING_INDICATOR).assertIsDisplayed()
  }

  @Test
  fun loadingStateShowsIndicatorInsteadOfMessage() {
    showScreen(ChangeCareCircleUIState(status = ChangeCareCircleStatus.LOADING))

    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.LOADING_INDICATOR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.EMPTY_MESSAGE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.CIRCLE_LIST).assertDoesNotExist()
  }

  @Test
  fun readyStateShowsCurrentCircleAndSelectedChip() {
    showScreen(
        ChangeCareCircleUIState(
            status = ChangeCareCircleStatus.READY,
            circles =
                listOf(
                    ChangeCareCircleChoice("first", "Family"),
                    ChangeCareCircleChoice("second", "Friends"),
                ),
            activeCircleId = "second",
        )
    )

    composeTestRule
        .onNodeWithTag(ChangeCareCircleTestTags.CURRENT_CIRCLE)
        .assertTextEquals("Friends")
    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.CIRCLE_LIST).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(ChangeCareCircleTestTags.circleChoice("first"))
        .assertIsNotSelected()
    composeTestRule
        .onNodeWithTag(ChangeCareCircleTestTags.circleChoice("second"))
        .assertIsSelected()
    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.EMPTY_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun readyStateWithoutMatchingActiveCircleShowsFallback() {
    showScreen(
        ChangeCareCircleUIState(
            status = ChangeCareCircleStatus.READY,
            circles = listOf(ChangeCareCircleChoice("first", "Family")),
            activeCircleId = "missing",
        )
    )

    val fallback =
        ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.changeCareCircleNone)
    composeTestRule
        .onNodeWithTag(ChangeCareCircleTestTags.CURRENT_CIRCLE)
        .assertTextEquals(fallback)
    composeTestRule
        .onNodeWithTag(ChangeCareCircleTestTags.circleChoice("first"))
        .assertIsNotSelected()
  }

  @Test
  fun readyStateWithNoCirclesShowsFallbackAndNoChips() {
    showScreen(ChangeCareCircleUIState(status = ChangeCareCircleStatus.READY))

    val fallback =
        ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.changeCareCircleNone)
    composeTestRule
        .onNodeWithTag(ChangeCareCircleTestTags.CURRENT_CIRCLE)
        .assertTextEquals(fallback)
    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.CIRCLE_LIST).assertExists()
    composeTestRule
        .onNodeWithTag(ChangeCareCircleTestTags.circleChoice("first"))
        .assertDoesNotExist()
  }

  @Test
  fun circleChipHasClickAction() {
    showScreen(
        ChangeCareCircleUIState(
            status = ChangeCareCircleStatus.READY,
            circles = listOf(ChangeCareCircleChoice("first", "Family")),
            activeCircleId = "first",
        )
    )

    composeTestRule
        .onNodeWithTag(ChangeCareCircleTestTags.circleChoice("first"))
        .assertHasClickAction()
        .performClick()
  }

  @Test
  fun backButtonCallsCallback() {
    var backClicks = 0
    showScreen(onBackClick = { backClicks++ })

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    assertEquals(1, backClicks)
  }

  @Test
  fun loadingPreviewRendersItsIndicator() {
    composeTestRule.setContent { ChangeCareCircleLoadingScreenPreview() }

    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.LOADING_INDICATOR).assertIsDisplayed()
  }

  @Test
  fun emptyPreviewRendersItsMessage() {
    composeTestRule.setContent { ChangeCareCircleEmptyScreenPreview() }

    val message =
        ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.changeCareCircleEmpty)
    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.EMPTY_MESSAGE).assertTextEquals(message)
  }

  @Test
  fun readyPreviewRendersItsSelectedCircle() {
    composeTestRule.setContent { ChangeCareCircleScreenPreview() }

    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.circleChoice("two")).assertIsSelected()
  }

  @Test
  @Config(qualifiers = "fr")
  fun emptyMessageIsDisplayedInFrenchWhenPhoneIsInFrench() {
    showScreen(ChangeCareCircleUIState(status = ChangeCareCircleStatus.EMPTY))

    composeTestRule
        .onNodeWithTag(ChangeCareCircleTestTags.EMPTY_MESSAGE)
        .assertTextEquals(contextFor("fr").getString(R.string.changeCareCircleEmpty))
  }

  @Test
  fun emptyMessageFrenchTranslationDiffersFromEnglish() {
    assertNotEquals(
        contextFor("en").getString(R.string.changeCareCircleEmpty),
        contextFor("fr").getString(R.string.changeCareCircleEmpty),
    )
  }
}
