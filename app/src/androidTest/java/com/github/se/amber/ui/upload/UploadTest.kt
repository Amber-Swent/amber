package com.github.se.amber.ui.upload

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.amber.ui.caregiver.upload.DisplayButton
import com.github.se.amber.ui.caregiver.upload.Upload
import com.github.se.amber.ui.caregiver.upload.UploadTestTags
import com.github.se.amber.ui.caregiver.upload.UploadViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UploadTest {

  @get:Rule val composeTestRule = createComposeRule()

  /** Fake callbacks: record which upload callbacks were invoked, in order. */
  private class FakeUploadActions {
    val calls = mutableListOf<String>()

    fun onUploadTextClick() {
      calls.add("text")
    }

    fun onUploadPictureClick() {
      calls.add("picture")
    }

    fun onUploadAudioClick() {
      calls.add("audio")
    }

    fun checkout() {
      calls.add("checkout")
    }
  }

  // ---------- Setup helpers ----------
  private fun setUpload(
      viewModel: UploadViewModel = UploadViewModel(),
      actions: FakeUploadActions = FakeUploadActions(),
  ) {
    composeTestRule.setContent {
      Upload(
          viewModel = viewModel,
          onUploadTextClick = actions::onUploadTextClick,
          onUploadPictureClick = actions::onUploadPictureClick,
          onUploadAudioClick = actions::onUploadAudioClick,
          checkout = actions::checkout,
      )
    }
  }

  private fun setUploadWithDefaultCallbacks() {
    val viewModel = UploadViewModel()
    composeTestRule.setContent { Upload(viewModel = viewModel) }
  }

  private fun setDisplayButton(onClick: () -> Unit = {}) {
    composeTestRule.setContent {
      DisplayButton(
          icon = Icons.Outlined.Description,
          text = SAMPLE_TEXT,
          description = SAMPLE_DESCRIPTION,
          onClick = onClick,
          modifier = Modifier.testTag(SAMPLE_BUTTON_TAG),
      )
    }
  }

  // ---------- Node / check helpers ----------
  private fun node(tag: String): SemanticsNodeInteraction = composeTestRule.onNodeWithTag(tag)

  private fun checkIsDisplayed(tag: String) {
    node(tag).assertIsDisplayed()
  }

  // ---------- Upload: display ----------
  @Test
  fun titleIsDisplayed() {
    setUpload()
    checkIsDisplayed(UploadTestTags.TITLE)
  }

  @Test
  fun textButtonIsDisplayed() {
    setUpload()
    checkIsDisplayed(UploadTestTags.TEXT_BUTTON)
  }

  @Test
  fun pictureButtonIsDisplayed() {
    setUpload()
    checkIsDisplayed(UploadTestTags.PICTURE_BUTTON)
  }

  @Test
  fun audioButtonIsDisplayed() {
    setUpload()
    checkIsDisplayed(UploadTestTags.AUDIO_BUTTON)
  }

  // TODO: unExtract once NavigationMenuTest passes
  //    @Test
  //    fun topBarIsDisplayed() {
  //        setUpload()
  //        checkIsDisplayed(TopNavigationMenuTestTags.ROOT)
  //    }

  //    @Test
  //    fun topBarShowsCheckoutAction() {
  //        setUpload()
  //        node(TopNavigationMenuTestTags.TITLE).assertTextEquals("Checkout")
  //    }

  // ---------- Upload: button labels ----------
  @Test
  fun buttonShowsLabel() {
    setUpload()
    node(UploadTestTags.TEXT_BUTTON).assertTextContains("Upload Text")
    node(UploadTestTags.PICTURE_BUTTON).assertTextContains("Upload Picture")
    node(UploadTestTags.AUDIO_BUTTON).assertTextContains("Upload Audio")
  }

  // ---------- Upload: accessibility ----------
  @Test
  fun buttonHaveClickAction() {
    setUpload()
    node(UploadTestTags.TEXT_BUTTON).assertHasClickAction()
    node(UploadTestTags.PICTURE_BUTTON).assertHasClickAction()
    node(UploadTestTags.AUDIO_BUTTON).assertHasClickAction()
  }

  @Test
  fun buttonIconHaveTextDescription() {
    setUpload()
    node(UploadTestTags.TEXT_BUTTON).assertContentDescriptionEquals("Text")
    node(UploadTestTags.PICTURE_BUTTON).assertContentDescriptionEquals("Picture")
    node(UploadTestTags.AUDIO_BUTTON).assertContentDescriptionEquals("Audio")
  }

  // ---------- Upload: callbacks ----------

  @Test
  fun noCallbackWithoutInteraction() {
    val actions = FakeUploadActions()
    setUpload(actions = actions)
    assertTrue(actions.calls.isEmpty())
  }

  @Test
  fun clickingTextButtonInvokesOnlyTextCallback() {
    val actions = FakeUploadActions()
    setUpload(actions = actions)
    node(UploadTestTags.TEXT_BUTTON).performClick()
    assertEquals(listOf("text"), actions.calls)
  }

  @Test
  fun clickingPictureButtonInvokesOnlyPictureCallback() {
    val actions = FakeUploadActions()
    setUpload(actions = actions)
    node(UploadTestTags.PICTURE_BUTTON).performClick()
    assertEquals(listOf("picture"), actions.calls)
  }

  @Test
  fun clickingAudioButtonInvokesOnlyAudioCallback() {
    val actions = FakeUploadActions()
    setUpload(actions = actions)
    node(UploadTestTags.AUDIO_BUTTON).performClick()
    assertEquals(listOf("audio"), actions.calls)
  }

  // TODO: unExtract once NavigationMenuTest passes
  //    @Test
  //    fun clickingTopBarBackInvokesOnlyCheckoutCallback() {
  //        val actions = FakeUploadActions()
  //        setUpload(actions = actions)
  //        node(TopNavigationMenuTestTags.BACK_BUTTON).performClick()
  //        assertEquals(listOf("checkout"), actions.calls)
  //    }

  // ---------- Upload: error handling ----------
  @Test
  fun errorMessageIsClearedOnceHandledByTheScreen() {
    val viewModel = UploadViewModel()
    setUpload(viewModel = viewModel)
    assertNull(viewModel.uiState.value.errorMsg)
    composeTestRule.runOnIdle { viewModel.setErrorMsg("Something went wrong") }
    composeTestRule.waitForIdle()
    assertNull(viewModel.uiState.value.errorMsg)
  }

  // ---------- DisplayButton Function----------
  @Test
  fun displayButtonIsDisplayed() {
    setDisplayButton()
    checkIsDisplayed(SAMPLE_BUTTON_TAG)
  }

  @Test
  fun displayButtonShowsGivenText() {
    setDisplayButton()
    node(SAMPLE_BUTTON_TAG).assertTextContains(SAMPLE_TEXT)
  }

  @Test
  fun displayButtonIconUsesGivenDescription() {
    setDisplayButton()
    node(SAMPLE_BUTTON_TAG).assertContentDescriptionEquals(SAMPLE_DESCRIPTION)
  }

  @Test
  fun displayButtonHasClickAction() {
    setDisplayButton()
    node(SAMPLE_BUTTON_TAG).assertHasClickAction()
  }

  @Test
  fun clickingDisplayButtonInvokesOnClickOnce() {
    var clickCount = 0
    setDisplayButton(onClick = { clickCount++ })
    node(SAMPLE_BUTTON_TAG).performClick()
    assertEquals(1, clickCount)
  }

  private companion object {
    const val SAMPLE_BUTTON_TAG = "sample_display_button"
    const val SAMPLE_TEXT = "Sample label"
    const val SAMPLE_DESCRIPTION = "Sample description"
  }
}
