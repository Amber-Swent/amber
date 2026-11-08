// Written by MeiliV, with assistance from OpenAI Codex
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.onboarding.createCareCircle

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.data.onboarding.OnboardingError
import com.github.se.amber.data.onboarding.OnboardingRepository
import com.github.se.amber.data.onboarding.OnboardingRepositoryProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State displayed by [CreateCareCircleScreen].
 *
 * @param name Care circle name currently entered by the user.
 * @param nameErrorRes Validation message shown below the name field, as a string resource, or null
 *   when the name is valid.
 * @param isLoading Whether circle creation is currently running.
 * @param isCreated Whether the first care circle was created successfully.
 */
data class CreateCareCircleUIState(
    val name: String = "",
    @param:StringRes val nameErrorRes: Int? = null,
    val isLoading: Boolean = false,
    val isCreated: Boolean = false,
)

/**
 * Owns the UI state and creation action of [CreateCareCircleScreen].
 *
 * The ViewModel validates the name and creates the first circle through [OnboardingRepository]. It
 * emits a success event after creation; navigation remains the responsibility of the caller
 * displaying the screen.
 *
 * @param repository Repository used to create the first care circle.
 */
class CreateCareCircleViewModel(
    private val repository: OnboardingRepository = OnboardingRepositoryProvider.default.repository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(CreateCareCircleUIState())
  val uiState = _uiState.asStateFlow()

  /*
  Channel.BUFFERED gives the screen a reliable one-shot success signal even if the composition is
  recreated before the navigation callback finishes. The channel stays open because a successful
  create should only happen once while the form is locked by `isCreated`.
  */
  private val successChannel = Channel<Unit>(Channel.BUFFERED)

  // receiveAsFlow() lets the screen observe the channel without being able to send into it
  val successEvents = successChannel.receiveAsFlow() // Tells the screen to call isCreated() once

  // Errors are string resources; the screen resolves them in the app language
  private val errorChannel = Channel<Int>(Channel.BUFFERED)
  val errorEvents = errorChannel.receiveAsFlow()

  /**
   * Updates the name displayed by the screen and its validation message.
   *
   * Changes are ignored during submission and after creation.
   *
   * @param name New care circle name entered by the user.
   */
  fun onNameChange(name: String) {
    if (_uiState.value.isLoading || _uiState.value.isCreated) return
    // Updates the supporting error while the user edits
    _uiState.update {
      it.copy(
          name = name,
          nameErrorRes = if (name.isBlank()) R.string.createCareCircleNameRequired else null,
      )
    }
  }

  /**
   * Validates the trimmed name, then asks the repository to create the first care circle.
   *
   * A blank name sets a field error without contacting the repository. While creation is running or
   * after it succeeds, further submissions are ignored. Failures leave the form ready for a retry.
   */
  fun createCareCircle() {
    val state = _uiState.value
    if (state.isLoading || state.isCreated) return

    val name = state.name.trim()

    // Checks name is not empty before calling the repository
    // Covers the case where the user presses Create without touching the text field before
    if (name.isEmpty()) {
      _uiState.update {
        it.copy(name = state.name, nameErrorRes = R.string.createCareCircleNameRequired)
      }
      return
    }

    _uiState.update { it.copy(isLoading = true, nameErrorRes = null) }
    viewModelScope.launch {
      try {
        repository
            .createInitialCareCircle(name)
            .fold(
                onSuccess = {
                  _uiState.update { it.copy(name = name, isLoading = false, isCreated = true) }
                  successChannel.send(Unit)
                },
                onFailure = { error ->
                  _uiState.update { it.copy(isLoading = false) } // Creation stopped
                  errorChannel.send(error.errorMessage())
                  // Failure allows retries, so channel not closed here
                },
            )
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        _uiState.update { it.copy(isLoading = false) }
        errorChannel.send(error.errorMessage())
      }
    }
  }
}

/** Maps a repository failure to the string resource of the message shown to the user. */
@StringRes
private fun Throwable.errorMessage(): Int =
    when (this) {
      is OnboardingError.InvalidCircleName -> R.string.createCareCircleErrorInvalidName
      is OnboardingError.Network -> R.string.createCareCircleErrorOffline
      is OnboardingError.NotAuthenticated -> R.string.createCareCircleErrorSessionExpired
      is OnboardingError.ProfileMissing,
      is OnboardingError.InvalidProfile -> R.string.createCareCircleErrorProfileMissing
      is OnboardingError.NotCaregiver -> R.string.createCareCircleErrorNotCaregiver
      is OnboardingError.CareCircleAlreadyExists -> R.string.createCareCircleErrorAlreadyExists
      is OnboardingError.PermissionDenied -> R.string.createCareCircleErrorPermissionDenied
      else -> R.string.createCareCircleErrorUnknown
    }
