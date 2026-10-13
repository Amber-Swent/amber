// this code was written with the aid of AI

package com.github.se.amber.ui.patient

import androidx.lifecycle.ViewModel
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PatientHomeUIState(
    val patient: UserProfile =
        UserProfile(
            uid = "",
            role = Role.PATIENT,
            person = Person(),
            circleIds = emptyList(),
        ),
){

}

class PatientHomeViewModel() : ViewModel() {

    // Home UI state
    private val _uiState = MutableStateFlow(PatientHomeUIState())
    val uiState: StateFlow<PatientHomeUIState> = _uiState.asStateFlow()
}