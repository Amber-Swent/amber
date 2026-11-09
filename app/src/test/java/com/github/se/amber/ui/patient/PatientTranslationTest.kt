// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.patient

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.github.se.amber.R
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.patient.parameters.PatientParameterScreen
import com.github.se.amber.ui.patient.parameters.PatientParameterTestTags
import com.github.se.amber.ui.patient.parameters.PatientParameterViewModel
import com.github.se.amber.ui.patient.seeMedias.SeeMediasScreen
import com.github.se.amber.ui.patient.seeMedias.SeeMediasTestTags
import com.github.se.amber.ui.patient.seeMedias.audios.SeeAudiosScreen
import com.github.se.amber.ui.patient.seeMedias.audios.SeeAudiosTestTags
import com.github.se.amber.ui.patient.seeMedias.pictures.SeePicturesScreen
import com.github.se.amber.ui.patient.seeMedias.pictures.SeePicturesTestTags
import com.github.se.amber.ui.patient.seeMedias.pictures.SeePicturesUIState
import com.github.se.amber.ui.patient.seeMedias.pictures.SeePicturesViewModel
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

/** Checks that the patient screens show their text and errors in French. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class PatientTranslationTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val patient =
      UserProfile(uid = "p1", role = Role.PATIENT, person = Person(firstName = "Arthur"))

  private fun setHome(state: PatientHomeUIState) {
    val viewModel = mockk<PatientHomeViewModel>(relaxed = true)
    every { viewModel.uiState } returns MutableStateFlow(state)
    composeTestRule.setContent { PatientHomeScreen(homeViewModel = viewModel) }
    composeTestRule.waitForIdle()
  }

  private fun setSeePictures(state: SeePicturesUIState) {
    val viewModel = mockk<SeePicturesViewModel>(relaxed = true)
    every { viewModel.uiState } returns MutableStateFlow(state)
    composeTestRule.setContent { SeePicturesScreen(pictureViewModel = viewModel) }
    composeTestRule.waitForIdle()
  }

  @Test
  fun homeTitleAndGreetingWithTheNameAreInFrench() {
    setHome(PatientHomeUIState(patientProfile = patient))

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.TITLE).assertTextEquals("Accueil")
    composeTestRule
        .onNodeWithTag(PatientHomeTestTags.GREETINGS)
        .assertTextEquals("Bonjour Arthur !")
  }

  @Test
  fun homeErrorToastIsInFrench() {
    setHome(PatientHomeUIState(errorMsgRes = R.string.patientHomeErrorLoadProfile))

    assertEquals("Impossible de charger le profil.", ShadowToast.getTextOfLatestToast())
  }

  @Test
  fun parametersTitleAndSignOutButtonAreInFrench() {
    val viewModel = PatientParameterViewModel()
    composeTestRule.setContent { PatientParameterScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithTag(PatientParameterTestTags.TITLE).assertTextEquals("Paramètres")
    composeTestRule
        .onNodeWithTag(PatientParameterTestTags.SIGN_OUT_BUTTON)
        .assertTextEquals("Se déconnecter")
  }

  @Test
  fun seeMediasTitleAndButtonsAreInFrench() {
    composeTestRule.setContent { SeeMediasScreen() }

    composeTestRule.onNodeWithTag(SeeMediasTestTags.TITLE).assertTextEquals("Voir les médias")
    composeTestRule.onNodeWithText("Voir les photos").assertIsDisplayed()
    composeTestRule.onNodeWithText("Voir les textes").assertIsDisplayed()
    composeTestRule.onNodeWithText("Voir les audios").assertIsDisplayed()
  }

  @Test
  fun seePicturesTitleAndEmptyMessageAreInFrench() {
    setSeePictures(SeePicturesUIState.Empty(patientProfile = patient, careCircle = CareCircle()))

    composeTestRule.onNodeWithTag(SeePicturesTestTags.TITLE).assertTextEquals("Voir les photos")
    composeTestRule
        .onNodeWithTag(SeePicturesTestTags.NO_PICTURES)
        .assertTextEquals("Il n'y a pas encore de photos.")
  }

  @Test
  fun seePicturesBlockingErrorAndRetryAreInFrench() {
    setSeePictures(SeePicturesUIState.BlockingError(R.string.seePicturesErrorNoCircle))

    composeTestRule
        .onNodeWithTag(SeePicturesTestTags.ERROR)
        .assertTextEquals("Aucun cercle de soins trouvé pour ce patient.")
    composeTestRule.onNodeWithText("Réessayer").assertIsDisplayed()
  }

  @Test
  fun seePicturesMediaErrorToastIsInFrench() {
    setSeePictures(
        SeePicturesUIState.Empty(
            patientProfile = patient,
            careCircle = CareCircle(),
            errorMsgRes = R.string.seePicturesErrorLoadMedia,
        )
    )

    assertEquals("Impossible de charger les médias.", ShadowToast.getTextOfLatestToast())
  }

  @Test
  fun seeAudiosTitleAndEmptyMessageAreInFrench() {
    composeTestRule.setContent { SeeAudiosScreen() }

    composeTestRule.onNodeWithTag(SeeAudiosTestTags.TITLE).assertTextEquals("Voir les audios")
    composeTestRule
        .onNodeWithTag(SeeAudiosTestTags.NO_AUDIOS)
        .assertTextEquals("Il n'y a pas encore d'audios.")
  }
}
