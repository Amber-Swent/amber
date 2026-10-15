// this code was written by Alaabrine
// with the aid of AI coding agent
package com.github.se.amber.ui.invitationRedemption

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
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
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RedeemInviteUITests {
  @get:Rule val composeTestRule = createComposeRule()

  private var redeemSuccessCount = 0
  private var backCount = 0
  private var lastRedemptionResult: InvitationRedemptionResult? = null

  private fun setScreen(
      repository: InvitationRedemptionRepository = stubRepository(),
  ): RedeemInviteViewModel {
    redeemSuccessCount = 0
    backCount = 0
    lastRedemptionResult = null
    val viewModel =
        RedeemInviteViewModel(
            invitationRedemptionRepository = repository,
            onSuccess = { result ->
              redeemSuccessCount++
              lastRedemptionResult = result
            },
        )
    composeTestRule.setContent {
      RedeemInvite(
          viewModel = viewModel,
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

  // ---------- Branding ----------

  @Test
  fun logoAndBrandName_areDisplayed() {
    setScreen()

    composeTestRule.onNodeWithTag(RedeemInviteTestTags.LOGO.name).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(RedeemInviteTestTags.BRAND_NAME.name)
        .assertIsDisplayed()
        .assertTextEquals("Amber")
  }

  // ---------- Title ----------

  @Test
  fun title_isDisplayed() {
    setScreen()

    composeTestRule
        .onNodeWithTag(RedeemInviteTestTags.TITLE.name)
        .assertIsDisplayed()
        .assertTextEquals(TITLE_LABEL)
  }

  // ---------- Code input / placeholder ----------

  @Test
  fun codeInput_isDisplayed() {
    setScreen()

    composeTestRule.onNodeWithTag(RedeemInviteTestTags.CODE_INPUT.name).assertIsDisplayed()
  }

  @Test
  fun codePlaceholder_showsWhenInputIsEmpty() {
    setScreen()

    composeTestRule
        .onNodeWithTag(RedeemInviteTestTags.CODE_PLACEHOLDER.name)
        .assertIsDisplayed()
        .assertTextEquals(CODE_PLACEHOLDER_LABEL)
  }

  @Test
  fun codePlaceholder_hiddenAfterTyping() {
    setScreen()

    composeTestRule.onNodeWithTag(RedeemInviteTestTags.CODE_INPUT.name).performTextInput(VALID_CODE)
    composeTestRule.waitForIdle()

    composeTestRule
        .onAllNodesWithTag(RedeemInviteTestTags.CODE_PLACEHOLDER.name)
        .assertCountEquals(0)
  }

  @Test
  fun codeInputAndPlaceholder_areDistinctTags() {
    assertNotEquals(RedeemInviteTestTags.CODE_INPUT, RedeemInviteTestTags.CODE_PLACEHOLDER)

    setScreen()

    composeTestRule.onNodeWithTag(RedeemInviteTestTags.CODE_INPUT.name).assertIsDisplayed()
    composeTestRule.onNodeWithTag(RedeemInviteTestTags.CODE_PLACEHOLDER.name).assertIsDisplayed()
  }

  // ---------- Submit / Back buttons ----------

  @Test
  fun submitAndBackButtons_areBothDisplayed() {
    setScreen()

    composeTestRule.onNodeWithTag(RedeemInviteTestTags.SUBMIT_BUTTON.name).assertIsDisplayed()
    composeTestRule.onNodeWithTag(RedeemInviteTestTags.BACK_BUTTON.name).assertIsDisplayed()
  }

  @Test
  fun submitAndBackButtons_areDistinctAndClickable() {
    setScreen()

    assertNotEquals(RedeemInviteTestTags.SUBMIT_BUTTON, RedeemInviteTestTags.BACK_BUTTON)

    composeTestRule.onNodeWithTag(RedeemInviteTestTags.SUBMIT_BUTTON.name).assertHasClickAction()
    composeTestRule.onNodeWithTag(RedeemInviteTestTags.BACK_BUTTON.name).assertHasClickAction()
  }

  @Test
  fun submitButton_showsSubmitLabel() {
    setScreen()

    composeTestRule
        .onNodeWithTag(RedeemInviteTestTags.SUBMIT_BUTTON.name)
        .assertTextEquals(SUBMIT_LABEL)
  }

  @Test
  fun backButton_showsBackLabel() {
    setScreen()

    composeTestRule
        .onNodeWithTag(RedeemInviteTestTags.BACK_BUTTON.name)
        .assertTextEquals(BACK_LABEL)
  }

  // ---------- Back navigation ----------

  @Test
  fun backButton_click_invokesOnBackOnly() {
    setScreen()

    composeTestRule.onNodeWithTag(RedeemInviteTestTags.BACK_BUTTON.name).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, backCount)
    assertEquals(0, redeemSuccessCount)
    assertNull(lastRedemptionResult)
  }

  @Test
  fun initially_doesNotInvokeNavigationCallbacks() {
    setScreen()
    composeTestRule.waitForIdle()

    assertEquals(0, backCount)
    assertEquals(0, redeemSuccessCount)
    assertNull(lastRedemptionResult)
  }

  // ---------- Validation ----------

  @Test
  fun submit_withEmptyCode_showsInvalidLinkError() {
    setScreen()

    composeTestRule.onAllNodesWithTag(RedeemInviteTestTags.CODE_ERROR.name).assertCountEquals(0)

    composeTestRule.onNodeWithTag(RedeemInviteTestTags.SUBMIT_BUTTON.name).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(RedeemInviteTestTags.CODE_INPUT.name).assert(hasErrorSemantics())
    composeTestRule
        .onNodeWithTag(RedeemInviteTestTags.CODE_ERROR.name)
        .assertIsDisplayed()
        .assertTextEquals(INVALID_LINK_LABEL)
    assertEquals(0, redeemSuccessCount)
  }

  @Test
  fun typingAfterError_clearsInvalidLinkError() {
    val viewModel = setScreen()

    composeTestRule.onNodeWithTag(RedeemInviteTestTags.SUBMIT_BUTTON.name).performClick()
    composeTestRule.waitForIdle()
    assertTrue(viewModel.uiState.value.showError)

    composeTestRule.onNodeWithTag(RedeemInviteTestTags.CODE_INPUT.name).performTextInput(VALID_CODE)
    composeTestRule.waitForIdle()

    composeTestRule.onAllNodesWithTag(RedeemInviteTestTags.CODE_ERROR.name).assertCountEquals(0)
  }

  // ---------- Redeem success / failure ----------

  @Test
  fun submit_withValidCode_invokesOnRedeemSuccess() {
    val expected = InvitationRedemptionResult(careCircleId = "circle-42", role = Role.PATIENT)
    setScreen(repository = stubRepository(Result.success(expected)))

    composeTestRule.onNodeWithTag(RedeemInviteTestTags.CODE_INPUT.name).performTextInput(VALID_CODE)
    composeTestRule.onNodeWithTag(RedeemInviteTestTags.SUBMIT_BUTTON.name).performClick()
    composeTestRule.waitUntil(timeoutMillis = 3_000) { redeemSuccessCount == 1 }

    assertEquals(1, redeemSuccessCount)
    assertEquals(expected, lastRedemptionResult)
    assertEquals(0, backCount)
  }

  @Test
  fun submit_withRejectedCode_showsInvalidLinkError() {
    setScreen(repository = stubRepository(Result.failure(InvitationError.InvalidCode)))

    composeTestRule.onNodeWithTag(RedeemInviteTestTags.CODE_INPUT.name).performTextInput(VALID_CODE)
    composeTestRule.onNodeWithTag(RedeemInviteTestTags.SUBMIT_BUTTON.name).performClick()
    composeTestRule.waitUntil(timeoutMillis = 3_000) {
      composeTestRule
          .onAllNodesWithTag(RedeemInviteTestTags.CODE_ERROR.name)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

    composeTestRule
        .onNodeWithTag(RedeemInviteTestTags.CODE_ERROR.name)
        .assertIsDisplayed()
        .assertTextEquals(INVALID_LINK_LABEL)
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

    composeTestRule.onNodeWithTag(RedeemInviteTestTags.CODE_INPUT.name).performTextInput(VALID_CODE)
    composeTestRule.onNodeWithTag(RedeemInviteTestTags.SUBMIT_BUTTON.name).performClick()
    composeTestRule.waitUntil(timeoutMillis = 3_000) { viewModel.uiState.value.isRedeeming }

    composeTestRule.onNodeWithTag(RedeemInviteTestTags.SUBMIT_BUTTON.name).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(RedeemInviteTestTags.BACK_BUTTON.name).assertIsNotEnabled()

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
