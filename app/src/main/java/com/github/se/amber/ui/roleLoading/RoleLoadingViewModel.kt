// this code was written with the aid of AI
package com.github.se.amber.ui.roleLoading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

class RoleLoadingViewModel : ViewModel() {
  private val _state = MutableStateFlow<RoleState>(RoleState.Loading)
  val state: StateFlow<RoleState> = _state

  fun loadRole() {
    viewModelScope.launch {
      val uid = FirebaseAuth.getInstance().currentUser?.uid
      // case where no one is connected
      if (uid == null) {
        _state.value = RoleState.Error
        return@launch
      }
      _state.value =
          try {
            val snapshot =
                FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
            val profile = snapshot.toObject(UserProfile::class.java)
            if (profile == null) RoleState.NoProfile else RoleState.Loaded(profile.role)
          } catch (e: Exception) {
            RoleState.Error
          }
    }
  }
}
