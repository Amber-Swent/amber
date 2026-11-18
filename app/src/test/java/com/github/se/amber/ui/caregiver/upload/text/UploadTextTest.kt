// Written by Yuhan YANG, with assistance from Cursor.
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.caregiver.upload.text

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
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
import com.github.se.amber.ui.caregiver.upload.media.CommonMediaUIState
import com.github.se.amber.ui.caregiver.upload.media.MediaEvent
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

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
  fun screenShowsTheTitleAndDescriptionAndGoesBack() {
    var wentBack = false
    composeTestRule.setContent {
      UploadTextScreen(viewModel = viewModel(), goBack = { wentBack = true })
    }

    composeTestRule
        .onNodeWithTag(UploadTextTestTags.TITLE)
        .assertTextEquals(string(R.string.uploadTextTitle))
    composeTestRule.onNodeWithText(string(R.string.uploadTextTitleLabel)).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(string(R.string.uploadTextDescriptionLabel))
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(UploadTextTestTags.UPLOAD_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    assertTrue(wentBack)
  }

  @Test
  fun titleAndBodyAreSentToTheViewModel() {
    val viewModel = viewModel()
    showScreen(viewModel)

    field(UploadTextTestTags.STORY_TITLE).performTextInput("Market day")
    field(UploadTextTestTags.DESCRIPTION).performTextInput("We bought bread.")

    verify { viewModel.setTitle(any()) }
    verify { viewModel.setText(any()) }
  }

  @Test
  fun readyStoryEnablesUploadAndCallsUploadMedia() {
    var leftTheScreen = false
    val viewModel =
        viewModel(
            UploadTextUIState(title = "Market day", text = "We bought bread."),
        )
    showScreen(viewModel, onUpload = { leftTheScreen = true })

    composeTestRule.onNodeWithTag(UploadTextTestTags.UPLOAD_BUTTON).assertIsEnabled()
    composeTestRule.onNodeWithTag(UploadTextTestTags.UPLOAD_BUTTON).performClick()

    verify { viewModel.uploadMedia() }
    assertFalse(leftTheScreen)
  }

  @Test
  fun dateSectionIsShown() {
    showScreen()

    composeTestRule
        .onNodeWithTag(UploadTextTestTags.SELECTED_DATE)
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "fr")
  fun screenTextIsInFrench() {
    composeTestRule.setContent { UploadTextScreen(viewModel = viewModel()) }

    composeTestRule
        .onNodeWithTag(UploadTextTestTags.TITLE)
        .assertTextEquals(string(R.string.uploadTextTitle))
    composeTestRule.onNodeWithText(string(R.string.uploadTextTitleLabel)).assertIsDisplayed()
    field(UploadTextTestTags.STORY_TITLE).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText(string(R.string.uploadTextTitlePlaceholder)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(UploadTextTestTags.DESCRIPTION).performScrollTo()
    field(UploadTextTestTags.DESCRIPTION).performClick()
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithText(string(R.string.uploadTextDescriptionPlaceholder))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(UploadTextTestTags.UPLOAD_BUTTON)
        .assertTextEquals(string(R.string.uploadTextUpload))
  }

  @Test
  @Config(qualifiers = "fr")
  fun anErrorToastUsesTheFrenchMessageAndAsksToClearIt() {
    ShadowToast.reset()
    val viewModel =
        viewModel(
            UploadTextUIState(
                common = CommonMediaUIState(errorMsgRes = R.string.uploadTextTitleEmpty)
            )
        )

    showScreen(viewModel)

    composeTestRule.waitForIdle()
    assertEquals(string(R.string.uploadTextTitleEmpty), ShadowToast.getTextOfLatestToast())
    composeTestRule.waitForIdle()
    verify(exactly = 1) { viewModel.clearErrorMsg() }
  }

  @Test
  fun uploadedEventLeavesTheScreen() {
    var leftTheScreen = false
    val viewModel = viewModel()
    every { viewModel.events } returns flowOf(MediaEvent.Uploaded)

    showScreen(viewModel, onUpload = { leftTheScreen = true })

    composeTestRule.waitForIdle()
    assertTrue(leftTheScreen)
  }

  @Test
  fun previewShowsTheUploadTextScreen() {
    composeTestRule.setContent { UploadTextScreenPreview(viewModel()) }

    composeTestRule
        .onNodeWithTag(UploadTextTestTags.TITLE)
        .assertTextEquals(string(R.string.uploadTextTitle))
    composeTestRule.onNodeWithTag(UploadTextTestTags.UPLOAD_BUTTON).assertIsNotEnabled()
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
    composeTestRule.setContent { UploadTextScreenDarkPreview(viewModel()) }

    composeTestRule
        .onNodeWithTag(UploadTextTestTags.TITLE)
        .assertTextEquals(string(R.string.uploadTextTitle))
    composeTestRule.onNodeWithTag(UploadTextTestTags.UPLOAD_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun cancelStaysOnTheScreenUntilNavigationRuns() {
    composeTestRule.setContent { UploadTextScreen(viewModel = viewModel()) }

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(UploadTextTestTags.TITLE)
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

  private fun field(sectionTag: String) =
      composeTestRule.onNode(
          hasTestTag(DisplayTextFieldTestTags.TEXT) and hasAnyAncestor(hasTestTag(sectionTag)),
          useUnmergedTree = true,
      )
}
