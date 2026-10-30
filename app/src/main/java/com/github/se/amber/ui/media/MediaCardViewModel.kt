package com.github.se.amber.ui.media

import androidx.lifecycle.ViewModel
import com.github.se.amber.data.profile.FirebaseProfileRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.ui.patient.PatientHomeUIState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaCardUIState(
    val addedByNickname: String = "",
    val relatedNicknames: List<String> = emptyList(),
    val isLoading: Boolean = false,
)

class MediaCardViewModel(
    private val profileRepository: ProfileRepository = FirebaseProfileRepository(),
): ViewModel() {

    private val _uiState = MutableStateFlow(MediaCardUIState())
    val uiState: StateFlow<MediaCardUIState> = _uiState.asStateFlow()

    // TODO add docs
    fun load(media: MediaItem){
        // TODO to implement
    }


}