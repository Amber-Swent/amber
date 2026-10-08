// this code was written with the aid of AI

package com.android.sample

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
import com.github.se.amber.resources.C
import com.github.se.amber.ui.navigation.AuthScreen
import com.github.se.amber.ui.navigation.CaregiverScreen
import com.github.se.amber.ui.navigation.NavGraphs
import com.github.se.amber.ui.navigation.NavigationActions
import com.github.se.amber.ui.navigation.PatientScreen
import com.github.se.amber.ui.navigation.graphForAuthState
import com.github.se.amber.ui.theme.AmberAppTheme
import com.google.firebase.auth.FirebaseAuth
import com.github.se.amber.ui.navigation.graphForRole
import com.github.se.amber.ui.roleLoading.RoleLoadingScreen


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

@Composable
fun AmberApp(
    // TODO add context and credential manager
) {
  val navController: NavHostController = rememberNavController()
  val navigationActions = NavigationActions(navController)

  val startDestination = graphForAuthState(FirebaseAuth.getInstance().currentUser)

  NavHost(
      navController = navController,
      startDestination = startDestination,
  ) {

    composable(route = NavGraphs.ROLE_LOADING){
      RoleLoadingScreen(
        onRoleLoaded = { role ->
          navController.navigate(graphForRole(role)){
            popUpTo(NavGraphs.ROLE_LOADING) {inclusive = true}
          }
        }
      )
    }

    // Authentification Graph
    navigation(
        startDestination = AuthScreen.Auth.route,
        route = NavGraphs.AUTH,
    ) {
      // SignInScreen
      composable(route = AuthScreen.Auth.route) {
        // TODO
      }
    }

    // Caregiver Graph
    navigation(
        startDestination = CaregiverScreen.Home.route,
        route = NavGraphs.CAREGIVER,
    ) {
      composable(route = CaregiverScreen.Home.route) {
        // TODO
      }
      composable(route = CaregiverScreen.Upload.route) {
        // TODO
      }
      composable(route = CaregiverScreen.UploadText.route) {
        // TODO
      }
      composable(route = CaregiverScreen.UploadPicture.route) {
        // TODO
      }
      composable(route = CaregiverScreen.UploadAudio.route) {
        // TODO
      }
    }

    // Patient Graph
    navigation(
        startDestination = PatientScreen.Home.route,
        route = NavGraphs.PATIENT,
    ) {
      composable(route = PatientScreen.Home.route) {
        // TODO
      }
      composable(route = PatientScreen.SeePictures.route) {
        // TODO
      }
    }
  }
}
