// this code was written by Alaabrine
// with the aid of an AI coding agent
package com.github.se.amber.ui.accountCreation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.data.profile.ProfileCreationError
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val FILL_OUT_MESSAGE = "Please fill this out"

/**
 * UI state for the create-profile step.
 *
 * Field errors are derived from the value once validation has been attempted (blank + already
 * touched), matching the bootcamp B2 pattern. [submitError] carries mapped repository failures.
 *
 * @param name first name entered by the user.
 * @param surname last name entered by the user.
 * @param validationAttempted true after the user has pressed Patient or Caregiver at least once.
 * @param isSaving true while a create-profile request is in flight.
 * @param submitError actionable repository failure message, or null when there is none.
 */
data class CreateProfileUiState(
    val name: String = "",
    val surname: String = "",
    val validationAttempted: Boolean = false,
    val isSaving: Boolean = false,
    val submitError: String? = null,
) {
  /** Fill-out message when validation ran and [name] is blank; otherwise null. */
  val nameError: String?
    get() = if (validationAttempted && name.isBlank()) FILL_OUT_MESSAGE else null

  /** Fill-out message when validation ran and [surname] is blank; otherwise null. */
  val surnameError: String?
    get() = if (validationAttempted && surname.isBlank()) FILL_OUT_MESSAGE else null
}

/**
 * ViewModel for [CreateProfileScreen]: validates name/surname and persists the profile on role
 * choice.
 *
 * @param onSuccess invoked with the chosen [Role] and trimmed first name after a successful create,
 *   so the navigation owner can move to the next onboarding step without observing UI state.
 */
class CreateProfileViewModel(
    private val profileRepository: ProfileRepository = ProfileRepositoryProvider.default.repository,
    private val onSuccess: (role: Role, displayName: String) -> Unit = { _, _ -> },
) : ViewModel() {

  private val _uiState = MutableStateFlow(CreateProfileUiState())
  val uiState: StateFlow<CreateProfileUiState> = _uiState.asStateFlow()

  private var hasCreatedProfile = false

  /** Updates [CreateProfileUiState.name]; field error clears automatically when non-blank. */
  fun onNameChange(value: String) {
    updateNameField(name = value)
  }

  /** Updates [CreateProfileUiState.surname]; field error clears automatically when non-blank. */
  fun onSurnameChange(value: String) {
    updateNameField(surname = value)
  }

  /**
   * Validates name and surname, then creates the profile with [role] via [ProfileRepository].
   *
   * Does nothing while a save is already in progress or after a successful create. Does not call
   * the repository when fields are blank after trim.
   */
  fun onRoleSelected(role: Role) {
    if (_uiState.value.isSaving || hasCreatedProfile) return

    val trimmedName = _uiState.value.name.trim()
    val trimmedSurname = _uiState.value.surname.trim()

    _uiState.update {
      it.copy(
          name = trimmedName,
          surname = trimmedSurname,
          validationAttempted = true,
          submitError = null,
      )
    }

    if (trimmedName.isBlank() || trimmedSurname.isBlank()) return

    viewModelScope.launch {
      _uiState.update { it.copy(isSaving = true, submitError = null) }
      try {
        val result =
            profileRepository.createProfile(
                person = Person(firstName = trimmedName, lastName = trimmedSurname),
                role = role,
            )
        result
            .onSuccess {
              hasCreatedProfile = true
              _uiState.update { it.copy(isSaving = false, submitError = null) }
              onSuccess(role, trimmedName)
            }
            .onFailure { error ->
              _uiState.update {
                it.copy(
                    isSaving = false,
                    submitError = error.toActionableMessage(),
                )
              }
            }
      } catch (exception: CancellationException) {
        throw exception
      }
    }
  }

  private fun updateNameField(name: String? = null, surname: String? = null) {
    _uiState.update {
      it.copy(
          name = name ?: it.name,
          surname = surname ?: it.surname,
          submitError = null,
      )
    }
  }
}

private fun Throwable.toActionableMessage(): String =
    when (this) {
      is ProfileCreationError.InvalidPersonDetails ->
          "Enter your first and last name and try again."
      is ProfileCreationError.NotAuthenticated -> "Your session expired. Sign in and try again."
      is ProfileCreationError.ProfileAlreadyExists -> "A profile already exists for this account."
      is ProfileCreationError.PermissionDenied -> "You don't have permission to create a profile."
      is ProfileCreationError.Network -> "You're offline. Check your connection and try again."
      is ProfileCreationError.Unknown -> "Couldn't create your profile. Please try again."
      else -> "Couldn't create your profile. Please try again."
    }
