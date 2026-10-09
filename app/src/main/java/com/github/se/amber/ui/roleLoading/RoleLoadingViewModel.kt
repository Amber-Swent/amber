// this code was written with the aid of AI
package com.github.se.amber.ui.roleLoading

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface RoleState {
  // waiting for finding the role
  data object Loading : RoleState

  // the role is loaded/ready
  data class Loaded(val role: Role) : RoleState

  // the user is connected but there are no user document with this uid
  data object NoProfile : RoleState

  // the user is not connected or if Firestore has failed
  data object Error : RoleState
}

class RoleLoadingViewModel(
    // TODO: move the Firebase access (FirebaseAuth / Firestore) into a repository and inject it
    // here instead of auth and firestore (planned for Sprint 2).
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : ViewModel() {
  private val _state = MutableStateFlow<RoleState>(RoleState.Loading)
  val state: StateFlow<RoleState> = _state

  private var loadJob: Job? = null

  fun loadRole() {
    // Ignore the call if a role loading operation is already in progress
    if (loadJob?.isActive == true) return
    loadJob = viewModelScope.launch {
      _state.value = RoleState.Loading
      val uid = auth.currentUser?.uid
      // case where no one is connected
      if (uid == null) {
        _state.value = RoleState.Error
        return@launch
      }
      _state.value =
          try {
            val snapshot = firestore.collection("users").document(uid).get().await()
            val profile = snapshot.toObject(UserProfile::class.java)
            when {
              profile == null -> RoleState.NoProfile
              !snapshot.contains("role") ->
                  RoleState.Error // missing field: to avoid a silent default role
              else -> RoleState.Loaded(profile.role)
            }
          } catch (e: CancellationException) {
            // Rethrow so the coroutine can be cancelled normally (not treated as a Firestore error)
            throw e
          } catch (e: Exception) {
            Log.e("RoleLoadingViewModel", "Failed to load role", e)
            RoleState.Error
          }
    }
  }

  fun signOut() {
    auth.signOut()
  }
}
