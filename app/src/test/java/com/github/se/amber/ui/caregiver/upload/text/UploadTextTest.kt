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
import com.github.se.amber.ui.caregiver.upload.UploadScreenDarkPreview
import com.github.se.amber.ui.caregiver.upload.UploadTestTags
import com.github.se.amber.ui.caregiver.upload.media.AddPersonDialogTestTags
import com.github.se.amber.ui.caregiver.upload.media.CommonMediaUIState
import com.github.se.amber.ui.caregiver.upload.media.DisplayWhoIsInvolvedTestTags
import com.github.se.amber.ui.caregiver.upload.media.MIN_PLACE_QUERY_LENGTH
import com.github.se.amber.ui.caregiver.upload.media.MediaLocationUIState
import com.github.se.amber.ui.caregiver.upload.media.UploadMediaLocationTestTags
import com.github.se.amber.ui.caregiver.upload.pictures.UploadPictureScreenTestTags
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tests the upload-text entry button and that the screen sends edits to [UploadTextViewModel]. */
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
    var wentBack = false
    composeTestRule.setContent { UploadTextScreen(goBack = { wentBack = true }) }

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
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    assertTrue(wentBack)
  }

  @Test
  fun editsAreSentToTheViewModel() {
    val viewModel = viewModel()
    showScreen(viewModel)

    field(UploadTextScreenTestTags.STORY_TITLE).performTextInput("Market day")
    field(UploadTextScreenTestTags.DESCRIPTION).performTextInput("We bought bread.")
    composeTestRule
        .onNode(
            hasSetTextAction() and
                hasAnyAncestor(hasTestTag(UploadMediaLocationTestTags.SEARCH_FIELD)),
            useUnmergedTree = true,
        )
        .performScrollTo()
        .performTextInput("L")
    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.ADD_PERSON)
        .performScrollTo()
        .performClick()

    verify { viewModel.setTitle("Market day") }
    verify { viewModel.setText("We bought bread.") }
    verify { viewModel.setPlaceQuery("L") }
    verify { viewModel.onAddPerson() }
  }

  @Test
  fun readyStoryEnablesUploadWithoutLeavingTheScreen() {
    var leftTheScreen = false
    val viewModel =
        viewModel(
            UploadTextUIState(title = "Market day", text = "We bought bread."),
        )
    showScreen(viewModel, onUpload = { leftTheScreen = true })

    composeTestRule.onNodeWithTag(UploadTextScreenTestTags.UPLOAD_BUTTON).assertIsEnabled()
    composeTestRule.onNodeWithTag(UploadTextScreenTestTags.UPLOAD_BUTTON).performClick()

    verify { viewModel.uploadText() }
    assertFalse(leftTheScreen)
  }

  @Test
  fun addPersonDialogForwardsTheTypedNames() {
    val viewModel =
        viewModel(
            UploadTextUIState(common = CommonMediaUIState(isAddPersonOpen = true)),
        )
    showScreen(viewModel)

    composeTestRule.onNodeWithTag(AddPersonDialogTestTags.SAVE).assertIsNotEnabled()
    namedField(R.string.addPersonFirstName).performTextInput("Ada")
    namedField(R.string.addPersonSurname).performTextInput("Lovelace")
    namedField(R.string.addPersonNickname).performTextInput("A")
    composeTestRule.onNodeWithTag(AddPersonDialogTestTags.CLOSE).performClick()

    verify { viewModel.setPersonFirstName("Ada") }
    verify { viewModel.setPersonLastName("Lovelace") }
    verify { viewModel.setPersonNickname("A") }
    verify { viewModel.dismissAddPerson() }
  }

  @Test
  fun savingAPersonCallsAddPerson() {
    val viewModel =
        viewModel(
            UploadTextUIState(
                common = CommonMediaUIState(isAddPersonOpen = true, personFirstName = "Ada"),
            ),
        )
    showScreen(viewModel)

    composeTestRule.onNodeWithTag(AddPersonDialogTestTags.SAVE).assertIsEnabled().performClick()

    verify { viewModel.addPerson() }
  }

  @Test
  fun aShortPlaceFromTheViewModelShowsTheTooShortHint() {
    val viewModel =
        viewModel(
            UploadTextUIState(
                common = CommonMediaUIState(location = MediaLocationUIState(query = "L")),
            ),
        )
    showScreen(viewModel)

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
  @Config(qualifiers = "fr")
  fun screenTextIsInFrench() {
    composeTestRule.setContent { UploadTextScreen() }

    composeTestRule
        .onNodeWithTag(UploadTextScreenTestTags.TITLE)
        .assertTextEquals(string(R.string.uploadTextTitle))
    composeTestRule.onNodeWithText(string(R.string.uploadTextTitleLabel)).assertIsDisplayed()
    field(UploadTextScreenTestTags.STORY_TITLE).performClick()
    composeTestRule.onNodeWithText(string(R.string.uploadTextTitlePlaceholder)).assertIsDisplayed()
    field(UploadTextScreenTestTags.DESCRIPTION).performScrollTo().performClick()
    composeTestRule
        .onNodeWithText(string(R.string.uploadTextDescriptionPlaceholder))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(UploadTextScreenTestTags.UPLOAD_BUTTON)
        .assertTextEquals(string(R.string.uploadTextUpload))
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
  fun darkPreviewShowsTheUploadMenu() {
    composeTestRule.setContent { UploadScreenDarkPreview() }

    composeTestRule
        .onNodeWithTag(UploadTestTags.TEXT_BUTTON)
        .assertTextEquals(string(R.string.uploadChooseText))
  }

  @Test
  fun darkPreviewShowsTheUploadTextScreen() {
    composeTestRule.setContent { UploadTextScreenDarkPreview() }

    composeTestRule
        .onNodeWithTag(UploadTextScreenTestTags.TITLE)
        .assertTextEquals(string(R.string.uploadTextTitle))
    composeTestRule.onNodeWithTag(UploadTextScreenTestTags.UPLOAD_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun cancelStaysOnTheScreenUntilNavigationRuns() {
    composeTestRule.setContent { UploadTextScreen() }

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(UploadTextScreenTestTags.TITLE)
        .assertTextEquals(string(R.string.uploadTextTitle))
  }

  private fun viewModel(state: UploadTextUIState = UploadTextUIState()): UploadTextViewModel {
    val viewModel = mockk<UploadTextViewModel>(relaxed = true)
    every { viewModel.uiState } returns MutableStateFlow(state)
    every { viewModel.events } returns emptyFlow()
    return viewModel
  }

  private fun showScreen(
      viewModel: UploadTextViewModel = viewModel(),
      onUpload: () -> Unit = {},
      goBack: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      UploadTextScreen(viewModel = viewModel, onUpload = onUpload, goBack = goBack)
    }
  }

  private fun namedField(label: Int) =
      composeTestRule.onNode(hasSetTextAction() and hasText(string(label)))

  private fun field(sectionTag: String) =
      composeTestRule.onNode(
          hasTestTag(DisplayTextFieldTestTags.TEXT) and hasAnyAncestor(hasTestTag(sectionTag)),
          useUnmergedTree = true,
      )
}
