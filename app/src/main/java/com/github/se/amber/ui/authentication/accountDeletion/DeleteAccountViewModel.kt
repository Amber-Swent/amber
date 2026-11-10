// Written by MeiliV, with assistance from OpenAI Codex.

package com.github.se.amber.ui.authentication.accountDeletion

import android.content.Context
import androidx.annotation.StringRes
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

/**
 * UI state for the caregiver account deletion screen.
 *
 * Family warnings must load before deletion is enabled. A failed warning read leaves
 * [warningsLoaded] false so the screen offers a retry. Successful deletion normally restarts the
 * app; [isDeleting] remains true until that happens.
 *
 * @param isLoading Whether the profile and family warnings are being read. Initially true.
 * @param isDeleting Whether reauthentication, remote deletion, or local cleanup is in progress.
 *   While true, the screen shows progress and blocks Cancel and system Back.
 * @param warningsLoaded Whether all family reads succeeded and deletion may be confirmed.
 * @param warnings Last-member or last-caregiver consequences to show before confirmation.
 * @param errorMessageRes A string resource for the screen to display once, then clear with
 *   [DeleteAccountViewModel.clearError].
 * @param localCleanupNeeded Whether the remote account is already gone. The next confirmation calls
 *   [AccountDeletionRepository.clearLocalData] instead of deleting it again.
 */
data class DeleteAccountUIState(
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val warningsLoaded: Boolean = false,
    val warnings: List<Int> = emptyList(),
    @param:StringRes val errorMessageRes: Int? = null,
    val localCleanupNeeded: Boolean = false,
)

/**
 * Loads family warnings, confirms the Google account, and requests permanent deletion.
 *
 * All external reads and writes go through repositories. The deletion repository clears local data
 * and restarts the app on success; there is no success-navigation state.
 *
 * @param deletionRepository Deletes the account or retries local cleanup.
 * @param authRepository Reauthenticates the currently signed-in account.
 * @param profileRepository Reads the current caregiver profile.
 * @param circleRepository Reads each family to determine last-member and last-caregiver warnings.
 */
class DeleteAccountViewModel(
    private val deletionRepository: AccountDeletionRepository =
        AccountDeletionRepositoryProvider.default.repository,
    private val authRepository: AuthRepository = AuthRepositoryProvider.repository,
    private val profileRepository: ProfileRepository = ProfileRepositoryProvider.default.repository,
    private val circleRepository: CareCircleRepository =
        CareCircleRepositoryProvider.default.repository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(DeleteAccountUIState())
  val uiState: StateFlow<DeleteAccountUIState> = _uiState.asStateFlow()
  private var warningJob: Job? = null

  /** Clears an error after the screen shows it. */
  fun clearError() {
    _uiState.update { it.copy(errorMessageRes = null) }
  }

  /** Reads every family before enabling confirmation; a failed read can be retried. */
  fun loadWarnings() {
    if (
        _uiState.value.warningsLoaded || _uiState.value.isDeleting || warningJob?.isActive == true
    ) {
      return
    }
    _uiState.update { it.copy(isLoading = true, errorMessageRes = null) }
    warningJob = viewModelScope.launch {
      try {
        val profile =
            profileRepository.getCurrentProfile().getOrThrow()
                ?: error("The caregiver profile is unavailable")
        check(profile.role == Role.CAREGIVER) { "Only caregivers can use this screen" }
        val warnings = mutableSetOf<Int>()
        for (circleId in profile.circleIds) {
          val circle = circleRepository.getCircle(circleId).getOrThrow()
          if (circle.memberIds.size == 1) {
            warnings += R.string.deleteAccountLastMemberWarning
          } else if (circle.memberIds.filter { it != circle.patientId } == listOf(profile.uid)) {
            warnings += R.string.deleteAccountLastCaregiverWarning
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
              errorMessageRes = R.string.deleteAccountCheckFamiliesError,
          )
        }
      }
    }
  }

  /**
   * Requests a fresh Google credential and reauthenticates before deletion.
   *
   * A repeat attempt after [AccountDeletionError.LocalDataNotCleared] skips Google and calls
   * [AccountDeletionRepository.clearLocalData] because the remote account is already deleted.
   *
   * @param context Activity context used by Credential Manager and for the Google client ID.
   * @param credentialManager Supplies the Google credential for reauthentication.
   */
  fun deleteAccount(context: Context, credentialManager: CredentialManager) {
    if (_uiState.value.isDeleting || !_uiState.value.warningsLoaded) return
    val clientId = context.getString(R.string.default_web_client_id)
    _uiState.update { it.copy(isDeleting = true, errorMessageRes = null) }
    viewModelScope.launch {
      try {
        if (_uiState.value.localCleanupNeeded) {
          handleResult(deletionRepository.clearLocalData())
          return@launch
        }
        val option = GetSignInWithGoogleOption.Builder(clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = credentialManager.getCredential(context, request).credential
        authRepository
            .reauthenticateWithGoogle(credential)
            .fold(
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
              errorMessageRes =
                  if (it.localCleanupNeeded) R.string.deleteAccountCleanupError
                  else R.string.deleteAccountUnknownError,
          )
        }
      }
    }
  }

  /**
   * Maps a failed Google reauthentication to a translated UI error. If the account no longer
   * exists, immediately tries to clear local data and marks cleanup for later retries.
   *
   * @param error The failure returned by [AuthRepository.reauthenticateWithGoogle].
   */
  private suspend fun handleReauthenticationFailure(error: Throwable) {
    if (error == ReauthenticationError.AccountNotFound) {
      _uiState.update { it.copy(localCleanupNeeded = true) }
      handleResult(deletionRepository.clearLocalData())
      return
    }
    @StringRes
    val messageRes =
        when (error) {
          ReauthenticationError.WrongAccount -> R.string.deleteAccountWrongAccountError
          ReauthenticationError.Network -> R.string.deleteAccountNetworkError
          else -> R.string.deleteAccountReauthenticationError
        }
    _uiState.update { it.copy(isDeleting = false, errorMessageRes = messageRes) }
  }

  /**
   * Handles a deletion or local-cleanup result. A failure restores the action and shows a
   * translated error; incomplete local cleanup stays marked so the next attempt skips deletion. On
   * success, the repository restarts the app, so this function leaves the progress state active.
   *
   * @param result The result of [AccountDeletionRepository.deleteAccount] or
   *   [AccountDeletionRepository.clearLocalData].
   */
  private fun handleResult(result: Result<Unit>) {
    result.onFailure { error ->
      @StringRes
      val messageRes =
          when (error) {
            AccountDeletionError.Network -> R.string.deleteAccountNetworkError
            AccountDeletionError.ReauthenticationRequired ->
                R.string.deleteAccountReauthenticationRequiredError
            is AccountDeletionError.LocalDataNotCleared -> R.string.deleteAccountCleanupError
            else -> R.string.deleteAccountUnknownError
          }
      _uiState.update {
        it.copy(
            isDeleting = false,
            localCleanupNeeded =
                it.localCleanupNeeded || error is AccountDeletionError.LocalDataNotCleared,
            errorMessageRes = messageRes,
        )
      }
    }
  }
}
