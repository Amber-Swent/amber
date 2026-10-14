/* Written by Yuhan YANG, with assistance from
 * Cursor.
 */
package com.github.se.amber.ui.parameters

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.se.amber.data.invitation.InvitationStatus
import com.github.se.amber.data.invitation.InvitationSummary
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests what [CreateInvitationScreen] draws and which actions a click performs.
 *
 * The ViewModel is mocked, so these tests do not load a profile or call Firebase.
 */
@RunWith(RobolectricTestRunner::class)
class CreateInvitationScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

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
        .assertTextContains("Click the left button to create a code")
    composeTestRule
        .onNodeWithTag(CreateInvitationTestTags.USAGE_TEXT)
        .assertTextEquals(
            "This code can only be used once.\nIt's active 30 days after its creation."
        )
    composeTestRule.onNodeWithText("Code | Creation  Time | Status").assertIsDisplayed()
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
                errorMessage = "Sign in to create an invitation",
                isCreating = true,
            ),
        ),
    )
    val createdAtText =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(createdAt))

    composeTestRule.onNodeWithTag(CreateInvitationTestTags.HINT_BOX).assertTextContains("AB12CD34")
    composeTestRule.onNodeWithText("AB12CD34 | $createdAtText | ACTIVE").assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(CreateInvitationTestTags.ERROR_MESSAGE)
        .assertTextEquals("Sign in to create an invitation")
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
    var createClicks = 0
    setScreen(viewModel, onCreateInvitationClick = { createClicks++ })

    composeTestRule.onNodeWithTag(CreateInvitationTestTags.CIRCLE_CHOICES).assertIsDisplayed()
    composeTestRule.onNodeWithText("Arthur's family").assertIsDisplayed()
    composeTestRule.onNodeWithText("Bea's family").assertIsDisplayed()
    composeTestRule.onNodeWithTag("create_invitation_circle_circle-2").performClick()
    composeTestRule.onNodeWithTag(CreateInvitationTestTags.CREATE_CARD).performClick()

    verify(exactly = 1) { viewModel.selectCircle("circle-2") }
    verify(exactly = 1) { viewModel.createInvitation() }
    assertEquals(1, createClicks)
  }

  private fun viewModel(state: CreateInvitationUiState): CreateInvitationViewModel =
      mockk(relaxed = true) { every { uiState } returns MutableStateFlow(state) }

  private fun setScreen(
      viewModel: CreateInvitationViewModel,
      onCreateInvitationClick: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      CreateInvitationScreen(
          viewModel = viewModel,
          onCreateInvitationClick = onCreateInvitationClick,
      )
    }
  }
}
