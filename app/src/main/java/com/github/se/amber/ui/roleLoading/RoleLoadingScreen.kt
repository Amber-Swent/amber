// this code was written with the aid of AI
package com.github.se.amber.ui.roleLoading

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.model.user.Role

/** Screen shown while the role of the signed-in user is being loaded. */
@Composable
fun RoleLoadingScreen(
    onRoleLoaded: (Role) -> Unit,
    viewModel: RoleLoadingViewModel = viewModel(),
) {
  val state by viewModel.state.collectAsState()

  LaunchedEffect(Unit) { viewModel.loadRole() }
  // when the state change and is l
  LaunchedEffect(state) {
    val s = state
    if (s is RoleState.Loaded) {
      onRoleLoaded(s.role)
    }
  }
  when (state) {
    // what is displayed when looking for the role
    is RoleState.Loading -> CircularProgressIndicator()
    // what is displayed when the user is connected but there are no user document with this uid
    is RoleState.NoProfile -> Text("No profile found.")
    // what is displayed if the user is not connected or if Firestore has failed
    is RoleState.Error -> Text("Could not load your profile. Try again.")
    // when the role is loaded it displays nothing because being redirected
    is RoleState.Loaded -> Unit
  }
}
