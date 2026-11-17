// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.authentication

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextStyle
import com.github.se.amber.ui.caregiver.DisplayCaregiverButtonTestTags
import com.github.se.amber.ui.patient.PatientUtilsTestTags
import com.github.se.amber.ui.theme.AmberAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuthenticationUtilsTest {
  @get:Rule val composeTestRule = createComposeRule()

  private var style: TextStyle? = null
  private var expected: TextStyle? = null

  @Test
  fun patientGetsLargeHeadlineStyle() {
    composeTestRule.setContent {
      AmberAppTheme {
        style = authenticationTextStyle(isPatient = true)
        expected = MaterialTheme.typography.headlineSmall
      }
    }

    assertEquals(expected, style)
  }

  @Test
  fun caregiverGetsBodyStyle() {
    composeTestRule.setContent {
      AmberAppTheme {
        style = authenticationTextStyle(isPatient = false)
        expected = MaterialTheme.typography.bodyLarge
      }
    }

    assertEquals(expected, style)
  }

  @Test
  fun defaultIsPatientStyle() {
    composeTestRule.setContent {
      AmberAppTheme {
        style = authenticationTextStyle()
        expected = MaterialTheme.typography.headlineSmall
      }
    }

    assertEquals(expected, style)
  }

  private fun setButton(
      isPatient: Boolean,
      onClick: () -> Unit = {},
      enabled: Boolean = true,
      testTag: String? = TEST_TAG,
  ) {
    composeTestRule.setContent {
      AmberAppTheme {
        DisplayAuthenticationButton(
            isPatient = isPatient,
            text = "text",
            onClick = onClick,
            enabled = enabled,
            testTag = testTag,
        )
      }
    }
  }

  @Test
  fun patientButtonIsDisplayedWithGivenTag() {
    setButton(isPatient = true)

    composeTestRule.onNodeWithTag(TEST_TAG).assertIsDisplayed()
  }

  @Test
  fun caregiverButtonIsDisplayedWithGivenTag() {
    setButton(isPatient = false)

    composeTestRule.onNodeWithTag(TEST_TAG).assertIsDisplayed()
  }

  @Test
  fun patientButtonDisplaysGivenText() {
    setButton(isPatient = true)

    composeTestRule.onNodeWithTag(TEST_TAG).assertTextEquals("text")
  }

  @Test
  fun caregiverButtonDisplaysGivenText() {
    setButton(isPatient = false)

    composeTestRule.onNodeWithTag(TEST_TAG).assertTextEquals("text")
  }

  @Test
  fun patientButtonKeepsDefaultTagWhenNoneGiven() {
    setButton(isPatient = true, testTag = null)

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertIsDisplayed()
  }

  @Test
  fun caregiverButtonKeepsDefaultTagWhenNoneGiven() {
    setButton(isPatient = false, testTag = null)

    composeTestRule.onNodeWithTag(DisplayCaregiverButtonTestTags.BUTTON).assertIsDisplayed()
  }

  @Test
  fun patientButtonClickInvokesCallback() {
    var clicks = 0
    setButton(isPatient = true, onClick = { clicks++ })

    composeTestRule.onNodeWithTag(TEST_TAG).performClick()

    assertEquals(1, clicks)
  }

  @Test
  fun caregiverButtonClickInvokesCallback() {
    var clicks = 0
    setButton(isPatient = false, onClick = { clicks++ })

    composeTestRule.onNodeWithTag(TEST_TAG).performClick()

    assertEquals(1, clicks)
  }

  @Test
  fun patientButtonIsEnabledByDefault() {
    setButton(isPatient = true)

    composeTestRule.onNodeWithTag(TEST_TAG).assertIsEnabled()
  }

  @Test
  fun patientButtonIsDisabledWhenNotEnabled() {
    setButton(isPatient = true, enabled = false)

    composeTestRule.onNodeWithTag(TEST_TAG).assertIsNotEnabled()
  }

  @Test
  fun caregiverButtonIsDisabledWhenNotEnabled() {
    setButton(isPatient = false, enabled = false)

    composeTestRule.onNodeWithTag(TEST_TAG).assertIsNotEnabled()
  }

  private companion object {
    const val TEST_TAG = "authentication_button_test_tag"
  }
}
