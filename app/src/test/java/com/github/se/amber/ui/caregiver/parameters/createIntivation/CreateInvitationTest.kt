// Written by Yuhan YANG, with assistance from Cursor
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.parameters.createIntivation

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.data.invitation.InvitationStatus
import com.github.se.amber.data.invitation.InvitationSummary
import com.github.se.amber.ui.caregiver.parameters.createInvitation.CareCircleChoice
import com.github.se.amber.ui.caregiver.parameters.createInvitation.CreateInvitationScreen
import com.github.se.amber.ui.caregiver.parameters.createInvitation.CreateInvitationTestTags
import com.github.se.amber.ui.caregiver.parameters.createInvitation.CreateInvitationUiState
import com.github.se.amber.ui.caregiver.parameters.createInvitation.CreateInvitationViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests what [CreateInvitationScreen] draws and which actions a click performs.
 *
 * The ViewModel is mocked, so these tests do not load a profile or call Firebase.
 */
@RunWith(RobolectricTestRunner::class)
class CreateInvitationScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** Text of [id] in the current test language. */
  private fun string(id: Int): String =
      ApplicationProvider.getApplicationContext<Context>().getString(id)

  @Test
  fun showsPlaceholderUsageAndListHeaderWithoutACircleSelector() {
    setScreen(
        viewModel(
            CreateInvitationUiState(
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
  }

  @Test
  fun showsCodeInvitationAndErrorAndDisablesCreateWhileCreating() {
    val createdAt = 1_700_000_000_000L
    setScreen(
        viewModel(
            CreateInvitationUiState(
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
                errorMessageRes = R.string.createInvitationErrorNotAuthenticated,
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
            CreateInvitationUiState(
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
        viewModel(CreateInvitationUiState(errorMessageRes = R.string.createInvitationErrorNetwork))
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
            CreateInvitationUiState(
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

  private fun viewModel(state: CreateInvitationUiState): CreateInvitationViewModel =
      mockk(relaxed = true) { every { uiState } returns MutableStateFlow(state) }

  private fun setScreen(viewModel: CreateInvitationViewModel) {
    composeTestRule.setContent { CreateInvitationScreen(viewModel = viewModel) }
  }
}
