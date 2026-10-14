// This file was written with the aid of AI.
package com.github.se.amber.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

private const val NAME_REQUIRED_ERROR = "Circle name cannot be empty"

/** State of the first care circle form. */
data class CreateCareCircleUiState(
    val name: String = "",
    val nameError: String? = null,
    val submitError: String? = null,
    val isLoading: Boolean = false,
    val isCreated: Boolean = false,
)

/** Owns validation and creation; the screen's caller handles navigation. */
class CreateCareCircleViewModel(
    private val repository: OnboardingRepository = OnboardingRepositoryProvider.default.repository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(CreateCareCircleUiState())
  val uiState = _uiState.asStateFlow()

  /*
   Channel.BUFFERED holds the Event until the screen collects it
   Removing the channel and using uiState.isCreated could be a problem if the screen is
   recreated before navigation finished: the callback isCreated() would run again
  */
  private val successChannel = Channel<Unit>(Channel.BUFFERED)

  // receiveAsFlow() lets the screen observe the channel without being able to send into it
  val successEvents = successChannel.receiveAsFlow() // Tells the screen to call isCreated() once

  fun onNameChange(name: String) {
    if (_uiState.value.isLoading || _uiState.value.isCreated) return
    // Updates the supporting error while the user edits
    _uiState.update {
      it.copy(
          name = name,
          nameError = if (name.isBlank()) NAME_REQUIRED_ERROR else null,
          submitError = null,
      )
    }
  }

  /** Clears a submission failure after its toast has been shown. */
  fun clearSubmitError() {
    _uiState.update { it.copy(submitError = null) }
  }

  /** Validates locally before creating the circle. Repeated taps share one operation. */
  fun createCareCircle() {
    val state = _uiState.value
    if (state.isLoading || state.isCreated) return

    val name = state.name.trim()

    // Checks name is not empty before calling the repository
    // Covers the case where the user presses Create without touching the text field before
    if (name.isEmpty()) {
      _uiState.update { it.copy(nameError = NAME_REQUIRED_ERROR, submitError = null) }
      return
    }

    _uiState.update { it.copy(isLoading = true, nameError = null, submitError = null) }
    viewModelScope.launch {
      try {
        repository
            .createInitialCareCircle(name)
            .fold(
                onSuccess = {
                  _uiState.update { it.copy(isLoading = false, isCreated = true) }
                  successChannel.send(Unit)
                },
                onFailure = { error ->
                  _uiState.update {
                    it.copy(isLoading = false, submitError = error.toActionableMessage())
                  }
                },
            )
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        _uiState.update { it.copy(isLoading = false, submitError = error.toActionableMessage()) }
      }
    }
  }
}

// Private extension function on Throwable
private fun Throwable.toActionableMessage(): String =
    when (this) {
      is OnboardingError.InvalidCircleName -> "Enter a care circle name and try again."
      is OnboardingError.Network -> "You're offline. Check your connection and try again."
      is OnboardingError.NotAuthenticated -> "Your session expired. Sign in and try again."
      is OnboardingError.ProfileMissing,
      is OnboardingError.InvalidProfile ->
          "Your profile could not be found. Complete your profile and try again."
      is OnboardingError.NotCaregiver ->
          "Only caregivers can create a care circle. Check your profile and try again."
      is OnboardingError.CareCircleAlreadyExists ->
          "You already have a care circle. Return to your home screen."
      is OnboardingError.PermissionDenied ->
          "Creation was denied. Check your account and try again."
      else -> "Couldn't create your care circle. Please try again."
    }
