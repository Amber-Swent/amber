/* Written by Yuhan YANG, with assistance from
 * Cursor.
 */
package com.github.se.amber.ui.parameters

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tests the parameters home buttons, hint, and checkout action. */
@RunWith(RobolectricTestRunner::class)
class ParametersHomeScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun showsCreateEnterAndTheInvitationHint() {
    setScreen()

    composeTestRule
        .onNodeWithTag(ParametersHomeTestTags.CREATE_INVITATION_BUTTON)
        .assertIsDisplayed()
        .assertTextContains("Create Invitation")
    composeTestRule
        .onNodeWithTag(ParametersHomeTestTags.ENTER_INVITATION_BUTTON)
        .assertIsDisplayed()
        .assertTextContains("Enter Invitation")
    composeTestRule
        .onNodeWithTag(ParametersHomeTestTags.INVITATION_HINT)
        .assertTextEquals(
            "Invitation code is needed to add families/patient into the family circle",
        )
  }

  @Test
  fun createAndEnterClicksInvokeTheMatchingCallback() {
    var createClicks = 0
    var enterClicks = 0
    setScreen(
        onCreateInvitationClick = { createClicks++ },
        onEnterInvitationClick = { enterClicks++ },
    )

    composeTestRule.onNodeWithTag(ParametersHomeTestTags.CREATE_INVITATION_BUTTON).performClick()
    assertEquals(1, createClicks)
    assertEquals(0, enterClicks)

    composeTestRule.onNodeWithTag(ParametersHomeTestTags.ENTER_INVITATION_BUTTON).performClick()
    assertEquals(1, createClicks)
    assertEquals(1, enterClicks)
  }

  @Test
  fun checkoutInvokesCheckout() {
    var checkoutClicks = 0
    setScreen(checkout = { checkoutClicks++ })

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    assertEquals(1, checkoutClicks)
  }

  private fun setScreen(
      onCreateInvitationClick: () -> Unit = {},
      onEnterInvitationClick: () -> Unit = {},
      checkout: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      ParametersHomeScreen(
          onCreateInvitationClick = onCreateInvitationClick,
          onEnterInvitationClick = onEnterInvitationClick,
          checkout = checkout,
      )
    }
  }
}
