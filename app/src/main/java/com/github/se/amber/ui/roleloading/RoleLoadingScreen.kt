// this code was written with the aid of AI
package com.github.se.amber.ui.roleloading

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.model.user.Role

object RoleLoadingTestTags {
  const val LOADING_INDICATOR = "roleLoading_loadingIndicator"
  const val ERROR_MESSAGE = "roleLoading_errorMessage"
  const val RETRY_BUTTON = "roleLoading_retryButton"
  const val BACK_TO_LOGIN_BUTTON = "roleLoading_backToLoginButton"
}

/** Screen shown while the role of the signed-in user is being loaded. */
@Composable
fun RoleLoadingScreen(
    onRoleLoaded: (Role) -> Unit,
    noProfile: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: RoleLoadingViewModel = viewModel(),
) {
  val state by viewModel.state.collectAsState()
  val context = LocalContext.current

  LaunchedEffect(Unit) {
    if (state.roleState is RoleState.Loading) {
      viewModel.loadRole()
    }
  }
  // Fires the callbacks as soon as the state is Loaded or NoProfile.
  LaunchedEffect(state.roleState) {
    val s = state.roleState
    // Callback fires immediately when the Loaded state is reached, passing the user's role
    // so that the app navigates to the matching graph (caregiver or patient) without showing
    // any UI.
    if (s is RoleState.Loaded) {
      onRoleLoaded(s.role)
    }
    // For NoProfile, the callback fires immediately when the state is reached,
    // triggering navigation to onboarding without showing any UI.
    else if (s is RoleState.NoProfile) {
      noProfile()
    }
  }

  // Show error message
  LaunchedEffect(state.errorMsg) {
    state.errorMsg?.let { message ->
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
      viewModel.clearErrorMsg()
    }
  }

  // when sign-out
  LaunchedEffect(state.signedOut) {
    if (state.signedOut) {
      onSignedOut()
      Toast.makeText(context, "Logout successful", Toast.LENGTH_SHORT).show()
    }
  }

  when (state.roleState) {
    // what is displayed when looking for the role
    is RoleState.Loading ->
        CircularProgressIndicator(
            modifier = Modifier.testTag(RoleLoadingTestTags.LOADING_INDICATOR)
        )
    // when there is no profile it displays nothing because being redirected
    is RoleState.NoProfile -> Unit
    // what is displayed if the user is not connected or if Firestore has failed
    is RoleState.Error -> {
      Column {
        Text(
            "Could not load your profile. Try again.",
            modifier = Modifier.testTag(RoleLoadingTestTags.ERROR_MESSAGE),
        )
        Button(
            onClick = { viewModel.loadRole() },
            modifier = Modifier.testTag(RoleLoadingTestTags.RETRY_BUTTON),
        ) {
          Text("Retry")
        }
        Button(
            onClick = { viewModel.signOut() },
            enabled = !state.signedOut,
            modifier = Modifier.testTag(RoleLoadingTestTags.BACK_TO_LOGIN_BUTTON),
        ) {
          Text("Back to login")
        }
      }
    }
    // when the role is loaded it displays nothing because being redirected
    is RoleState.Loaded -> Unit
  }
}
