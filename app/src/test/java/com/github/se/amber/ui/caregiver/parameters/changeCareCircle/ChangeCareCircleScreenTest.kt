// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.caregiver.parameters.changeCareCircle

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
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.circle.ActiveCircleRepositoryProvider
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class ChangeCareCircleScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val profileRepository = mockk<ProfileRepository>()
  private val circleRepository = mockk<CareCircleRepository>()
  private val activeCircleRepository = mockk<ActiveCircleRepository>()

  @Before
  fun setUp() {
    mockkObject(
        ProfileRepositoryProvider.default,
        CareCircleRepositoryProvider.default,
        ActiveCircleRepositoryProvider.default,
    )
    every { ProfileRepositoryProvider.default.repository } returns profileRepository
    every { CareCircleRepositoryProvider.default.repository } returns circleRepository
    every { ActiveCircleRepositoryProvider.default.repository } returns activeCircleRepository
  }

  @After
  fun tearDown() {
    unmockkObject(
        ProfileRepositoryProvider.default,
        CareCircleRepositoryProvider.default,
        ActiveCircleRepositoryProvider.default,
    )
  }

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

  private fun emptyMessage(context: Context): String =
      context.getString(
          R.string.changeCareCircleEmpty,
          context.getString(R.string.navigationTabSetting),
          context.getString(R.string.caregiverParameterCreateCareCircle),
          context.getString(R.string.caregiverParameterEnterInvitation),
      )

  @Test
  fun emptyStateShowsMessageWithoutCircleSections() {
    showScreen(ChangeCareCircleUIState(status = ChangeCareCircleStatus.EMPTY))

    val message = emptyMessage(ApplicationProvider.getApplicationContext())
    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.EMPTY_MESSAGE).assertTextEquals(message)
    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.CURRENT_CIRCLE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.CIRCLE_LIST).assertDoesNotExist()
  }

  @Test
  fun blockingErrorShowsMessageAndRetryButton() {
    showScreen(
        ChangeCareCircleUIState(
            status = ChangeCareCircleStatus.ERROR,
            errorMsgRes = R.string.changeCareCircleErrorLoadProfile,
        )
    )

    composeTestRule
        .onNodeWithTag(ChangeCareCircleTestTags.ERROR_MESSAGE)
        .assertTextEquals(
            ApplicationProvider.getApplicationContext<Context>()
                .getString(R.string.changeCareCircleErrorLoadProfile)
        )
    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.RETRY_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.RETRY_BUTTON).assertHasClickAction()
    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.CIRCLE_LIST).assertDoesNotExist()
  }

  @Test
  fun blockingErrorWithoutResourceShowsCircleError() {
    showScreen(ChangeCareCircleUIState(status = ChangeCareCircleStatus.ERROR))

    composeTestRule
        .onNodeWithTag(ChangeCareCircleTestTags.ERROR_MESSAGE)
        .assertTextEquals(
            ApplicationProvider.getApplicationContext<Context>()
                .getString(R.string.changeCareCircleErrorLoadCircle)
        )
  }

  @Test
  fun selectionErrorShowsToastInReadyState() {
    showScreen(
        ChangeCareCircleUIState(
            status = ChangeCareCircleStatus.READY,
            circles = listOf(ChangeCareCircleChoice("first", "Family")),
            activeCircleId = "first",
            errorMsgRes = R.string.changeCareCircleErrorChange,
        )
    )

    composeTestRule.waitForIdle()
    assertEquals(
        ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.changeCareCircleErrorChange),
        ShadowToast.getTextOfLatestToast(),
    )
    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.circleChoice("first")).assertIsSelected()
  }

  @Test
  fun retryButtonCallsViewModelLoad() {
    val viewModel =
        spyk(
            ChangeCareCircleViewModel(
                ChangeCareCircleUIState(
                    status = ChangeCareCircleStatus.ERROR,
                    errorMsgRes = R.string.changeCareCircleErrorLoadCircle,
                )
            )
        )
    every { viewModel.loadCareCircles() } returns Unit
    composeTestRule.setContent {
      AmberAppTheme(dynamicColor = false) { ChangeCareCircleScreen(viewModel = viewModel) }
    }

    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.RETRY_BUTTON).performClick()

    verify(exactly = 1) { viewModel.loadCareCircles() }
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

    val message = emptyMessage(ApplicationProvider.getApplicationContext())
    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.EMPTY_MESSAGE).assertTextEquals(message)
  }

  @Test
  fun readyPreviewRendersItsSelectedCircle() {
    composeTestRule.setContent { ChangeCareCircleScreenPreview() }

    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.circleChoice("two")).assertIsSelected()
  }

  @Test
  fun errorPreviewRendersRetryButton() {
    composeTestRule.setContent { ChangeCareCircleErrorScreenPreview() }

    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.ERROR_MESSAGE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ChangeCareCircleTestTags.RETRY_BUTTON).assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "fr")
  fun blockingErrorUsesFrenchText() {
    showScreen(
        ChangeCareCircleUIState(
            status = ChangeCareCircleStatus.ERROR,
            errorMsgRes = R.string.changeCareCircleErrorLoadProfile,
        )
    )

    composeTestRule
        .onNodeWithTag(ChangeCareCircleTestTags.ERROR_MESSAGE)
        .assertTextEquals(contextFor("fr").getString(R.string.changeCareCircleErrorLoadProfile))
  }

  @Test
  @Config(qualifiers = "fr")
  fun emptyMessageIsDisplayedInFrenchWhenPhoneIsInFrench() {
    showScreen(ChangeCareCircleUIState(status = ChangeCareCircleStatus.EMPTY))

    composeTestRule
        .onNodeWithTag(ChangeCareCircleTestTags.EMPTY_MESSAGE)
        .assertTextEquals(emptyMessage(contextFor("fr")))
  }

  @Test
  fun emptyMessageFrenchTranslationDiffersFromEnglish() {
    assertNotEquals(
        emptyMessage(contextFor("en")),
        emptyMessage(contextFor("fr")),
    )
  }
}
