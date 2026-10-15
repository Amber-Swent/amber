// this code was written with the aid of AI

package com.github.se.amber

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.github.se.amber.model.user.Role
import com.github.se.amber.resources.C
import com.github.se.amber.ui.accountCreation.CreateProfileScreen
import com.github.se.amber.ui.accountCreation.caregiverOnboarding.CaregiverChoiceScreen
import com.github.se.amber.ui.invitationRedemption.InvitationRedemptionScreen
import com.github.se.amber.ui.navigation.AuthScreen
import com.github.se.amber.ui.navigation.CaregiverScreen
import com.github.se.amber.ui.navigation.NavGraphs
import com.github.se.amber.ui.navigation.NavigationActions
import com.github.se.amber.ui.navigation.OnboardingScreen
import com.github.se.amber.ui.navigation.PatientScreen
import com.github.se.amber.ui.navigation.TransitionScreen
import com.github.se.amber.ui.navigation.graphForRole
import com.github.se.amber.ui.navigation.startDestinationForAuthState
import com.github.se.amber.ui.roleLoading.RoleLoadingScreen
import com.github.se.amber.ui.theme.AmberAppTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
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

@Suppress("java:S1135")
@Composable
fun AmberApp(
    // TODO add context and credential manager
    startDestination: String = startDestinationForAuthState(FirebaseAuth.getInstance().currentUser)
) {
  val navController: NavHostController = rememberNavController()
  val navigationActions = NavigationActions(navController)

  NavHost(
      navController = navController,
      startDestination = startDestination,
  ) {

    // Authentication Graph
    navigation(
        startDestination = AuthScreen.Auth.route,
        route = NavGraphs.AUTH,
    ) {
      // SignInScreen
      @Suppress("java:S1135")
      composable(route = AuthScreen.Auth.route) {
        // TODO
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
      @Suppress("java:S1135")
      composable(route = OnboardingScreen.CreateCareCircle.route) {
        // Todo
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
      @Suppress("java:S1135")
      composable(route = CaregiverScreen.Home.route) {
        // TODO
      }
      @Suppress("java:S1135")
      composable(route = CaregiverScreen.Upload.route) {
        // TODO
      }
      @Suppress("java:S1135")
      composable(route = CaregiverScreen.UploadText.route) {
        // TODO
      }
      @Suppress("java:S1135")
      composable(route = CaregiverScreen.UploadPicture.route) {
        // TODO
      }
      @Suppress("java:S1135")
      composable(route = CaregiverScreen.UploadAudio.route) {
        // TODO
      }
      @Suppress("java:S1135")
      composable(route = CaregiverScreen.Parameters.route) {
        // TODO
      }
      @Suppress("java:S1135")
      composable(route = CaregiverScreen.SeeInvitationCode.route) {
        // TODO
      }
    }

    // Patient Graph
    navigation(
        startDestination = PatientScreen.Home.route,
        route = NavGraphs.PATIENT,
    ) {
      @Suppress("java:S1135")
      composable(route = PatientScreen.Home.route) {
        // TODO
      }
      @Suppress("java:S1135")
      composable(route = PatientScreen.SeePictures.route) {
        // TODO
      }
    }
  }
}
