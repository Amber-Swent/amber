// this code was written with the aid of AI
package com.github.se.amber.ui.roleLoading

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
    viewModel: RoleLoadingViewModel = viewModel(),
    onSignedOut: () -> Unit,
) {
  val state by viewModel.state.collectAsState()

  LaunchedEffect(Unit) {
    if (state is RoleState.Loading) {
      viewModel.loadRole()
    }
  }
  // when the state changes to Loaded
  LaunchedEffect(state) {
    val s = state
    if (s is RoleState.Loaded) {
      onRoleLoaded(s.role)
    }
  }
  when (state) {
    // what is displayed when looking for the role
    is RoleState.Loading ->
        CircularProgressIndicator(
            modifier = Modifier.testTag(RoleLoadingTestTags.LOADING_INDICATOR)
        )
    // what is displayed when the user is connected but there are no user document with this uid
    is RoleState.NoProfile ->
        Button(
            onClick = {
              viewModel.signOut()
              onSignedOut()
            },
            modifier = Modifier.testTag(RoleLoadingTestTags.BACK_TO_LOGIN_BUTTON),
        ) {
          Text("Back to login")
        }
    // what is displayed if the user is not connected or if Firestore has failed
    is RoleState.Error ->
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
        }
    // when the role is loaded it displays nothing because being redirected
    is RoleState.Loaded -> Unit
  }
}
