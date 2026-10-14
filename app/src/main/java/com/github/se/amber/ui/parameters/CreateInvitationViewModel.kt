/* Written by Yuhan YANG, with assistance from
 * Cursor.
 */

package com.github.se.amber.ui.parameters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.invitation.InvitationCreationRepository
import com.github.se.amber.data.invitation.InvitationCreationRepositoryProvider
import com.github.se.amber.data.invitation.InvitationCreationResult
import com.github.se.amber.data.invitation.InvitationLookupRepository
import com.github.se.amber.data.invitation.InvitationLookupRepositoryProvider
import com.github.se.amber.data.invitation.InvitationSummary
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CareCircleChoice(
    val id: String,
    val name: String,
)

data class CreateInvitationUiState(
    val generatedCode: String? = null,
    val invitations: List<InvitationSummary> = emptyList(),
    val errorMessage: String? = null,
    val circles: List<CareCircleChoice> = emptyList(),
    val selectedCircleId: String? = null,
    val isCreating: Boolean = false,
)

class CreateInvitationViewModel(
    private val profileRepository: ProfileRepository = ProfileRepositoryProvider.default.repository,
    private val creationRepository: InvitationCreationRepository =
        InvitationCreationRepositoryProvider.default.repository,
    private val lookupRepository: InvitationLookupRepository =
        InvitationLookupRepositoryProvider.default.repository,
    private val circleRepository: CareCircleRepository =
        CareCircleRepositoryProvider.default.repository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(CreateInvitationUiState())
  val uiState: StateFlow<CreateInvitationUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      prepareCircles()
      val circleId = _uiState.value.selectedCircleId ?: return@launch
      fetchInvitations(circleId)
    }
  }

  fun selectCircle(circleId: String) {
    val state = _uiState.value
    if (state.isCreating) return
    if (state.circles.none { it.id == circleId }) return
    if (state.selectedCircleId == circleId) return
    _uiState.update {
      it.copy(
          selectedCircleId = circleId,
          generatedCode = null,
          invitations = emptyList(),
          errorMessage = null,
      )
    }
    viewModelScope.launch { fetchInvitations(circleId) }
  }

  fun createInvitation() {
    if (_uiState.value.isCreating) return
    _uiState.update { it.copy(isCreating = true) }
    viewModelScope.launch {
      try {
        if (_uiState.value.selectedCircleId == null) {
          prepareCircles()
        }
        val circleId = _uiState.value.selectedCircleId ?: return@launch
        when (val result = creationRepository.createInvitation(circleId)) {
          is InvitationCreationResult.Created -> {
            _uiState.update { it.copy(generatedCode = result.code, errorMessage = null) }
            fetchInvitations(
                circleId,
                failureMessage = "The invitation was created, but the list could not be refreshed",
            )
          }
          else -> _uiState.update { it.copy(errorMessage = messageFor(result)) }
        }
      } finally {
        _uiState.update { it.copy(isCreating = false) }
      }
    }
  }

  private suspend fun prepareCircles() {
    val circleIds = readCircleIds() ?: return
    val choices = circleIds.map { id ->
      val loadedName = circleRepository.getCircle(id).getOrNull()?.name
      CareCircleChoice(id = id, name = loadedName?.takeIf { it.isNotBlank() } ?: id)
    }
    val selected =
        _uiState.value.selectedCircleId?.takeIf { current -> choices.any { it.id == current } }
            ?: choices.first().id
    _uiState.update { it.copy(circles = choices, selectedCircleId = selected) }
  }

  private suspend fun fetchInvitations(
      circleId: String,
      failureMessage: String = "Could not load invitations",
  ) {
    lookupRepository
        .getInvitations(circleId)
        .onSuccess { invitations ->
          _uiState.update { it.copy(invitations = invitations, errorMessage = null) }
        }
        .onFailure { error ->
          _uiState.update {
            it.copy(
                errorMessage =
                    error.message?.takeIf { message -> message.isNotBlank() } ?: failureMessage,
            )
          }
        }
  }

  private suspend fun readCircleIds(): List<String>? {
    val profile =
        profileRepository.getCurrentProfile().getOrElse { error ->
          _uiState.update {
            it.copy(
                errorMessage =
                    error.message?.takeIf { message -> message.isNotBlank() }
                        ?: "Could not load your profile",
            )
          }
          return null
        }
    if (profile == null) {
      _uiState.update { it.copy(errorMessage = "Your profile was not found") }
      return null
    }
    if (profile.circleIds.isEmpty()) {
      _uiState.update { it.copy(errorMessage = "No care circle") }
      return null
    }
    return profile.circleIds
  }

  private fun messageFor(result: InvitationCreationResult): String =
      when (result) {
        is InvitationCreationResult.Created -> "Invitation created"
        InvitationCreationResult.NotAuthenticated -> "Sign in to create an invitation"
        InvitationCreationResult.PermissionDenied ->
            "You do not have permission to create an invitation"
        InvitationCreationResult.NetworkFailure ->
            "Network error. Check your connection and try again"
        InvitationCreationResult.InvalidExpiry -> "The invitation expiration date is invalid"
        InvitationCreationResult.CollisionRetryLimitExceeded ->
            "Every generated invitation code was already taken. Try again"
        is InvitationCreationResult.FirestoreFailure -> {
          val detail = result.source.message?.takeIf { it.isNotBlank() }
          if (detail == null) {
            "Saving the invitation failed"
          } else {
            "Saving the invitation failed: $detail"
          }
        }
      }
}
