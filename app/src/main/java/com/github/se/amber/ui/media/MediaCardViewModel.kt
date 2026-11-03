package com.github.se.amber.ui.media

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaCardUIState(
    val addedByNickname: String = "",
    val relatedNicknames: List<String> = emptyList(),
    val isLoading: Boolean = false,
)

class MediaCardViewModel(): ViewModel() {
    private val _uiState = MutableStateFlow(MediaCardUIState())
    val uiState: StateFlow<MediaCardUIState> = _uiState.asStateFlow()
}