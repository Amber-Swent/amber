/* Written by nehachak1, with assistance from
 * Claude Code
 */
package com.github.se.amber.ui.authentication

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import com.github.se.amber.R
import com.github.se.amber.ui.authentication.signIn.AuthenticationButton
import com.github.se.amber.ui.authentication.signIn.AuthenticationComponentTestTags
import com.github.se.amber.ui.authentication.signIn.AuthenticationComponentsPreview
import com.github.se.amber.ui.authentication.signIn.AuthenticationHeader
import com.github.se.amber.ui.authentication.signIn.AuthenticationLayout
import com.github.se.amber.ui.authentication.signIn.AuthenticationText
import com.github.se.amber.ui.authentication.signIn.AuthenticationTextField
import com.github.se.amber.ui.authentication.signIn.TermsFooter
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_BIG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the components shared by the authentication screens in isolation.
 *
 * Each component is rendered both with its default arguments and with explicit ones, so that the
 * defaults stay usable on their own.
 */
@RunWith(RobolectricTestRunner::class)
class AuthenticationComponentsTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun layoutWithDefaultsShowsHeaderAndFooter() {
    composeTestRule.setContent { AuthenticationLayout() }

    composeTestRule.onNodeWithTag(AuthenticationComponentTestTags.HEADER).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AuthenticationComponentTestTags.TERMS_FOOTER).assertIsDisplayed()
  }

  @Test
  fun layoutShowsScreenContent() {
    composeTestRule.setContent {
      AuthenticationLayout(modifier = Modifier.testTag(ROOT), spacing = CAREGIVER_PADDING_BIG) {
        Text("Screen content", modifier = Modifier.testTag(TEXT))
      }
    }

    composeTestRule.onNodeWithTag(ROOT).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TEXT).assertIsDisplayed()
  }

  @Test
  fun headerIsDisplayed() {
    composeTestRule.setContent { AuthenticationHeader() }

    composeTestRule.onNodeWithTag(AuthenticationComponentTestTags.HEADER).assertIsDisplayed()
  }

  @Test
  fun footerIsDisplayed() {
    composeTestRule.setContent { TermsFooter() }

    composeTestRule.onNodeWithTag(AuthenticationComponentTestTags.TERMS_FOOTER).assertIsDisplayed()
  }

  @Test
  fun textFieldWithDefaultsIsDisplayed() {
    composeTestRule.setContent { AuthenticationTextField() }

    composeTestRule.onNode(hasSetTextAction()).assertIsDisplayed()
  }

  @Test
  fun textFieldShowsPlaceholderAndForwardsInput() {
    var typed = ""
    composeTestRule.setContent {
      AuthenticationTextField(
          modifier = Modifier.testTag(FIELD),
          value = "",
          onValueChange = { typed = it },
          placeholder = "email@domain.com",
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
      )
    }

    composeTestRule.onNodeWithTag(FIELD).assertIsDisplayed()
    composeTestRule.onNodeWithTag(FIELD).performTextInput("person@example.com")

    assertEquals("person@example.com", typed)
  }

  @Test
  fun passwordTransformationMarksFieldAsPassword() {
    composeTestRule.setContent {
      AuthenticationTextField(
          modifier = Modifier.testTag(FIELD),
          value = "secret",
          visualTransformation = PasswordVisualTransformation(),
      )
    }

    composeTestRule
        .onNodeWithTag(FIELD)
        .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
  }

  @Test
  fun buttonWithDefaultsIsEnabled() {
    composeTestRule.setContent { AuthenticationButton() }

    composeTestRule.onNode(hasClickAction()).assertIsEnabled()
  }

  @Test
  fun buttonShowsTextAndForwardsClicks() {
    var clicks = 0
    composeTestRule.setContent {
      AuthenticationButton(
          modifier = Modifier.testTag(BUTTON),
          text = "Continue",
          onClick = { clicks++ },
      )
    }

    composeTestRule.onNodeWithTag(BUTTON).assertTextEquals("Continue").performClick()

    assertEquals(1, clicks)
  }

  @Test
  fun disabledButtonIgnoresClicks() {
    var clicks = 0
    composeTestRule.setContent {
      AuthenticationButton(
          modifier = Modifier.testTag(BUTTON),
          onClick = { clicks++ },
          enabled = false,
      )
    }

    composeTestRule.onNodeWithTag(BUTTON).assertIsNotEnabled().performClick()

    assertEquals(0, clicks)
  }

  @Test
  fun buttonWithLeadingIconStillShowsText() {
    composeTestRule.setContent {
      AuthenticationButton(
          modifier = Modifier.testTag(BUTTON),
          text = "Sign in with Google",
          leadingIcon = painterResource(R.drawable.google_logo),
      )
    }

    composeTestRule.onNodeWithTag(BUTTON).assertTextEquals("Sign in with Google")
  }

  @Test
  fun textWithDefaultsRendersGivenText() {
    composeTestRule.setContent {
      AuthenticationText(modifier = Modifier.testTag(TEXT), text = "Hello")
    }

    composeTestRule.onNodeWithTag(TEXT).assertTextEquals("Hello")
  }

  @Test
  fun textWithExplicitArgumentsRenders() {
    composeTestRule.setContent {
      AuthenticationText(
          modifier = Modifier.testTag(TEXT),
          text = "Hello",
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.primary,
      )
    }

    composeTestRule.onNodeWithTag(TEXT).assertIsDisplayed()
  }

  @Test
  fun previewShowsEveryComponent() {
    composeTestRule.setContent { AuthenticationComponentsPreview() }

    composeTestRule.onNodeWithTag(AuthenticationComponentTestTags.HEADER).assertIsDisplayed()
    composeTestRule.onNodeWithText("Screen heading").assertIsDisplayed()
    composeTestRule.onNodeWithText("Disabled").assertIsNotEnabled()
  }

  private companion object {
    const val ROOT = "test_root"
    const val FIELD = "test_field"
    const val BUTTON = "test_button"
    const val TEXT = "test_text"
  }
}
