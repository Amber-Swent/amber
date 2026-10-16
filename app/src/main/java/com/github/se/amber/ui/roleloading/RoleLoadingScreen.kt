// this code was written with the aid of AI
package com.github.se.amber.ui.roleloading

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.model.user.Role
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.PADDING_MEDIUM
import com.github.se.amber.ui.theme.lightOrange

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
      Toast.makeText(context, "Logout successful", Toast.LENGTH_SHORT).show()
      onSignedOut()
    }
  }

  RoleLoadingContent(
      state = state,
      onRetry = { viewModel.loadRole() },
      onSignOut = { viewModel.signOut() },
  )
}

@Composable
fun RoleLoadingContent(
    state: RoleLoadingUIState,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
) {
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
      Column(
          Modifier.fillMaxSize().padding(PADDING_MEDIUM),
          verticalArrangement = Arrangement.spacedBy(PADDING_MEDIUM, Alignment.CenterVertically),
          horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
            "Could not load your profile.\nTry again.",
            modifier = Modifier.fillMaxWidth().testTag(RoleLoadingTestTags.ERROR_MESSAGE),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(PADDING_MEDIUM))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().testTag(RoleLoadingTestTags.RETRY_BUTTON),
            colors = ButtonDefaults.buttonColors(containerColor = lightOrange),
        ) {
          Text(
              text = "Retry",
              color = Color.Black,
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.Bold,
          )
        }
        Button(
            onClick = onSignOut,
            enabled = (!state.signedOut && !state.isSigningOut),
            modifier = Modifier.fillMaxWidth().testTag(RoleLoadingTestTags.BACK_TO_LOGIN_BUTTON),
            colors = ButtonDefaults.buttonColors(containerColor = lightOrange),
        ) {
          Text(
              text = "Back to login",
              color = Color.Black,
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.Bold,
          )
        }
      }
    }
    // when the role is loaded it displays nothing because being redirected
    is RoleState.Loaded -> Unit
  }
}

@Preview(showBackground = true, name = "Error")
@Composable
private fun RoleLoadingContentLoadingPreview() {
  AmberAppTheme(dynamicColor = false) {
    RoleLoadingContent(
        state = RoleLoadingUIState(roleState = RoleState.Error),
        onRetry = {},
        onSignOut = {},
    )
  }
}
