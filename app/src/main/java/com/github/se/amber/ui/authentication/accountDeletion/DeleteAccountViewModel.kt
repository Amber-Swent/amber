// Written by MeiliV, with assistance from OpenAI Codex.
package com.github.se.amber.ui.authentication.accountDeletion

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.data.account.AccountDeletionError
import com.github.se.amber.data.account.AccountDeletionRepository
import com.github.se.amber.data.account.AccountDeletionRepositoryProvider
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthRepositoryProvider
import com.github.se.amber.model.authentication.ReauthenticationError
import com.github.se.amber.model.user.Role
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val DELETE_ACCOUNT_TITLE = "Delete account"
internal const val DELETE_ACCOUNT_EXPLANATION =
    "Deletion is permanent. Your uploads and stories are removed for the whole family, and " +
        "pictures of you are removed from other people's stories. Photos of you uploaded by " +
        "others stay. You are also removed from appointments."
internal const val DELETE_ACCOUNT_PROGRESS = "Deleting your account. This may take a few minutes."
internal const val DELETE_ACCOUNT_CLEANUP_LABEL = "Finish deleting"
internal const val DELETE_ACCOUNT_CHECKING_LABEL = "Checking your families..."
internal const val DELETE_ACCOUNT_RETRY_LABEL = "Retry"
private const val CLEANUP_ERROR = "Your account was deleted, but this device could not be cleared. Tap Finish deleting to retry."
private const val UNKNOWN_ERROR = "Could not delete the account. Please try again."
private const val NETWORK_ERROR = "Check your connection and try again."

/** State of the caregiver deletion confirmation and its family warnings. */
data class DeleteAccountUIState(
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val warningsLoaded: Boolean = false,
    val warnings: List<String> = emptyList(),
    val errorMessage: String? = null,
    val localCleanupNeeded: Boolean = false,
)

/** Loads family warnings, confirms the Google account, and requests deletion. */
class DeleteAccountViewModel(
    private val deletionRepository: AccountDeletionRepository =
        AccountDeletionRepositoryProvider.default.repository,
    private val authRepository: AuthRepository = AuthRepositoryProvider.repository,
    private val profileRepository: ProfileRepository =
        ProfileRepositoryProvider.default.repository,
    private val circleRepository: CareCircleRepository =
        CareCircleRepositoryProvider.default.repository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(DeleteAccountUIState())
  val uiState: StateFlow<DeleteAccountUIState> = _uiState.asStateFlow()
  private var warningJob: Job? = null

  /** Clears an error after the screen shows it. */
  fun clearError() {
    _uiState.update { it.copy(errorMessage = null) }
  }

  /** Reads every family before enabling the confirmation button. */
  fun loadWarnings() {
    if (_uiState.value.warningsLoaded || _uiState.value.isDeleting || warningJob?.isActive == true) {
      return
    }
    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
    warningJob = viewModelScope.launch {
      try {
        val profile =
            profileRepository.getCurrentProfile().getOrThrow()
                ?: error("The caregiver profile is unavailable")
        check(profile.role == Role.CAREGIVER) { "Only caregivers can use this screen" }
        val warnings = mutableSetOf<String>()
        for (circleId in profile.circleIds) {
          val circle = circleRepository.getCircle(circleId).getOrThrow()
          if (circle.memberIds.size == 1) {
            warnings +=
                "You are this family's last member. The whole family and its media will be deleted."
          } else if (circle.memberIds.filter { it != circle.patientId } == listOf(profile.uid)) {
            warnings +=
                "You are this family's last caregiver. The family will continue with no one to manage it."
          }
        }
        _uiState.update {
          it.copy(isLoading = false, warningsLoaded = true, warnings = warnings.toList())
        }
      } catch (error: CancellationException) {
        throw error
      } catch (_: Exception) {
        _uiState.update {
          it.copy(
              isLoading = false,
              errorMessage = "Could not check your families. Please try again.",
          )
        }
      }
    }
  }

  /** Reauthenticates the caregiver before calling the deletion repository. */
  fun deleteAccount(context: Context, credentialManager: CredentialManager) {
    if (_uiState.value.isDeleting || !_uiState.value.warningsLoaded) return
    val clientId = context.getString(R.string.default_web_client_id)
    _uiState.update { it.copy(isDeleting = true, errorMessage = null) }
    viewModelScope.launch {
      try {
        if (_uiState.value.localCleanupNeeded) {
          handleResult(deletionRepository.clearLocalData())
          return@launch
        }
        val option = GetSignInWithGoogleOption.Builder(clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = credentialManager.getCredential(context, request).credential
        authRepository.reauthenticateWithGoogle(credential).fold(
            onSuccess = { handleResult(deletionRepository.deleteAccount()) },
            onFailure = { handleReauthenticationFailure(it) },
        )
      } catch (_: GetCredentialCancellationException) {
        _uiState.update { it.copy(isDeleting = false) }
      } catch (error: CancellationException) {
        throw error
      } catch (_: Exception) {
        _uiState.update {
          it.copy(
              isDeleting = false,
              errorMessage = if (it.localCleanupNeeded) CLEANUP_ERROR else UNKNOWN_ERROR,
          )
        }
      }
    }
  }

  private suspend fun handleReauthenticationFailure(error: Throwable) {
    if (error == ReauthenticationError.AccountNotFound) {
      _uiState.update { it.copy(localCleanupNeeded = true) }
      handleResult(deletionRepository.clearLocalData())
      return
    }
    val message =
        when (error) {
          ReauthenticationError.WrongAccount -> "Use the Google account you're signed in with."
          ReauthenticationError.Network -> NETWORK_ERROR
          else -> "Could not confirm your Google account. Please try again."
        }
    _uiState.update { it.copy(isDeleting = false, errorMessage = message) }
  }

  private fun handleResult(result: Result<Unit>) {
    result.onFailure { error ->
      val message =
          when (error) {
            AccountDeletionError.Network -> NETWORK_ERROR
            AccountDeletionError.ReauthenticationRequired ->
                "Confirm your Google account again and retry."
            is AccountDeletionError.LocalDataNotCleared -> CLEANUP_ERROR
            else -> UNKNOWN_ERROR
          }
      _uiState.update {
        it.copy(
            isDeleting = false,
            localCleanupNeeded =
                it.localCleanupNeeded || error is AccountDeletionError.LocalDataNotCleared,
            errorMessage = message,
        )
      }
    }
  }
}
