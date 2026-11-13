// Written by Yuhan YANG, with assistance from Cursor
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.caregiver.parameters.createIntivation

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.data.invitation.InvitationStatus
import com.github.se.amber.data.invitation.InvitationSummary
import com.github.se.amber.ui.caregiver.DisplayCaregiverButtonTestTags
import com.github.se.amber.ui.caregiver.parameters.createInvitation.CareCircleChoice
import com.github.se.amber.ui.caregiver.parameters.createInvitation.CreateInvitationDarkPreview
import com.github.se.amber.ui.caregiver.parameters.createInvitation.CreateInvitationScreen
import com.github.se.amber.ui.caregiver.parameters.createInvitation.CreateInvitationScreenPreview
import com.github.se.amber.ui.caregiver.parameters.createInvitation.CreateInvitationTestTags
import com.github.se.amber.ui.caregiver.parameters.createInvitation.CreateInvitationUIState
import com.github.se.amber.ui.caregiver.parameters.createInvitation.CreateInvitationViewModel
import com.github.se.amber.ui.caregiver.parameters.createInvitation.formatCreatedAt
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.CaregiverTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.theme.darkOrange
import com.github.se.amber.ui.theme.lightOrange
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tests what [CreateInvitationScreen] draws and which actions a click performs.
 *
 * The ViewModel is mocked, so these tests do not load a profile or call Firebase.
 */
@RunWith(RobolectricTestRunner::class)
class CreateInvitationScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** Text of [id] in the current test language. */
  private fun string(id: Int, vararg args: Any): String =
      ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

  @Test
  fun showsPlaceholderUsageAndListHeaderWithoutACircleSelector() {
    setScreen(
        viewModel(
            CreateInvitationUIState(
                circles = listOf(CareCircleChoice("circle-1", "Arthur's family"))
            )
        )
    )

    composeTestRule
        .onNodeWithTag(CreateInvitationTestTags.HINT_BOX)
        .assertTextContains(string(R.string.createInvitationPlaceholder))
    composeTestRule
        .onNodeWithTag(CreateInvitationTestTags.USAGE_TEXT)
        .assertTextEquals(string(R.string.createInvitationUsage))
    composeTestRule.onNodeWithText(string(R.string.createInvitationListHeader)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(CreateInvitationTestTags.ERROR_MESSAGE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(CreateInvitationTestTags.CIRCLE_CHOICES).assertDoesNotExist()
    composeTestRule.onNodeWithTag(CreateInvitationTestTags.CREATE_CARD).assertIsEnabled()
    composeTestRule
        .onNodeWithTag(DisplayCaregiverButtonTestTags.ICON, useUnmergedTree = true)
        .assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "+en-rUS")
  fun showsCodeInvitationAndErrorAndDisablesCreateWhileCreating() {
    val createdAt = 1_700_000_000_000L
    setScreen(
        viewModel(
            CreateInvitationUIState(
                generatedCode = "AB12CD34",
                invitations =
                    listOf(
                        InvitationSummary(
                            code = "AB12CD34",
                            createdAt = createdAt,
                            expiresAt = createdAt + 86_400_000L,
                            status = InvitationStatus.ACTIVE,
                        ),
                    ),
                errorMsgRes = R.string.createInvitationErrorNotAuthenticated,
                isCreating = true,
            ),
        ),
    )
    val createdAtText =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.US)
            .format(Date(createdAt))

    composeTestRule.onNodeWithTag(CreateInvitationTestTags.HINT_BOX).assertTextContains("AB12CD34")
    composeTestRule
        .onNodeWithText(
            "AB12CD34 | $createdAtText | ${string(R.string.createInvitationStatusActive)}"
        )
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(CreateInvitationTestTags.ERROR_MESSAGE)
        .assertTextEquals(string(R.string.createInvitationErrorNotAuthenticated))
    composeTestRule.onNodeWithTag(CreateInvitationTestTags.CREATE_CARD).assertIsNotEnabled()
  }

  @Test
  fun createClickAndCircleClickNotifyTheScreenCallbacks() {
    val viewModel =
        viewModel(
            CreateInvitationUIState(
                circles =
                    listOf(
                        CareCircleChoice("circle-1", "Arthur's family"),
                        CareCircleChoice("circle-2", "Bea's family"),
                    ),
                selectedCircleId = "circle-1",
            ),
        )
    setScreen(viewModel)

    composeTestRule.onNodeWithTag(CreateInvitationTestTags.CIRCLE_CHOICES).assertIsDisplayed()
    composeTestRule.onNodeWithText("Arthur's family").assertIsDisplayed()
    composeTestRule.onNodeWithText("Bea's family").assertIsDisplayed()
    composeTestRule.onNodeWithTag(CreateInvitationTestTags.circleChoice("circle-2")).performClick()
    composeTestRule.onNodeWithTag(CreateInvitationTestTags.CREATE_CARD).performClick()

    verify(exactly = 1) { viewModel.selectCircle("circle-2") }
    verify(exactly = 1) { viewModel.createInvitation() }
  }

  @Test
  @Config(qualifiers = "fr")
  fun screenAndErrorAreShownInFrench() {
    setScreen(
        viewModel(CreateInvitationUIState(errorMsgRes = R.string.createInvitationErrorNetwork))
    )

    composeTestRule
        .onNodeWithTag(CreateInvitationTestTags.TITLE)
        .assertTextEquals("Créer une invitation")
    composeTestRule
        .onNodeWithTag(CreateInvitationTestTags.HINT_BOX)
        .assertTextContains("Appuyez sur le bouton de gauche pour créer un code")
    composeTestRule
        .onNodeWithTag(CreateInvitationTestTags.USAGE_TEXT)
        .assertTextEquals(
            "Ce code ne peut être utilisé qu'une seule fois.\nIl reste actif 30 jours après sa création."
        )
    composeTestRule.onNodeWithText("Code | Date de création | Statut").assertExists()
    composeTestRule
        .onNodeWithTag(CreateInvitationTestTags.ERROR_MESSAGE)
        .assertTextEquals("Erreur réseau. Vérifiez votre connexion, puis réessayez")
  }

  @Test
  @Config(qualifiers = "fr")
  fun invitationRowShowsTheStatusAndDateInFrench() {
    val createdAt = 1_700_000_000_000L
    setScreen(
        viewModel(
            CreateInvitationUIState(
                invitations =
                    listOf(
                        InvitationSummary(
                            code = "USED1234",
                            createdAt = createdAt,
                            expiresAt = createdAt + 86_400_000L,
                            status = InvitationStatus.USED,
                        ),
                    ),
            ),
        ),
    )
    val createdAtText =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.FRENCH)
            .format(Date(createdAt))

    composeTestRule.onNodeWithText("USED1234 | $createdAtText | UTILISÉE").assertExists()
  }

  @Test
  @Config(qualifiers = "+en-rUS")
  fun expiredInvitationShowsTheExpiredStatus() {
    val createdAt = 1_700_000_000_000L
    setScreen(
        viewModel(
            CreateInvitationUIState(
                invitations =
                    listOf(
                        InvitationSummary(
                            code = "EXP12345",
                            createdAt = createdAt,
                            expiresAt = createdAt,
                            status = InvitationStatus.EXPIRED,
                        ),
                    ),
            ),
        ),
    )
    val createdAtText =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.US)
            .format(Date(createdAt))

    composeTestRule
        .onNodeWithTag(CreateInvitationTestTags.codeRow("EXP12345"))
        .assertTextEquals(
            string(
                R.string.createInvitationListRow,
                "EXP12345",
                createdAtText,
                string(R.string.createInvitationStatusExpired),
            ),
        )
  }

  @Test
  fun circleChoiceIsDisabledWhileAnInvitationIsBeingCreated() {
    setScreen(
        viewModel(
            CreateInvitationUIState(
                circles =
                    listOf(
                        CareCircleChoice("circle-1", "Arthur's family"),
                        CareCircleChoice("circle-2", "Bea's family"),
                    ),
                selectedCircleId = "circle-1",
                isCreating = true,
            ),
        ),
    )

    composeTestRule
        .onNodeWithTag(CreateInvitationTestTags.circleChoice("circle-2"))
        .assertIsNotEnabled()
  }

  @Test
  fun defaultNavigationCallbacksLeaveTheScreenOpen() {
    composeTestRule.setContent {
      CreateInvitationScreen(viewModel = viewModel(CreateInvitationUIState()))
    }

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.tabTag(CaregiverTab.Home))
        .performClick()

    composeTestRule.onNodeWithTag(CreateInvitationTestTags.TITLE).assertIsDisplayed()
  }

  @Test
  fun formatCreatedAtWithoutArgumentsUsesNowAndTheDefaultLocale() {
    val before = System.currentTimeMillis()
    val text = formatCreatedAt()
    val after = System.currentTimeMillis()
    val format =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
    val accepted = setOf(format.format(Date(before)), format.format(Date(after)))

    assertTrue("$text was not the current short date", text in accepted)
  }

  @Test
  fun cancelCallsCheckout() {
    var checkedOut = false
    setScreen(viewModel = viewModel(CreateInvitationUIState()), checkout = { checkedOut = true })

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    assertTrue(checkedOut)
  }

  @Test
  fun selectingAnotherTabReportsThatTab() {
    var selected: Tab? = null
    setScreen(viewModel = viewModel(CreateInvitationUIState()), onTabSelected = { selected = it })

    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.tabTag(CaregiverTab.Home))
        .performClick()

    assertEquals(CaregiverTab.Home, selected)
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun errorTextIsDarkOrangeInLightMode() {
    setScreen(
        viewModel(
            CreateInvitationUIState(errorMsgRes = R.string.createInvitationErrorProfileNotFound)
        ),
    )

    composeTestRule
        .onNodeWithTag(CreateInvitationTestTags.ERROR_MESSAGE)
        .assertTextEquals(string(R.string.createInvitationErrorProfileNotFound))
    assertDrawnColor(CreateInvitationTestTags.ERROR_MESSAGE, darkOrange, lightOrange)
  }

  @Test
  @Config(qualifiers = "night")
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun errorTextIsLightOrangeInDarkMode() {
    setScreen(
        viewModel(
            CreateInvitationUIState(errorMsgRes = R.string.createInvitationErrorProfileNotFound)
        ),
    )

    composeTestRule
        .onNodeWithTag(CreateInvitationTestTags.ERROR_MESSAGE)
        .assertTextEquals(string(R.string.createInvitationErrorProfileNotFound))
    assertDrawnColor(CreateInvitationTestTags.ERROR_MESSAGE, lightOrange, darkOrange)
  }

  @Test
  fun lightPreviewShowsTheMissingProfileError() {
    composeTestRule.setContent { CreateInvitationScreenPreview() }
    waitForError()

    composeTestRule
        .onNodeWithTag(CreateInvitationTestTags.ERROR_MESSAGE)
        .assertTextEquals(string(R.string.createInvitationErrorProfileNotFound))
  }

  @Test
  @Config(qualifiers = "night")
  fun darkPreviewShowsTheMissingProfileError() {
    composeTestRule.setContent { CreateInvitationDarkPreview() }
    waitForError()

    composeTestRule
        .onNodeWithTag(CreateInvitationTestTags.ERROR_MESSAGE)
        .assertTextEquals(string(R.string.createInvitationErrorProfileNotFound))
  }

  private fun waitForError() {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule
          .onAllNodesWithTag(CreateInvitationTestTags.ERROR_MESSAGE)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
  }

  /**
   * Some opaque pixels of [tag] are [expected], and more of them are [expected] than [other].
   *
   * The node also contains the screen background, so a majority of pixels is not the text.
   */
  private fun assertDrawnColor(tag: String, expected: Color, other: Color) {
    val pixels = composeTestRule.onNodeWithTag(tag).captureToImage().toPixelMap()
    var expectedCount = 0
    var otherCount = 0
    for (x in 0 until pixels.width) {
      for (y in 0 until pixels.height) {
        val pixel = pixels[x, y]
        if (pixel.alpha < 0.9f) continue
        if (pixel.isCloseTo(expected)) expectedCount += 1
        if (pixel.isCloseTo(other)) otherCount += 1
      }
    }
    assertTrue(
        "$tag had $expectedCount pixels of $expected and $otherCount of $other",
        expectedCount >= 30 && otherCount * 3 < expectedCount,
    )
  }

  private fun Color.isCloseTo(other: Color): Boolean {
    fun Float.near(value: Float) = abs(this - value) < 0.08f
    return red.near(other.red) && green.near(other.green) && blue.near(other.blue)
  }

  private fun viewModel(state: CreateInvitationUIState): CreateInvitationViewModel =
      mockk(relaxed = true) { every { uiState } returns MutableStateFlow(state) }

  private fun setScreen(
      viewModel: CreateInvitationViewModel,
      onTabSelected: (Tab) -> Unit = {},
      checkout: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      CreateInvitationScreen(
          viewModel = viewModel,
          onTabSelected = onTabSelected,
          checkout = checkout,
      )
    }
  }
}
