// Written by Alaabrine, with assistance from Cursor.
package com.github.se.amber.ui.authentication.signOut

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.se.amber.ui.theme.AmberAppTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SignOutButtonTest {
  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var viewModel: SignOutViewModel
  private lateinit var stateFlow: MutableStateFlow<SignOutUIState>

  @Before
  fun setUp() {
    stateFlow = MutableStateFlow(SignOutUIState())
    viewModel = mockk(relaxed = true)
    every { viewModel.uiState } returns stateFlow
  }

  @Test
  fun buttonIsDisplayedWithDefaultLabel() {
    composeTestRule.setContent { AmberAppTheme { SignOutButton(viewModel = viewModel) } }

    composeTestRule
        .onNodeWithTag(SignOutButtonTestTags.BUTTON)
        .assertIsDisplayed()
        .assertTextEquals("Sign out")
        .assertIsEnabled()
  }

  @Test
  fun clickCallsSignOutOnViewModel() {
    composeTestRule.setContent { AmberAppTheme { SignOutButton(viewModel = viewModel) } }

    composeTestRule.onNodeWithTag(SignOutButtonTestTags.BUTTON).performClick()

    verify(exactly = 1) { viewModel.signOut() }
  }

  @Test
  fun customParametersAreApplied() {
    val customTag = "custom_sign_out_button"

    composeTestRule.setContent {
      AmberAppTheme {
        SignOutButton(
            viewModel = viewModel,
            modifier = Modifier.padding(4.dp),
            text = "Log out now",
            buttonHeight = 56.dp,
            buttonWidth = 0.5f,
            testTag = customTag,
        )
      }
    }

    composeTestRule.onNodeWithTag(customTag).assertIsDisplayed().assertTextEquals("Log out now")
  }

  @Test
  fun buttonIsDisabledWhenAlreadySignedOut() {
    stateFlow.value = SignOutUIState(signedOut = true)

    composeTestRule.setContent { AmberAppTheme { SignOutButton(viewModel = viewModel) } }

    composeTestRule.onNodeWithTag(SignOutButtonTestTags.BUTTON).assertIsNotEnabled()
  }

  @Test
  fun previewRendersWithoutCrashing() {
    composeTestRule.setContent { SignOutButtonPreview() }

    composeTestRule.onNodeWithTag(SignOutButtonTestTags.BUTTON).assertIsDisplayed()
  }
}
