// this code was written with the aid of AI
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.espresso.Espresso
import com.github.se.amber.ui.authentication.signOut.SignOutButtonTestTags
import com.github.se.amber.ui.caregiver.parameters.CaregiverParametersTestTags
import com.github.se.amber.ui.caregiver.upload.UploadTestTags
import com.github.se.amber.ui.navigation.AuthScreen
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags.tabTag
import com.github.se.amber.ui.navigation.CaregiverScreen
import com.github.se.amber.ui.navigation.CaregiverTab
import com.github.se.amber.ui.navigation.NavGraphs
import com.github.se.amber.ui.navigation.OnboardingScreen
import com.github.se.amber.ui.navigation.PatientScreen
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.onboarding.choice.CaregiverChoiceTestTags
import com.github.se.amber.ui.onboarding.invitationRedemption.InvitationRedemptionTestTags
import com.github.se.amber.ui.patient.parameters.PatientParameterTestTags
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented tests for the navigation wiring of [AmberApp].
 *
 * Each test starts [AmberApp] in a given graph with a [TestNavHostController], performs a click on
 * a real UI element, and checks the route of the destination reached. Only the destinations that
 * are implemented are covered (empty `composable` placeholders are not tested).
 *
 * REQUIREMENTS:
 * - [AmberApp] must take `navController: NavHostController = rememberNavController()` as parameter
 * - `androidTestImplementation("androidx.navigation:navigation-testing:<version>")`
 * - Fill in the [Tags] object below with the real test tags (ideally from `C.Tag`)
 */
class AmberAppNavigationTest {

  /**
   * Test tags of the clickable elements that trigger a navigation. Replace each value with the real
   * tag used in the corresponding screen.
   */
  private object Tags {
    // Onboarding
    val CHOICE_CREATE_CIRCLE_BUTTON = CaregiverChoiceTestTags.CREATE_BUTTON
    val CHOICE_JOIN_CIRCLE_BUTTON = CaregiverChoiceTestTags.JOIN_BUTTON
    val CHOICE_BACK = CaregiverChoiceTestTags.BACK_BUTTON
    val INVITATION_REDEMPTION_BACK = InvitationRedemptionTestTags.BACK_BUTTON

    // Caregiver
    val CAREGIVER_TAB_HOME = tabTag(CaregiverTab.Home)
    val CAREGIVER_TAB_UPLOAD = tabTag(CaregiverTab.Upload)
    val CAREGIVER_TAB_PARAMETERS = tabTag(CaregiverTab.Setting)
    const val UPLOAD_PICTURE_BUTTON = UploadTestTags.PICTURE_BUTTON
    const val UPLOAD_BACK = TopNavigationMenuTestTags.BACK_BUTTON
    const val UPLOAD_PICTURE_BACK = TopNavigationMenuTestTags.BACK_BUTTON
    const val CREATE_INVITATION_BUTTON = CaregiverParametersTestTags.CREATE_INVITATION_BUTTON
    const val ENTER_INVITATION_BUTTON = CaregiverParametersTestTags.ENTER_INVITATION_BUTTON
    const val CAREGIVER_PARAMETERS_SIGN_OUT = CaregiverParametersTestTags.SIGN_OUT_BUTTON
    const val CREATE_INVITATION_BACK = TopNavigationMenuTestTags.BACK_BUTTON
    const val SIGN_OUT_CONFIRM = SignOutButtonTestTags.BUTTON
    const val SIGN_OUT_BACK = TopNavigationMenuTestTags.BACK_BUTTON

    // Patient
    val PATIENT_TAB_HOME = tabTag(PatientTab.Home)
    val PATIENT_TAB_SEE_MEDIAS = tabTag(PatientTab.SeeMedias)
    val PATIENT_TAB_SEE_PICTURES = tabTag(PatientTab.SeePictures)
    val PATIENT_TAB_PARAMETERS = tabTag(PatientTab.Setting)
    const val PATIENT_PARAMETERS_SIGN_OUT = PatientParameterTestTags.SIGN_OUT_BUTTON
    const val SEE_PICTURES_BACK = TopNavigationMenuTestTags.BACK_BUTTON
  }

  @get:Rule val composeRule = createComposeRule()

  private lateinit var navController: TestNavHostController

  @Before
  fun setUp() {
    // Nothing to do: the controller is created inside setContent (needs a Compose context)
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------------

  /** Launches [AmberApp] with a [TestNavHostController], starting at [start] (a graph or route). */
  private fun launch(start: String) {
    composeRule.setContent {
      val context = LocalContext.current
      val controller = remember {
        TestNavHostController(context).apply { navigatorProvider.addNavigator(ComposeNavigator()) }
      }
      navController = controller
      AmberApp(navController = controller, startDestination = start)
    }
    composeRule.waitForIdle()
  }

  /** Programmatically moves to [route] (used to reach a nested screen before clicking). */
  private fun goTo(route: String) {
    composeRule.runOnUiThread { navController.navigate(route) }
    composeRule.waitForIdle()
  }

  private fun click(tag: String) {
    // The screen can still be composing after a navigation, so the click target is not there yet.
    composeRule.waitUntil(timeoutMillis = 5_000) {
      try {
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
      } catch (_: IllegalStateException) {
        false
      }
    }
    composeRule.onNodeWithTag(tag).performClick()
    composeRule.waitForIdle()
  }

  private fun assertRoute(expected: String) {
    assertEquals(expected, navController.currentDestination?.route)
  }

  private fun waitForRoute(expected: String) {
    composeRule.waitUntil(timeoutMillis = 5_000) {
      navController.currentDestination?.route == expected
    }
    assertRoute(expected)
  }

  // ---------------------------------------------------------------------------------------------
  // Start destination of each implemented graph
  // ---------------------------------------------------------------------------------------------

  @Test
  fun authGraph_startsOnAuthScreen() {
    launch(NavGraphs.AUTH)
    assertRoute(AuthScreen.Auth.route)
  }

  @Test
  fun caregiverGraph_startsOnCaregiverHome() {
    launch(NavGraphs.CAREGIVER)
    assertRoute(CaregiverScreen.Home.route)
  }

  @Test
  fun patientGraph_startsOnPatientHome() {
    launch(NavGraphs.PATIENT)
    assertRoute(PatientScreen.Home.route)
  }

  // ---------------------------------------------------------------------------------------------
  // Onboarding graph
  // ---------------------------------------------------------------------------------------------

  @Test
  fun onboardingGraph_startsOnCreateProfile() {
    launch(NavGraphs.ONBOARDING)
    assertRoute(OnboardingScreen.CreateProfile.route)
  }

  @Test
  fun caregiverChoice_createCircle_navigatesToCreateCareCircle() {
    launch(NavGraphs.ONBOARDING)
    goTo(OnboardingScreen.CaregiverChoice.route)

    click(Tags.CHOICE_CREATE_CIRCLE_BUTTON)

    assertRoute(OnboardingScreen.CreateCareCircle.route)
  }

  @Test
  fun caregiverChoice_joinCircle_navigatesToEnterInvitation() {
    launch(NavGraphs.ONBOARDING)
    goTo(OnboardingScreen.CaregiverChoice.route)

    click(Tags.CHOICE_JOIN_CIRCLE_BUTTON)

    assertRoute(OnboardingScreen.EnterInvitation.route)
  }

  @Test
  fun caregiverChoice_back_returnsToPreviousScreen() {
    launch(NavGraphs.ONBOARDING)
    goTo(OnboardingScreen.CaregiverChoice.route)

    click(Tags.CHOICE_BACK)

    // goBack() pops once: we come back to CreateProfile
    assertRoute(OnboardingScreen.CreateProfile.route)
  }

  @Test
  fun enterInvitation_back_returnsToParameters() {
    // Cross-graph: Parameters -> EnterInvitation, then back
    launch(NavGraphs.CAREGIVER)
    goTo(CaregiverScreen.Parameters.route)
    click(Tags.ENTER_INVITATION_BUTTON)

    click(Tags.INVITATION_REDEMPTION_BACK.name)

    assertRoute(CaregiverScreen.Parameters.route)
  }

  // ---------------------------------------------------------------------------------------------
  // Caregiver graph: Home
  // ---------------------------------------------------------------------------------------------

  @Test
  fun caregiverHome_uploadTab_navigatesToUpload() {
    launch(NavGraphs.CAREGIVER)

    click(Tags.CAREGIVER_TAB_UPLOAD)

    assertRoute(CaregiverScreen.Upload.route)
  }

  @Test
  fun caregiverHome_parametersTab_navigatesToParameters() {
    launch(NavGraphs.CAREGIVER)

    click(Tags.CAREGIVER_TAB_PARAMETERS)

    assertRoute(CaregiverScreen.Parameters.route)
  }

  // ---------------------------------------------------------------------------------------------
  // Caregiver graph: Upload
  // ---------------------------------------------------------------------------------------------

  @Test
  fun upload_uploadPictureButton_navigatesToUploadPicture() {
    launch(NavGraphs.CAREGIVER)
    goTo(CaregiverScreen.Upload.route)

    click(Tags.UPLOAD_PICTURE_BUTTON)

    assertRoute(CaregiverScreen.UploadPicture.route)
  }

  @Test
  fun upload_homeTab_navigatesToCaregiverHome() {
    launch(NavGraphs.CAREGIVER)
    goTo(CaregiverScreen.Upload.route)

    click(Tags.CAREGIVER_TAB_HOME)

    assertRoute(CaregiverScreen.Home.route)
  }

  @Test
  fun upload_parametersTab_navigatesToParameters() {
    launch(NavGraphs.CAREGIVER)
    goTo(CaregiverScreen.Upload.route)

    click(Tags.CAREGIVER_TAB_PARAMETERS)

    assertRoute(CaregiverScreen.Parameters.route)
  }

  @Test
  fun upload_back_navigatesToCaregiverHome() {
    launch(NavGraphs.CAREGIVER)
    goTo(CaregiverScreen.Upload.route)

    click(Tags.UPLOAD_BACK)

    assertRoute(CaregiverScreen.Home.route)
  }

  // ---------------------------------------------------------------------------------------------
  // Caregiver graph: UploadPicture
  // ---------------------------------------------------------------------------------------------

  @Test
  fun uploadPicture_back_returnsToUpload() {
    launch(NavGraphs.CAREGIVER)
    goTo(CaregiverScreen.Upload.route)
    goTo(CaregiverScreen.UploadPicture.route)

    click(Tags.UPLOAD_PICTURE_BACK)

    assertRoute(CaregiverScreen.Upload.route)
  }

  // ---------------------------------------------------------------------------------------------
  // Caregiver graph: Parameters
  // ---------------------------------------------------------------------------------------------

  @Test
  fun parameters_signOut_navigatesToSignOutScreen() {
    launch(NavGraphs.CAREGIVER)
    goTo(CaregiverScreen.Parameters.route)

    click(Tags.CAREGIVER_PARAMETERS_SIGN_OUT)

    assertRoute(CaregiverScreen.SignOut.route)
  }

  @Test
  fun caregiverSignOut_back_returnsToParameters() {
    launch(NavGraphs.CAREGIVER)
    goTo(CaregiverScreen.Parameters.route)
    goTo(CaregiverScreen.SignOut.route)

    click(Tags.SIGN_OUT_BACK)

    assertRoute(CaregiverScreen.Parameters.route)
  }

  @Test
  fun caregiverSignOut_confirm_navigatesToAuth() {
    launch(NavGraphs.CAREGIVER)
    goTo(CaregiverScreen.Parameters.route)
    goTo(CaregiverScreen.SignOut.route)

    click(Tags.SIGN_OUT_CONFIRM)

    composeRule.waitUntil(timeoutMillis = 5_000) {
      navController.currentDestination?.route == AuthScreen.Auth.route
    }

    assertRoute(AuthScreen.Auth.route)
  }

  @Test
  fun parameters_homeTab_navigatesToCaregiverHome() {
    launch(NavGraphs.CAREGIVER)
    goTo(CaregiverScreen.Parameters.route)

    click(Tags.CAREGIVER_TAB_HOME)

    assertRoute(CaregiverScreen.Home.route)
  }

  @Test
  fun parameters_uploadTab_navigatesToUpload() {
    launch(NavGraphs.CAREGIVER)
    goTo(CaregiverScreen.Parameters.route)

    click(Tags.CAREGIVER_TAB_UPLOAD)

    assertRoute(CaregiverScreen.Upload.route)
  }

  @Test
  fun parameters_createInvitation_navigatesToCreateInvitationCode() {
    launch(NavGraphs.CAREGIVER)
    goTo(CaregiverScreen.Parameters.route)

    click(Tags.CREATE_INVITATION_BUTTON)

    assertRoute(CaregiverScreen.CreateInvitationCode.route)
  }

  @Test
  fun parameters_enterInvitation_navigatesToOnboardingEnterInvitation() {
    // Cross-graph navigation: caregiver graph -> onboarding graph
    launch(NavGraphs.CAREGIVER)
    goTo(CaregiverScreen.Parameters.route)

    click(Tags.ENTER_INVITATION_BUTTON)

    assertRoute(OnboardingScreen.EnterInvitation.route)
  }

  // ---------------------------------------------------------------------------------------------
  // Caregiver graph: CreateInvitationCode
  // ---------------------------------------------------------------------------------------------

  @Test
  fun createInvitation_back_returnsToParameters() {
    launch(NavGraphs.CAREGIVER)
    goTo(CaregiverScreen.Parameters.route)
    goTo(CaregiverScreen.CreateInvitationCode.route)

    click(Tags.CREATE_INVITATION_BACK)

    // goBack() pops the stack once: we come back to where we were
    assertRoute(CaregiverScreen.Parameters.route)
  }

  @Test
  fun createInvitation_homeTab_navigatesToCaregiverHome() {
    launch(NavGraphs.CAREGIVER)
    goTo(CaregiverScreen.CreateInvitationCode.route)

    click(Tags.CAREGIVER_TAB_HOME)

    assertRoute(CaregiverScreen.Home.route)
  }

  // ---------------------------------------------------------------------------------------------
  // Patient graph
  // ---------------------------------------------------------------------------------------------

  @Test
  fun patientHome_seePicturesTab_navigatesToSeePictures() {
    launch(NavGraphs.PATIENT)

    click(Tags.PATIENT_TAB_SEE_PICTURES)

    assertRoute(PatientScreen.SeePictures.route)
  }

  @Test
  fun patientHome_parametersTab_navigatesToParameters() {
    launch(NavGraphs.PATIENT)

    click(Tags.PATIENT_TAB_PARAMETERS)

    assertRoute(PatientScreen.Parameters.route)
  }

  @Test
  fun patientParameters_signOut_navigatesToSignOutScreen() {
    launch(NavGraphs.PATIENT)
    goTo(PatientScreen.Parameters.route)

    click(Tags.PATIENT_PARAMETERS_SIGN_OUT)

    assertRoute(PatientScreen.SignOut.route)
  }

  @Test
  fun patientSignOut_confirm_navigatesToAuth() {
    launch(NavGraphs.PATIENT)
    goTo(PatientScreen.Parameters.route)
    goTo(PatientScreen.SignOut.route)

    click(Tags.SIGN_OUT_CONFIRM)

    composeRule.waitUntil(timeoutMillis = 5_000) {
      navController.currentDestination?.route == AuthScreen.Auth.route
    }

    assertRoute(AuthScreen.Auth.route)
  }

  @Test
  fun patientHome_seeMediasTab_navigatesToSeeMedias() {
    launch(NavGraphs.PATIENT)

    click(Tags.PATIENT_TAB_SEE_MEDIAS)

    assertRoute(PatientScreen.SeeMedias.route)
  }

  @Test
  fun seePictures_homeTab_navigatesToPatientHome() {
    launch(NavGraphs.PATIENT)
    goTo(PatientScreen.SeePictures.route)

    click(Tags.PATIENT_TAB_HOME)

    assertRoute(PatientScreen.Home.route)
  }

  @Test
  fun seePictures_back_navigatesToPatientHome() {
    launch(NavGraphs.PATIENT)
    goTo(PatientScreen.SeePictures.route)

    click(Tags.SEE_PICTURES_BACK)

    assertRoute(PatientScreen.Home.route)
  }

  @Test
  fun caregiverTabs_systemBack_returnsToPreviousTab() {
    launch(NavGraphs.CAREGIVER)
    click(Tags.CAREGIVER_TAB_UPLOAD)
    click(Tags.CAREGIVER_TAB_PARAMETERS)

    Espresso.pressBack()
    composeRule.waitForIdle()

    // Home -> Upload -> Parameters -> back: we expect to land on Upload
    assertRoute(CaregiverScreen.Upload.route)
  }
}
