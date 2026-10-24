// This file was written with the aid of AI.
package com.github.se.amber.ui.onboarding.createCareCircle

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

/**
 * State displayed by [CreateCareCircleScreen].
 *
 * @param name Care circle name currently entered by the user.
 * @param nameError Validation message shown below the name field, or null when the name is valid.
 * @param submitError Message for the latest creation failure, or null when there is no failure.
 * @param isLoading Whether circle creation is currently running.
 * @param isCreated Whether the first care circle was created successfully.
 */
data class CreateCareCircleUiState(
    val name: String = "",
    val nameError: String? = null,
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

  private val errorChannel = Channel<String>(Channel.BUFFERED)
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
          nameError = if (name.isBlank()) NAME_REQUIRED_ERROR else null,
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
      _uiState.update { it.copy(name = name, nameError = NAME_REQUIRED_ERROR) }
      return
    }

    _uiState.update { it.copy(isLoading = true, nameError = null) }
    viewModelScope.launch {
      try {
        repository
            .createInitialCareCircle(name)
            .fold(
                onSuccess = {
                  _uiState.update { it.copy(name = name, isLoading = false, isCreated = true) }
                  successChannel.send(Unit)
                  // Make explicit that successChannel will never send another request
                  successChannel.close()
                },
                onFailure = { error ->
                  _uiState.update { it.copy(isLoading = false) } // Creation stopped
                  errorChannel.send(error.toActionableMessage())
                  // Failure allows retries, so channel not closed here
                },
            )
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        _uiState.update { it.copy(isLoading = false) }
        errorChannel.send(error.toActionableMessage())
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
