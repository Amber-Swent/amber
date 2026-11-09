// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.caregiver.upload

import android.content.Context
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.ui.caregiver.upload.media.AddPersonDialogTestTags
import com.github.se.amber.ui.caregiver.upload.media.CommonMediaUIState
import com.github.se.amber.ui.caregiver.upload.media.DateField
import com.github.se.amber.ui.caregiver.upload.media.DatePickerTestTags
import com.github.se.amber.ui.caregiver.upload.media.DisplayScrollableDatePicker
import com.github.se.amber.ui.caregiver.upload.media.DisplayWhoIsInvolved
import com.github.se.amber.ui.caregiver.upload.media.DisplayWhoIsInvolvedTestTags
import com.github.se.amber.ui.caregiver.upload.media.MediaViewModelErrorMessage
import com.github.se.amber.ui.caregiver.upload.media.unknownMemberLabel
import com.github.se.amber.ui.caregiver.upload.pictures.UploadPictureScreen
import com.github.se.amber.ui.caregiver.upload.pictures.UploadPictureScreenTestTags
import com.github.se.amber.ui.caregiver.upload.pictures.UploadPictureUIState
import com.github.se.amber.ui.caregiver.upload.pictures.UploadPictureViewModel
import com.github.se.amber.ui.caregiver.upload.pictures.UploadPictureViewModelErrorMessage
import com.github.se.amber.ui.utils.UiText
import com.github.se.amber.ui.utils.asString
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * Checks that the caregiver upload screens show their text, their errors and their toasts in
 * French. The ViewModels are mocked, so no repository is called.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class UploadTranslationTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  /** Shows the picture upload screen with [state] and the people [options]. */
  private fun setPictureScreen(
      state: UploadPictureUIState = UploadPictureUIState(),
      options: Map<String, UiText> = emptyMap(),
  ) {
    val viewModel = mockk<UploadPictureViewModel>(relaxed = true)
    every { viewModel.uiState } returns MutableStateFlow(state)
    every { viewModel.careCircle } returns MutableStateFlow(null)
    every { viewModel.involvedOptions } returns MutableStateFlow(options)
    every { viewModel.placeOptions } returns MutableStateFlow(emptyMap())
    composeTestRule.setContent { UploadPictureScreen(viewModel = viewModel) }
    composeTestRule.waitForIdle()
  }

  @Test
  fun uploadMenuAndItsToastAreInFrench() {
    val viewModel = UploadViewModel()
    composeTestRule.setContent { UploadScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithTag(UploadTestTags.TITLE).assertTextEquals("Ajouter un média")
    composeTestRule.onNodeWithText("Ajouter une photo").assertIsDisplayed()
    composeTestRule.onNodeWithText("Ajouter un audio").assertIsDisplayed()

    composeTestRule.runOnIdle { viewModel.setErrorMsg(R.string.uploadMediaErrorUploadFailed) }
    composeTestRule.waitForIdle()
    assertEquals("L'envoi a échoué, réessayez", ShadowToast.getTextOfLatestToast())
  }

  @Test
  fun pictureScreenIsInFrench() {
    setPictureScreen()

    composeTestRule
        .onNodeWithTag(UploadPictureScreenTestTags.TITLE)
        .assertTextEquals("Ajouter une photo")
    composeTestRule.onNodeWithText("Prendre une nouvelle photo").assertIsDisplayed()
    composeTestRule.onNodeWithText("Appuyez pour choisir une photo existante").assertIsDisplayed()
    composeTestRule.onNodeWithText("Qui est concerné - facultatif").assertExists()
    composeTestRule.onNodeWithText("Lieu - facultatif").assertExists()
    composeTestRule.onNodeWithText("Ajouter la photo").assertExists()
  }

  @Test
  fun pictureScreenErrorToastIsInFrench() {
    setPictureScreen(
        UploadPictureUIState(
            common =
                CommonMediaUIState(
                    errorMsgRes = UploadPictureViewModelErrorMessage.SELECT_PICTURE.messageRes
                )
        )
    )

    assertEquals(
        "Veuillez d'abord sélectionner une photo",
        ShadowToast.getTextOfLatestToast(),
    )
  }

  @Test
  fun peopleChipsShowNamesAsTypedAndUnknownMembersInFrench() {
    setPictureScreen(
        options = mapOf("p1" to UiText.Dynamic("Mamie Jo"), "m2" to unknownMemberLabel(2))
    )

    composeTestRule
        .onNodeWithTag(DisplayWhoIsInvolvedTestTags.personInvolved("p1"), useUnmergedTree = true)
        .assertExists()
    composeTestRule.onNodeWithText("Mamie Jo").assertExists()
    composeTestRule.onNodeWithText("Membre inconnu 2").assertExists()
  }

  @Test
  fun addPersonDialogAndItsNameErrorAreInFrench() {
    setPictureScreen(
        UploadPictureUIState(
            common =
                CommonMediaUIState(
                    isAddPersonOpen = true,
                    personFirstName = "Anne",
                    personNickname = "Nana2",
                )
        )
    )

    composeTestRule.onNodeWithTag(AddPersonDialogTestTags.DIALOG).assertIsDisplayed()
    composeTestRule.onNodeWithText("Ajouter une personne").assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(AddPersonDialogTestTags.CLOSE)
        .assertContentDescriptionEquals("Fermer")
    composeTestRule.onNodeWithText("Prénom").assertIsDisplayed()
    composeTestRule.onNodeWithText("Surnom - facultatif").assertIsDisplayed()
    composeTestRule
        .onNodeWithText(
            "Utilisez uniquement des lettres, des espaces, des traits d'union (-) et des " +
                "apostrophes (')"
        )
        .assertIsDisplayed()
  }

  @Test
  fun nobodyInTheCircleIsSaidInFrench() {
    composeTestRule.setContent { DisplayWhoIsInvolved() }

    composeTestRule
        .onNodeWithText("Personne ne fait partie de votre cercle de soins")
        .assertIsDisplayed()
  }

  @Test
  fun datePickerLabelsAndMonthErrorAreInFrench() {
    composeTestRule.setContent { DisplayScrollableDatePicker(initialDate = MemoryDate(2020)) }

    composeTestRule.onNodeWithText("Année").assertIsDisplayed()
    composeTestRule.onNodeWithText("Mois").assertIsDisplayed()
    composeTestRule.onNodeWithText("Jour").assertIsDisplayed()
    composeTestRule.onNodeWithTag(DatePickerTestTags.box(DateField.DAY)).performClick()
    composeTestRule
        .onNodeWithTag(DatePickerTestTags.MONTH_REQUIRED_ERROR)
        .assertTextEquals("Choisissez un mois avant de choisir un jour.")
  }

  @Test
  fun datePickerDialogTitleIsAWholeFrenchSentence() {
    composeTestRule.setContent { DisplayScrollableDatePicker(initialDate = MemoryDate(2020)) }

    composeTestRule.onNodeWithTag(DatePickerTestTags.box(DateField.MONTH)).performClick()

    composeTestRule
        .onNodeWithTag(DatePickerTestTags.dialogTitle(DateField.MONTH))
        .assertTextEquals("Choisir le mois")
  }

  @Test
  fun unknownMemberAndSharedErrorsResolveInFrench() {
    assertEquals("Membre inconnu 3", unknownMemberLabel(3).asString(context.resources))
    assertEquals(
        "Votre cercle de soins est encore en cours de chargement, réessayez",
        context.getString(MediaViewModelErrorMessage.STILL_LOADING.messageRes),
    )
  }

  @Test
  fun countedHintsUseTheFrenchPluralForms() {
    val resources = context.resources
    assertEquals(
        "Maximum de 1 caractère atteint",
        resources.getQuantityString(R.plurals.textFieldMaxCharReached, 1, 1),
    )
    assertEquals(
        "Maximum de 50 caractères atteint",
        resources.getQuantityString(R.plurals.textFieldMaxCharReached, 50, 50),
    )
    assertEquals(
        "Maximum de 1 ligne atteint",
        resources.getQuantityString(R.plurals.textFieldMaxLinesReached, 1, 1),
    )
    assertEquals(
        "Saisissez au moins 1 lettre pour rechercher, ou videz le champ.",
        resources.getQuantityString(R.plurals.locationQueryTooShort, 1, 1),
    )
  }
}
