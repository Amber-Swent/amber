// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Alaabrine, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.model.user.Role
import com.github.se.amber.ui.authentication.signIn.AuthenticationButton
import com.github.se.amber.ui.authentication.signIn.AuthenticationLayout
import com.github.se.amber.ui.authentication.signIn.AuthenticationText
import com.github.se.amber.ui.theme.AmberAppTheme

/** Test tags of [RoleLoadingScreen]. */
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
 * @param onNeedsCareCircle Called when the user has a profile but no care circle yet.
 * @param onSignedOut Called after a successful sign-out from the error state.
 * @param viewModel ViewModel holding the state of the screen.
 */
@Composable
fun RoleLoadingScreen(
    modifier: Modifier = Modifier,
    onRoleLoaded: (Role) -> Unit = {},
    noProfile: () -> Unit = {},
    onNeedsCareCircle: (CareCircleDestination) -> Unit = {},
    onSignedOut: () -> Unit = {},
    viewModel: RoleLoadingViewModel = viewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  // Configuration-aware resources, so the toast follows the app language
  val resources = LocalResources.current
  val context = LocalContext.current

  LaunchedEffect(Unit) {
    if (state.roleState is RoleState.Loading) {
      viewModel.loadRole()
    }
  }
  // Fires the callbacks as soon as the state is Loaded, NoProfile, or NeedsCareCircle.
  LaunchedEffect(state.roleState) {
    when (val roleState = state.roleState) {
      // Callback fires immediately when the Loaded state is reached, passing the user's role
      // so that the app navigates to the matching graph (caregiver or patient) without showing
      // any UI.
      is RoleState.Loaded -> onRoleLoaded(roleState.role)
      // For NoProfile, the callback fires immediately when the state is reached,
      // triggering navigation to onboarding without showing any UI.
      RoleState.NoProfile -> noProfile()
      // Profile exists but has no circle: caregiver chooses, patient enters an invitation.
      is RoleState.NeedsCareCircle -> onNeedsCareCircle(roleState.destination)
      RoleState.Loading,
      RoleState.Error -> Unit
    }
  }

  // Show error message
  LaunchedEffect(state.errorMsgRes) {
    state.errorMsgRes?.let { errorMsgRes ->
      Toast.makeText(context, resources.getString(errorMsgRes), Toast.LENGTH_SHORT).show()
      viewModel.clearErrorMsg()
    }
  }

  // when sign-out
  LaunchedEffect(state.signedOut) {
    if (state.signedOut) {
      onSignedOut()
    }
  }

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
    // profile without a circle displays nothing because being redirected
    is RoleState.NeedsCareCircle -> Unit
    // what is displayed if the user is not connected or if Firestore has failed
    is RoleState.Error ->
        AuthenticationLayout(modifier = modifier) {
          AuthenticationText(
              text = stringResource(R.string.roleLoadingError),
              modifier = Modifier.fillMaxWidth().testTag(RoleLoadingTestTags.ERROR_MESSAGE),
              textAlign = TextAlign.Center,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.error,
          )
          AuthenticationButton(
              text = stringResource(R.string.roleLoadingRetry),
              onClick = { viewModel.loadRole() },
              modifier = Modifier.testTag(RoleLoadingTestTags.RETRY_BUTTON),
          )
          AuthenticationButton(
              text = stringResource(R.string.roleLoadingBackToLogin),
              onClick = { viewModel.signOut() },
              enabled = (!state.signedOut && !state.isSigningOut),
              modifier = Modifier.testTag(RoleLoadingTestTags.BACK_TO_LOGIN_BUTTON),
          )
        }
    // when the role is loaded it displays nothing because being redirected
    is RoleState.Loaded -> Unit
  }
}

/** Preview of [RoleLoadingScreen] using the Amber application theme. */
@Preview(showBackground = true)
@Composable
private fun RoleLoadingScreenPreview() {
  AmberAppTheme(dynamicColor = false) { RoleLoadingScreen() }
}
