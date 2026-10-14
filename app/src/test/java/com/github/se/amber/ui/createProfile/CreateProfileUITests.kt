// this code was written by Alaabrine
// with the aid of AI coding agent
package com.github.se.amber.ui.createProfile

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.ui.accountCreation.CreateProfile
import com.github.se.amber.ui.accountCreation.CreateProfilePlaceholderColor
import com.github.se.amber.ui.accountCreation.CreateProfileTestTags
import com.github.se.amber.ui.accountCreation.CreateProfileViewModel
import com.github.se.amber.ui.theme.darkGray
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CreateProfileUITests {
  @get:Rule val composeTestRule = createComposeRule()

  private fun setCreateProfile(
      viewModel: CreateProfileViewModel =
          CreateProfileViewModel(profileRepository = stubRepository()),
  ) {
    composeTestRule.setContent { CreateProfile(viewModel = viewModel) }
  }

  private fun stubRepository(): ProfileRepository =
      mockk<ProfileRepository>().also {
        coEvery { it.createProfile(any(), any()) } returns Result.success(Unit)
      }

  private fun hasErrorSemantics(): SemanticsMatcher =
      SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)

  // ---------- Role buttons ----------

  @Test
  fun patientAndCaregiverButtons_areBothDisplayed() {
    setCreateProfile()

    composeTestRule.onNodeWithTag(CreateProfileTestTags.PATIENT_BUTTON.name).assertIsDisplayed()
    composeTestRule.onNodeWithTag(CreateProfileTestTags.CAREGIVER_BUTTON.name).assertIsDisplayed()
  }

  @Test
  fun patientAndCaregiverButtons_areDistinctAndClickable() {
    setCreateProfile()

    assertNotEquals(
        CreateProfileTestTags.PATIENT_BUTTON,
        CreateProfileTestTags.CAREGIVER_BUTTON,
    )

    composeTestRule.onNodeWithTag(CreateProfileTestTags.PATIENT_BUTTON.name).assertHasClickAction()
    composeTestRule
        .onNodeWithTag(CreateProfileTestTags.CAREGIVER_BUTTON.name)
        .assertHasClickAction()
  }

  @Test
  fun patientButton_showsPatientLabel() {
    setCreateProfile()

    composeTestRule
        .onNodeWithTag(CreateProfileTestTags.PATIENT_BUTTON.name)
        .assertTextEquals("Patient")
  }

  @Test
  fun caregiverButton_showsCaregiverLabel() {
    setCreateProfile()

    composeTestRule
        .onNodeWithTag(CreateProfileTestTags.CAREGIVER_BUTTON.name)
        .assertTextEquals("Caregiver")
  }

  // ---------- Name / Surname inputs & placeholders ----------

  @Test
  fun nameAndSurnameInputs_areBothDisplayed() {
    setCreateProfile()

    composeTestRule.onNodeWithTag(CreateProfileTestTags.NAME_INPUT.name).assertIsDisplayed()
    composeTestRule.onNodeWithTag(CreateProfileTestTags.SURNAME_INPUT.name).assertIsDisplayed()
  }

  @Test
  fun nameAndSurnameInputs_areDistinct() {
    assertNotEquals(CreateProfileTestTags.NAME_INPUT, CreateProfileTestTags.SURNAME_INPUT)

    setCreateProfile()

    composeTestRule.onNodeWithTag(CreateProfileTestTags.NAME_INPUT.name).assertIsDisplayed()
    composeTestRule.onNodeWithTag(CreateProfileTestTags.SURNAME_INPUT.name).assertIsDisplayed()
  }

  @Test
  fun namePlaceholder_showsGreyNameTextWhenEmpty() {
    setCreateProfile()

    assertEquals(darkGray, CreateProfilePlaceholderColor)
    assertPlaceholderIsGrey(CreateProfilePlaceholderColor)

    composeTestRule
        .onNodeWithTag(CreateProfileTestTags.NAME_PLACEHOLDER.name)
        .assertIsDisplayed()
        .assertTextEquals("Name")
  }

  @Test
  fun surnamePlaceholder_showsGreySurnameTextWhenEmpty() {
    setCreateProfile()

    assertEquals(darkGray, CreateProfilePlaceholderColor)
    assertPlaceholderIsGrey(CreateProfilePlaceholderColor)

    composeTestRule
        .onNodeWithTag(CreateProfileTestTags.SURNAME_PLACEHOLDER.name)
        .assertIsDisplayed()
        .assertTextEquals("Surname")
  }

  // ---------- Validation when role is chosen with empty fields ----------

  @Test
  fun pressingPatient_withEmptyFields_highlightsInputsInRedAndAsksToFillOut() {
    setCreateProfile()

    composeTestRule.onNodeWithTag(CreateProfileTestTags.PATIENT_BUTTON.name).performClick()
    composeTestRule.waitForIdle()

    assertEmptyFieldValidationIsShown()
  }

  @Test
  fun pressingCaregiver_withEmptyFields_highlightsInputsInRedAndAsksToFillOut() {
    setCreateProfile()

    composeTestRule.onNodeWithTag(CreateProfileTestTags.CAREGIVER_BUTTON.name).performClick()
    composeTestRule.waitForIdle()

    assertEmptyFieldValidationIsShown()
  }

  private fun assertEmptyFieldValidationIsShown() {
    composeTestRule.onNodeWithTag(CreateProfileTestTags.NAME_INPUT.name).assert(hasErrorSemantics())
    composeTestRule
        .onNodeWithTag(CreateProfileTestTags.SURNAME_INPUT.name)
        .assert(hasErrorSemantics())

    composeTestRule
        .onNodeWithTag(CreateProfileTestTags.NAME_ERROR.name)
        .assertIsDisplayed()
        .assertTextEquals(FILL_OUT_MESSAGE)
    composeTestRule
        .onNodeWithTag(CreateProfileTestTags.SURNAME_ERROR.name)
        .assertIsDisplayed()
        .assertTextEquals(FILL_OUT_MESSAGE)
  }

  private fun assertPlaceholderIsGrey(color: Color) {
    // Placeholder must be a muted grey, not black/white or the error red.
    assertEquals(darkGray, color)
    assertTrue(
        "Placeholder color should be grey (equal RGB channels), was $color",
        color.red == color.green && color.green == color.blue,
    )
    assertTrue(
        "Placeholder grey should be mid-tone, was $color",
        color.red < 1f && color.red > 0f,
    )
  }

  private companion object {
    const val FILL_OUT_MESSAGE = "Please fill this out"
  }
}
