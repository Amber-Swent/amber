/* This file was written with the aid of AI.
 * Modified by Alaabrine, with assistance from Cursor.
 */
package com.github.se.amber.ui.authentication.roleLoading

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.model.user.Role
import com.github.se.amber.ui.authentication.signIn.AuthenticationButton
import com.github.se.amber.ui.authentication.signIn.AuthenticationLayout
import com.github.se.amber.ui.authentication.signIn.AuthenticationText
import com.github.se.amber.ui.theme.AmberAppTheme

/** Test tags of [RoleLoadingScreen] and of its private helpers. */
object RoleLoadingTestTags {
  const val LOADING_INDICATOR = "roleLoading_loadingIndicator"
  const val ERROR_MESSAGE = "roleLoading_errorMessage"
  const val RETRY_BUTTON = "roleLoading_retryButton"
  const val BACK_TO_LOGIN_BUTTON = "roleLoading_backToLoginButton"
}

/**
 * Screen shown while the role of the signed-in user is being loaded.
 *
 * @param modifier Modifier applied to the root layout.
 * @param onRoleLoaded Called once the user's role is known so navigation can switch graph.
 * @param noProfile Called when the signed-in user has no profile yet (onboarding).
 * @param onSignedOut Called after a successful sign-out from the error state.
 * @param viewModel ViewModel holding the state of the screen.
 */
@Composable
fun RoleLoadingScreen(
    modifier: Modifier = Modifier,
    onRoleLoaded: (Role) -> Unit = {},
    noProfile: () -> Unit = {},
    onSignedOut: () -> Unit = {},
    viewModel: RoleLoadingViewModel = viewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
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
    }
  }

  RoleLoadingContent(
      modifier = modifier,
      state = state,
      onRetry = { viewModel.loadRole() },
      onSignOut = { viewModel.signOut() },
  )
}

/**
 * Stateless content of [RoleLoadingScreen], rendered from [state] only.
 *
 * @param modifier Modifier applied to the root layout.
 * @param state State to display.
 * @param onRetry Called when the user presses Retry after a load failure.
 * @param onSignOut Called when the user presses Back to login.
 */
@Composable
internal fun RoleLoadingContent(
    modifier: Modifier = Modifier,
    state: RoleLoadingUIState = RoleLoadingUIState(),
    onRetry: () -> Unit = {},
    onSignOut: () -> Unit = {},
) {
  when (state.roleState) {
    // what is displayed when looking for the role
    is RoleState.Loading ->
        AuthenticationLayout(modifier = modifier) {
          CircularProgressIndicator(
              modifier = Modifier.testTag(RoleLoadingTestTags.LOADING_INDICATOR),
              color = MaterialTheme.colorScheme.primary,
          )
        }
    // when there is no profile it displays nothing because being redirected
    is RoleState.NoProfile -> Unit
    // what is displayed if the user is not connected or if Firestore has failed
    is RoleState.Error ->
        AuthenticationLayout(modifier = modifier) {
          AuthenticationText(
              text = "Could not load your profile.\nTry again.",
              modifier = Modifier.fillMaxWidth().testTag(RoleLoadingTestTags.ERROR_MESSAGE),
              textAlign = TextAlign.Center,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.error,
          )
          AuthenticationButton(
              text = "Retry",
              onClick = onRetry,
              modifier = Modifier.testTag(RoleLoadingTestTags.RETRY_BUTTON),
          )
          AuthenticationButton(
              text = "Back to login",
              onClick = onSignOut,
              enabled = (!state.signedOut && !state.isSigningOut),
              modifier = Modifier.testTag(RoleLoadingTestTags.BACK_TO_LOGIN_BUTTON),
          )
        }
    // when the role is loaded it displays nothing because being redirected
    is RoleState.Loaded -> Unit
  }
}

/** Preview of [RoleLoadingContent] in the error state, using the Amber application theme. */
@Preview(showBackground = true, name = "Error")
@Composable
private fun RoleLoadingContentErrorPreview() {
  AmberAppTheme(dynamicColor = false) {
    RoleLoadingContent(
        state = RoleLoadingUIState(roleState = RoleState.Error),
    )
  }
}
