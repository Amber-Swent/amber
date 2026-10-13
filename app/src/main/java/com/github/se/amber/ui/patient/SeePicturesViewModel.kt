// this code was written with the aid of AI

package com.github.se.amber.ui.patient

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SeePicturesUIState(
    // TODO remove this fake parameter
    private val oui : String = "oui"
    // TODO add more parameters
    //private val allPictures: List<Pictures> = emptyList(),
){

}

class SeePicturesViewModel() : ViewModel() {

    // See Pictures UI state
    private val _uiState = MutableStateFlow(SeePicturesUIState())
    val uiState: StateFlow<SeePicturesUIState> = _uiState.asStateFlow()
}