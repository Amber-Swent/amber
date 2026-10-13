// this code was written with the aid of AI

package com.github.se.amber.ui.patient

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PatientHomeUIState(
    // TODO remove this parameter and add the repositories (just need because cannot have empty ())
    private val needToChange: String = "this will change"
){

}

class PatientHomeViewModel() : ViewModel() {

    // Home UI state
    private val _uiState = MutableStateFlow(PatientHomeUIState())
    val uiState: StateFlow<PatientHomeUIState> = _uiState.asStateFlow()
}