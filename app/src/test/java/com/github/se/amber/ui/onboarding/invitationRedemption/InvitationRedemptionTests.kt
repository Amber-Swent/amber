// this code was written by Alaabrine
// with the aid of AI coding agent
// Modified by Yuhan YANG, with assistance from Cursor.
package com.github.se.amber.ui.invitationRedemption

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.se.amber.data.invitation.InvitationError
import com.github.se.amber.data.invitation.InvitationRedemptionRepository
import com.github.se.amber.data.invitation.InvitationRedemptionResult
import com.github.se.amber.model.user.Role
import com.github.se.amber.ui.onboarding.invitationRedemption.CODE_REQUIRED_MESSAGE
import com.github.se.amber.ui.onboarding.invitationRedemption.InvitationRedemptionScreen
import com.github.se.amber.ui.onboarding.invitationRedemption.InvitationRedemptionTestTags
import com.github.se.amber.ui.onboarding.invitationRedemption.InvitationRedemptionViewModel
import com.github.se.amber.ui.onboarding.invitationRedemption.toActionableMessage
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InvitationRedemptionUITests {
  @get:Rule val composeTestRule = createComposeRule()

  private var redeemSuccessCount = 0
  private var backCount = 0
  private var lastRedemptionResult: InvitationRedemptionResult? = null

  private fun setScreen(
      repository: InvitationRedemptionRepository = stubRepository(),
  ): InvitationRedemptionViewModel {
    redeemSuccessCount = 0
    backCount = 0
    lastRedemptionResult = null
    val viewModel =
        InvitationRedemptionViewModel(
            invitationRedemptionRepository = repository,
        )
    composeTestRule.setContent {
      InvitationRedemptionScreen(
          viewModel = viewModel,
          onRedeemSuccess = { result ->
            redeemSuccessCount++
            lastRedemptionResult = result
          },
          onBack = { backCount++ },
      )
    }
    return viewModel
  }

  private fun stubRepository(
      result: Result<InvitationRedemptionResult> =
          Result.success(
              InvitationRedemptionResult(careCircleId = "circle-1", role = Role.CAREGIVER),
          ),
  ): InvitationRedemptionRepository =
      mockk<InvitationRedemptionRepository>().also {
        coEvery { it.redeemInvitation(any()) } returns result
      }

  private fun hasErrorSemantics(): SemanticsMatcher =
      SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)

  // ---------- Placeholder behaviour ----------

  @Test
  fun codePlaceholder_hiddenAfterTyping() {
    setScreen()

    composeTestRule
        .onNodeWithTag(InvitationRedemptionTestTags.CODE_INPUT)
        .performTextInput(VALID_CODE)
    composeTestRule.waitForIdle()

    composeTestRule
        .onAllNodesWithTag(
            InvitationRedemptionTestTags.CODE_PLACEHOLDER,
            useUnmergedTree = true,
        )
        .assertCountEquals(0)
  }

  // ---------- Back navigation ----------

  @Test
  fun backButton_click_invokesOnBackOnly() {
    setScreen()

    composeTestRule.onNodeWithTag(InvitationRedemptionTestTags.BACK_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, backCount)
    assertEquals(0, redeemSuccessCount)
    assertNull(lastRedemptionResult)
  }

  // ---------- Validation ----------

  @Test
  fun submit_withEmptyCode_showsCodeRequiredError() {
    setScreen()

    composeTestRule
        .onAllNodesWithTag(InvitationRedemptionTestTags.CODE_ERROR, useUnmergedTree = true)
        .assertCountEquals(0)

    composeTestRule.onNodeWithTag(InvitationRedemptionTestTags.SUBMIT_BUTTON).performClick()
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(InvitationRedemptionTestTags.CODE_INPUT)
        .assert(hasErrorSemantics())
    composeTestRule
        .onNodeWithTag(InvitationRedemptionTestTags.CODE_ERROR, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals(CODE_REQUIRED_MESSAGE)
    assertEquals(0, redeemSuccessCount)
  }

  @Test
  fun typingAfterError_clearsCodeRequiredError() {
    val viewModel = setScreen()

    composeTestRule.onNodeWithTag(InvitationRedemptionTestTags.SUBMIT_BUTTON).performClick()
    composeTestRule.waitForIdle()
    assertEquals(CODE_REQUIRED_MESSAGE, viewModel.uiState.value.errorMessage)

    composeTestRule
        .onNodeWithTag(InvitationRedemptionTestTags.CODE_INPUT)
        .performTextInput(VALID_CODE)
    composeTestRule.waitForIdle()

    composeTestRule
        .onAllNodesWithTag(InvitationRedemptionTestTags.CODE_ERROR, useUnmergedTree = true)
        .assertCountEquals(0)
  }

  // ---------- Redeem success / failure ----------

  @Test
  fun submit_withValidCode_invokesOnRedeemSuccess() {
    val expected = InvitationRedemptionResult(careCircleId = "circle-42", role = Role.PATIENT)
    setScreen(repository = stubRepository(Result.success(expected)))

    composeTestRule
        .onNodeWithTag(InvitationRedemptionTestTags.CODE_INPUT)
        .performTextInput(VALID_CODE)
    composeTestRule.onNodeWithTag(InvitationRedemptionTestTags.SUBMIT_BUTTON).performClick()
    composeTestRule.waitUntil(timeoutMillis = 3_000) { redeemSuccessCount == 1 }

    assertEquals(1, redeemSuccessCount)
    assertEquals(expected, lastRedemptionResult)
    assertEquals(0, backCount)
  }

  @Test
  fun submit_withRejectedCode_showsMappedInvalidCodeError() {
    setScreen(repository = stubRepository(Result.failure(InvitationError.InvalidCode)))

    composeTestRule
        .onNodeWithTag(InvitationRedemptionTestTags.CODE_INPUT)
        .performTextInput(VALID_CODE)
    composeTestRule.onNodeWithTag(InvitationRedemptionTestTags.SUBMIT_BUTTON).performClick()
    composeTestRule.waitUntil(timeoutMillis = 3_000) {
      composeTestRule
          .onAllNodesWithTag(InvitationRedemptionTestTags.CODE_ERROR, useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

    composeTestRule
        .onNodeWithTag(InvitationRedemptionTestTags.CODE_ERROR, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals(InvitationError.InvalidCode.toActionableMessage())
    assertEquals(0, redeemSuccessCount)
  }

  @Test
  fun whileRedeeming_submitAndBackButtonsAreDisabled() {
    val pending = CompletableDeferred<Result<InvitationRedemptionResult>>()
    val repository =
        mockk<InvitationRedemptionRepository>().also {
          coEvery { it.redeemInvitation(any()) } coAnswers { pending.await() }
        }
    val viewModel = setScreen(repository = repository)

    composeTestRule
        .onNodeWithTag(InvitationRedemptionTestTags.CODE_INPUT)
        .performTextInput(VALID_CODE)
    composeTestRule.onNodeWithTag(InvitationRedemptionTestTags.SUBMIT_BUTTON).performClick()
    composeTestRule.waitUntil(timeoutMillis = 3_000) { viewModel.uiState.value.isRedeeming }

    composeTestRule.onNodeWithTag(InvitationRedemptionTestTags.SUBMIT_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(InvitationRedemptionTestTags.BACK_BUTTON).assertIsNotEnabled()

    pending.complete(
        Result.success(
            InvitationRedemptionResult(careCircleId = "circle-1", role = Role.CAREGIVER)
        ),
    )
    composeTestRule.waitUntil(timeoutMillis = 3_000) { redeemSuccessCount == 1 }
  }

  private companion object {
    const val VALID_CODE = "invite-abc-123"
  }
}
