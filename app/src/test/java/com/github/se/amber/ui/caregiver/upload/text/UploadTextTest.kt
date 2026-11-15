// Written by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.caregiver.upload.text

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.ui.caregiver.DisplayTextFieldTestTags
import com.github.se.amber.ui.caregiver.upload.UploadScreen
import com.github.se.amber.ui.caregiver.upload.UploadTestTags
import com.github.se.amber.ui.caregiver.upload.media.AddPersonDialogTestTags
import com.github.se.amber.ui.caregiver.upload.media.DisplayWhoIsInvolvedTestTags
import com.github.se.amber.ui.caregiver.upload.media.MIN_PLACE_QUERY_LENGTH
import com.github.se.amber.ui.caregiver.upload.media.UploadMediaLocationTestTags
import com.github.se.amber.ui.caregiver.upload.pictures.UploadPictureScreenTestTags
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the upload-text entry button and what [UploadTextScreen] draws before a view model exists.
 */
@RunWith(RobolectricTestRunner::class)
class UploadTextScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private fun string(id: Int): String =
      ApplicationProvider.getApplicationContext<Context>().getString(id)

  @Test
  fun textButtonIsShownAndOnlyThatCallbackRuns() {
    var pictureClicked = false
    var audioClicked = false
    var textClicked = false
    composeTestRule.setContent {
      UploadScreen(
          onUploadPictureClick = { pictureClicked = true },
          onUploadAudioClick = { audioClicked = true },
          onUploadTextClick = { textClicked = true },
      )
    }

    composeTestRule
        .onNodeWithTag(UploadTestTags.TEXT_BUTTON)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.uploadChooseText))
        .performClick()

    assertTrue(textClicked)
    assertFalse(pictureClicked)
    assertFalse(audioClicked)
  }

  @Test
  fun screenShowsATitleFieldInsteadOfThePictureButtons() {
    var uploaded = false
    var wentBack = false
    composeTestRule.setContent {
      UploadTextScreen(onUpload = { uploaded = true }, goBack = { wentBack = true })
    }

    composeTestRule
        .onNodeWithTag(UploadTextScreenTestTags.TITLE)
        .assertTextEquals(string(R.string.uploadTextTitle))
    composeTestRule.onNodeWithText(string(R.string.uploadTextTitleLabel)).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(string(R.string.uploadTextDescriptionLabel))
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(UploadPictureScreenTestTags.TAKE_PICTURE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(UploadPictureScreenTestTags.SELECT_PICTURE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(UploadTextScreenTestTags.UPLOAD_BUTTON).assertIsNotEnabled()

    field(UploadTextScreenTestTags.STORY_TITLE).performTextInput("Market day")
    field(UploadTextScreenTestTags.DESCRIPTION).performTextInput("We bought bread.")
    composeTestRule.onNodeWithTag(UploadTextScreenTestTags.UPLOAD_BUTTON).assertIsEnabled()
    composeTestRule.onNodeWithTag(UploadTextScreenTestTags.UPLOAD_BUTTON).performClick()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    assertTrue(uploaded)
    assertTrue(wentBack)
  }

  @Test
  fun titleWithoutADescriptionLeavesUploadDisabled() {
    showScreen()

    field(UploadTextScreenTestTags.STORY_TITLE).performTextInput("Market day")

    composeTestRule.onNodeWithTag(UploadTextScreenTestTags.UPLOAD_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun descriptionWithoutATitleLeavesUploadDisabled() {
    showScreen()

    field(UploadTextScreenTestTags.DESCRIPTION).performTextInput("We bought bread.")

    composeTestRule.onNodeWithTag(UploadTextScreenTestTags.UPLOAD_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun spacesDoNotCountAsATitleOrADescription() {
    showScreen()

    field(UploadTextScreenTestTags.STORY_TITLE).performTextInput("   ")
    field(UploadTextScreenTestTags.DESCRIPTION).performTextInput("   ")

    composeTestRule.onNodeWithTag(UploadTextScreenTestTags.UPLOAD_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun addPersonDialogClosesOnSaveAndDoesNotListThePerson() {
    showScreen()

    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.ADD_PERSON)
        .performScrollTo()
        .performClick()
    composeTestRule.onNodeWithTag(AddPersonDialogTestTags.SAVE).assertIsNotEnabled()

    namedField(R.string.addPersonSurname).performTextInput("Lovelace")
    namedField(R.string.addPersonNickname).performTextInput("Ada")
    composeTestRule.onNodeWithTag(AddPersonDialogTestTags.SAVE).assertIsNotEnabled()
    namedField(R.string.addPersonFirstName).performTextInput("Ada")
    composeTestRule.onNodeWithTag(AddPersonDialogTestTags.SAVE).assertIsEnabled().performClick()

    composeTestRule.onNodeWithTag(AddPersonDialogTestTags.DIALOG).assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.NO_PERSON_INVOLVED)
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun closingAddPersonDialogHidesIt() {
    showScreen()

    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.ADD_PERSON)
        .performScrollTo()
        .performClick()
    composeTestRule.onNodeWithTag(AddPersonDialogTestTags.DIALOG).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AddPersonDialogTestTags.CLOSE).performClick()

    composeTestRule.onNodeWithTag(AddPersonDialogTestTags.DIALOG).assertDoesNotExist()
  }

  @Test
  fun typingAPlaceShowsThatTheSearchIsTooShort() {
    showScreen()

    composeTestRule
        .onNode(
            hasSetTextAction() and
                hasAnyAncestor(hasTestTag(UploadMediaLocationTestTags.SEARCH_FIELD)),
            useUnmergedTree = true,
        )
        .performScrollTo()
        .performTextInput("L")

    composeTestRule
        .onNodeWithTag(UploadMediaLocationTestTags.TOO_SHORT)
        .performScrollTo()
        .assertIsDisplayed()
        .assertTextEquals(
            ApplicationProvider.getApplicationContext<Context>()
                .resources
                .getQuantityString(
                    R.plurals.locationQueryTooShort,
                    MIN_PLACE_QUERY_LENGTH,
                    MIN_PLACE_QUERY_LENGTH,
                ),
        )
  }

  @Test
  fun dateSectionIsShown() {
    showScreen()

    composeTestRule
        .onNodeWithTag(UploadTextScreenTestTags.SELECT_DATE)
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun previewShowsTheUploadTextScreen() {
    composeTestRule.setContent { UploadTextScreenPreview() }

    composeTestRule
        .onNodeWithTag(UploadTextScreenTestTags.TITLE)
        .assertTextEquals(string(R.string.uploadTextTitle))
    composeTestRule.onNodeWithTag(UploadTextScreenTestTags.UPLOAD_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun defaultCallbacksLeaveTheScreenOpen() {
    composeTestRule.setContent { UploadTextScreen() }

    field(UploadTextScreenTestTags.STORY_TITLE).performTextInput("Market day")
    field(UploadTextScreenTestTags.DESCRIPTION).performTextInput("We bought bread.")
    composeTestRule.onNodeWithTag(UploadTextScreenTestTags.UPLOAD_BUTTON).performClick()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    composeTestRule
        .onNodeWithTag(UploadTextScreenTestTags.TITLE)
        .assertTextEquals(string(R.string.uploadTextTitle))
  }

  private fun showScreen(onUpload: () -> Unit = {}, goBack: () -> Unit = {}) {
    composeTestRule.setContent { UploadTextScreen(onUpload = onUpload, goBack = goBack) }
  }

  private fun namedField(label: Int) =
      composeTestRule.onNode(hasSetTextAction() and hasText(string(label)))

  private fun field(sectionTag: String) =
      composeTestRule.onNode(
          hasTestTag(DisplayTextFieldTestTags.TEXT) and hasAnyAncestor(hasTestTag(sectionTag)),
          useUnmergedTree = true,
      )
}
