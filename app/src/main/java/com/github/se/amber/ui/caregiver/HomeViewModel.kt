package com.github.se.amber.ui.caregiver

import androidx.lifecycle.ViewModel
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile

data class HomeUIState(
    val patient: UserProfile =
        UserProfile(
            uid = "",
            role = Role.CAREGIVER,
            person = Person(),
            circleIds = emptyList(),
        )
)

// TODO: implement repository
/** ViewModel responsible for providing data to the caregiver home screen. */
class HomeViewModel(
    // private val repository: MediaRepository = MediaRepositoryProvider.repository,
) : ViewModel() {

  /** Returns the name of the patient associated with the caregiver home screen. */
  fun getPatientName(): String {
    // TODO : to be implement
    return ""
  }
}
