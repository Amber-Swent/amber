// Written by Yuhan YANG, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by MeiliV, with assistance from OpenAI Codex.
// Modified by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.caregiver.parameters.createInvitation

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.circle.ActiveCircleRepositoryProvider
import com.github.se.amber.data.invitation.InvitationCreationRepository
import com.github.se.amber.data.invitation.InvitationCreationRepositoryProvider
import com.github.se.amber.data.invitation.InvitationCreationResult
import com.github.se.amber.data.invitation.InvitationLookupRepository
import com.github.se.amber.data.invitation.InvitationLookupRepositoryProvider
import com.github.se.amber.data.invitation.InvitationSummary
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.user.UserProfile
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "CreateInvitationViewModel"

/**
 * UI state of the create invitation screen.
 *
 * @property generatedCode the code created last, or null before the first one.
 * @property invitations the invitations of the active circle.
 * @property errorMsgRes the error to show, as a string resource, or null for none.
 * @property selectedCircleId the active circle new invitations are created for.
 * @property isCreating whether an invitation is being created.
 * @property codeToCopy the invitation code to place on the clipboard, or null when none is pending.
 * @property copiedMessageRes the message to show after that code is copied, or null when none is
 *   pending.
 */
data class CreateInvitationUIState(
    val generatedCode: String? = null,
    val invitations: List<InvitationSummary> = emptyList(),
    @param:StringRes val errorMsgRes: Int? = null,
    val selectedCircleId: String? = null,
    val isCreating: Boolean = false,
    val codeToCopy: String? = null,
    @param:StringRes val copiedMessageRes: Int? = null,
)

class CreateInvitationViewModel(
    private val profileRepository: ProfileRepository = ProfileRepositoryProvider.default.repository,
    private val creationRepository: InvitationCreationRepository =
        InvitationCreationRepositoryProvider.default.repository,
    private val lookupRepository: InvitationLookupRepository =
        InvitationLookupRepositoryProvider.default.repository,
    private val activeCircleRepository: ActiveCircleRepository =
        ActiveCircleRepositoryProvider.default.repository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(CreateInvitationUIState())
  val uiState: StateFlow<CreateInvitationUIState> = _uiState.asStateFlow()
  private var invitationsJob: Job? = null

  init {
    invitationsJob = viewModelScope.launch {
      loadActiveCircle()
      val circleId = _uiState.value.selectedCircleId ?: return@launch
      fetchInvitations(circleId)
    }
  }

  /** Asks the screen to copy [code] and show the copied message. */
  fun copyCode(code: String) {
    _uiState.update {
      it.copy(codeToCopy = code, copiedMessageRes = R.string.createInvitationCodeCopied)
    }
  }

  /** Clears a copy request after the screen has copied the code and shown the message. */
  fun clearCopiedCode() {
    _uiState.update { it.copy(codeToCopy = null, copiedMessageRes = null) }
  }

  fun createInvitation() {
    if (_uiState.value.isCreating) return
    _uiState.update { it.copy(isCreating = true) }
    viewModelScope.launch {
      try {
        invitationsJob?.cancel()
        if (_uiState.value.selectedCircleId == null) {
          loadActiveCircle()
        }
        val circleId = _uiState.value.selectedCircleId ?: return@launch
        when (val result = creationRepository.createInvitation(circleId)) {
          is InvitationCreationResult.Created -> {
            _uiState.update { it.copy(generatedCode = result.code, errorMsgRes = null) }
            fetchInvitations(
                circleId,
                errorMsgRes = R.string.createInvitationErrorRefreshAfterCreate,
            )
          }
          else -> _uiState.update { it.copy(errorMsgRes = messageFor(result)) }
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        Log.e(TAG, "Creating the invitation failed", error)
        _uiState.update { it.copy(errorMsgRes = R.string.createInvitationErrorCreate) }
      } finally {
        _uiState.update { it.copy(isCreating = false) }
      }
    }
  }

  private suspend fun loadActiveCircle() {
    val profile = readProfile() ?: return
    val circleIds = profile.circleIds.filter { it.isNotBlank() }
    val selected = activeCircleRepository.activeCircleId(profile.uid, circleIds).first()
    if (selected == null) {
      _uiState.update { it.copy(errorMsgRes = R.string.createInvitationErrorNoCircle) }
      return
    }
    _uiState.update { it.copy(selectedCircleId = selected) }
  }

  private suspend fun fetchInvitations(
      circleId: String,
      @StringRes errorMsgRes: Int = R.string.createInvitationErrorLoadInvitations,
  ) {
    lookupRepository
        .getInvitations(circleId)
        .onSuccess { invitations ->
          _uiState.update { it.copy(invitations = invitations, errorMsgRes = null) }
        }
        .onFailure { error ->
          Log.e(TAG, "Failed to load invitations", error)
          _uiState.update { it.copy(errorMsgRes = errorMsgRes) }
        }
  }

  private suspend fun readProfile(): UserProfile? {
    val profile =
        profileRepository.getCurrentProfile().getOrElse { error ->
          Log.e(TAG, "Failed to load profile", error)
          _uiState.update { it.copy(errorMsgRes = R.string.createInvitationErrorLoadProfile) }
          return null
        }
    if (profile == null) {
      _uiState.update { it.copy(errorMsgRes = R.string.createInvitationErrorProfileNotFound) }
      return null
    }
    if (profile.circleIds.none { it.isNotBlank() }) {
      _uiState.update { it.copy(errorMsgRes = R.string.createInvitationErrorNoCircle) }
      return null
    }
    return profile
  }

  @StringRes
  private fun messageFor(result: InvitationCreationResult): Int =
      when (result) {
        is InvitationCreationResult.Created -> R.string.createInvitationCreated
        InvitationCreationResult.NotAuthenticated -> R.string.createInvitationErrorNotAuthenticated
        InvitationCreationResult.PermissionDenied -> R.string.createInvitationErrorPermissionDenied
        InvitationCreationResult.NetworkFailure -> R.string.createInvitationErrorNetwork
        InvitationCreationResult.InvalidExpiry -> R.string.createInvitationErrorInvalidExpiry
        InvitationCreationResult.CollisionRetryLimitExceeded ->
            R.string.createInvitationErrorCodesTaken
        is InvitationCreationResult.FirestoreFailure -> {
          Log.e(TAG, "Saving the invitation failed", result.source)
          R.string.createInvitationErrorSave
        }
      }
}
