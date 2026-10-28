// this code was written with the aid of AI
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.credentials.CredentialManager
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.github.se.amber.data.connectivity.ConnectivityMonitorProvider
import com.github.se.amber.model.user.Role
import com.github.se.amber.resources.C
import com.github.se.amber.ui.authentication.roleLoading.RoleLoadingScreen
import com.github.se.amber.ui.authentication.signIn.EmailSignInScreen
import com.github.se.amber.ui.caregiver.CaregiverHomeScreen
import com.github.se.amber.ui.caregiver.parameters.CaregiverParameterScreen
import com.github.se.amber.ui.caregiver.parameters.CreateInvitationScreen
import com.github.se.amber.ui.caregiver.upload.UploadScreen
import com.github.se.amber.ui.caregiver.upload.picture.UploadPictureScreen
import com.github.se.amber.ui.media.LocalMediaRetrySignal
import com.github.se.amber.ui.navigation.AuthScreen
import com.github.se.amber.ui.navigation.CaregiverScreen
import com.github.se.amber.ui.navigation.NavGraphs
import com.github.se.amber.ui.navigation.NavigationActions
import com.github.se.amber.ui.navigation.OnboardingScreen
import com.github.se.amber.ui.navigation.PatientScreen
import com.github.se.amber.ui.navigation.TransitionScreen
import com.github.se.amber.ui.navigation.graphForRole
import com.github.se.amber.ui.navigation.startDestinationForAuthState
import com.github.se.amber.ui.onboarding.choice.CaregiverChoiceScreen
import com.github.se.amber.ui.onboarding.createCareCircle.CreateCareCircleScreen
import com.github.se.amber.ui.onboarding.createProfile.CreateProfileScreen
import com.github.se.amber.ui.onboarding.invitationRedemption.InvitationRedemptionScreen
import com.github.se.amber.ui.patient.PatientHomeScreen
import com.github.se.amber.ui.patient.seeMedias.pictures.SeePicturesScreen
import com.github.se.amber.ui.theme.AmberAppTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    setContent {
      // Media images that failed while offline load again by themselves once back online.
      CompositionLocalProvider(
          LocalMediaRetrySignal provides ConnectivityMonitorProvider.monitor.backOnline
      ) {
        AmberAppTheme {
          // A surface container using the 'background' color from the theme
          Surface(
              modifier = Modifier.fillMaxSize().semantics { testTag = C.Tag.main_screen_container },
              color = MaterialTheme.colorScheme.background,
          ) {
            AmberApp()
          }
        }
      }
    }
  }
}

@Composable
fun AmberApp(
    modifier: Modifier = Modifier,
    // Gives Google sign-in its account; tests pass a fake one
    credentialManager: CredentialManager = CredentialManager.create(LocalContext.current),
    navController: NavHostController = rememberNavController(),
    startDestination: String = startDestinationForAuthState(FirebaseAuth.getInstance().currentUser),
) {

  val navigationActions = remember(navController) { NavigationActions(navController) }

  NavHost(
      navController = navController,
      startDestination = startDestination,
      modifier = modifier,
  ) {

    // Authentication Graph
    navigation(
        startDestination = AuthScreen.Auth.route,
        route = NavGraphs.AUTH,
    ) {
      // SignInScreen
      composable(route = AuthScreen.Auth.route) {
        EmailSignInScreen(
            credentialManager = credentialManager,
            // onContinue will be implemented in M2/M3
            onGoogleSignIn = { navigationActions.navigateTo(TransitionScreen.RoleLoading) },
        )
      }
    }

    // Loading Screen
    composable(route = TransitionScreen.RoleLoading.route) {
      RoleLoadingScreen(
          onRoleLoaded = { role -> navigationActions.navigateToGraph(graphForRole(role)) },
          noProfile = { navigationActions.navigateToGraph(NavGraphs.ONBOARDING) },
          onSignedOut = { navigationActions.navigateToGraph(NavGraphs.AUTH) },
      )
    }

    // Onboarding Screen
    navigation(
        startDestination = OnboardingScreen.CreateProfile.route,
        route = NavGraphs.ONBOARDING,
    ) {
      composable(route = OnboardingScreen.CreateProfile.route) {
        CreateProfileScreen(
            onProfileCreated = { role ->
              when (role) {
                Role.PATIENT -> navigationActions.navigateTo(OnboardingScreen.EnterInvitation)
                Role.CAREGIVER -> navigationActions.navigateTo(OnboardingScreen.CaregiverChoice)
              }
            },
        )
      }
      composable(route = OnboardingScreen.CaregiverChoice.route) {
        CaregiverChoiceScreen(
            onBack = { navigationActions.goBack() },
            onCreateCircle = { navigationActions.navigateTo(OnboardingScreen.CreateCareCircle) },
            onJoinCircle = { navigationActions.navigateTo(OnboardingScreen.EnterInvitation) },
        )
      }
      composable(route = OnboardingScreen.CreateCareCircle.route) {
        CreateCareCircleScreen(
            onCreated = { navigationActions.navigateToGraph(NavGraphs.CAREGIVER) }
        )
      }
      composable(route = OnboardingScreen.EnterInvitation.route) {
        InvitationRedemptionScreen(
            onRedeemSuccess = { result ->
              navigationActions.navigateToGraph(graphForRole(result.role))
            },
            onBack = { navigationActions.goBack() },
        )
      }
    }

    // Caregiver Graph
    navigation(
        startDestination = CaregiverScreen.Home.route,
        route = NavGraphs.CAREGIVER,
    ) {
      composable(route = CaregiverScreen.Home.route) {
        CaregiverHomeScreen(
            onTabSelected = { tab -> navigationActions.navigateToTab(tab) },
            onSignedOut = { navigationActions.navigateToGraph(NavGraphs.AUTH) },
        )
      }
      composable(route = CaregiverScreen.Upload.route) {
        UploadScreen(
            // onUploadAudioClick and onUploadTextClick will be implemented in M2/M3
            onUploadPictureClick = { navigationActions.navigateTo(CaregiverScreen.UploadPicture) },
            onTabSelected = { tab -> navigationActions.navigateToTab(tab) },
            // For M1 we have decided that the back button on the top bar redirect to the Home
            // Screen if we were on a top level destination
            checkout = { navigationActions.navigateToGraph(NavGraphs.CAREGIVER) },
        )
      }
      @Suppress("java:S1135")
      composable(route = CaregiverScreen.UploadText.route) {
        // TODO in M2/M3
      }
      composable(route = CaregiverScreen.UploadPicture.route) {
        UploadPictureScreen(
            // For M1 at least, may change for M2
            onUpload = { navigationActions.navigateToGraph(NavGraphs.CAREGIVER) },
            goBack = { navigationActions.goBack() },
        )
      }
      @Suppress("java:S1135")
      composable(route = CaregiverScreen.UploadAudio.route) {
        // TODO in M2/M3
      }
      composable(route = CaregiverScreen.Parameters.route) {
        CaregiverParameterScreen(
            // For M1 we have decided that the back button on the top bar redirect to the Home
            // Screen if we were on a top level destination
            onCheckout = { navigationActions.navigateToGraph(NavGraphs.CAREGIVER) },
            onTabSelected = { tab -> navigationActions.navigateToTab(tab) },
            onCreateInvitationClick = {
              navigationActions.navigateTo(CaregiverScreen.CreateInvitationCode)
            },
            onEnterInvitationClick = {
              navigationActions.navigateTo(OnboardingScreen.EnterInvitation)
            },
        )
      }
      composable(route = CaregiverScreen.CreateInvitationCode.route) {
        CreateInvitationScreen(
            onTabSelected = { tab -> navigationActions.navigateToTab(tab) },
            checkout = { navigationActions.goBack() },
        )
      }
    }

    // Patient Graph
    navigation(
        startDestination = PatientScreen.Home.route,
        route = NavGraphs.PATIENT,
    ) {
      composable(route = PatientScreen.Home.route) {
        PatientHomeScreen(
            onSignedOut = { navigationActions.navigateToGraph(NavGraphs.AUTH) },
            onTabSelected = { tab -> navigationActions.navigateToTab(tab) },
        )
      }
      composable(route = PatientScreen.SeePictures.route) {
        SeePicturesScreen(
            // For M1 we have decided that the back button on the top bar redirect to the Home
            // Screen
            // if we were on a top level destination
            onBackClick = { navigationActions.navigateToGraph(NavGraphs.PATIENT) },
            onTabSelected = { tab -> navigationActions.navigateToTab(tab) },
        )
      }
    }
  }
}
